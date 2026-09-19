<?php
/**
 * Einmal aufrufen, danach löschen.
 *
 * Legt die Tabellen an, erzeugt das Schlüsselpaar und fragt nach einem Passwort
 * fürs Panel. Das Passwort tippst du selbst ein — es wird sofort gehasht und nie
 * im Klartext gespeichert.
 */
require __DIR__ . '/lib/db.php';
require __DIR__ . '/lib/util.php';
require __DIR__ . '/lib/license.php';
require __DIR__ . '/lib/auth.php';

session_start_safe();

$lock = __DIR__ . '/keys/installed.lock';

/**
 * Ist hier schon alles eingerichtet?
 *
 * Gefragt wird nach dem Ergebnis, nicht nach der Sperrdatei allein. Zwei Gründe:
 * Die Sperrdatei kann fehlen, obwohl längst eingerichtet ist — dann dürfte sich
 * sonst jeder, der install.php noch auf dem Server findet, ein neues Panel-Passwort
 * setzen. Und sie kann dastehen, obwohl die Einrichtung mittendrin abgebrochen ist —
 * dann wäre das Panel für immer verriegelt und ohne Schlüssel unbrauchbar.
 * Fertig ist es genau dann, wenn beides da ist: Schlüssel und Passwort.
 */
function already_installed(): bool {
    try {
        return has_key() && admin_password_hash() !== null;
    } catch (Throwable $e) {
        // Noch keine Tabellen: dann ist hier auch noch nichts eingerichtet.
        return false;
    }
}

if (already_installed()) {
    exit('Bereits installiert. Lösche install.php vom Server.');
}

$error = null;
$done  = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $pass   = $_POST['password'] ?? '';
    $repeat = $_POST['repeat'] ?? '';

    if (mb_strlen($pass) < 10) {
        $error = 'Mindestens zehn Zeichen, bitte. Dieses Panel stellt Lizenzen aus.';
    } elseif ($pass !== $repeat) {
        $error = 'Die beiden Eingaben sind nicht gleich.';
    } elseif (!is_writable(dirname($lock)) || !is_writable(dirname(private_key_path()))) {
        // Zuerst gefragt, bevor irgendetwas geschrieben wird: auf einem per FTP
        // hochgeladenen Webspace gehört keys/ oft einem anderen Benutzer als PHP.
        // Bräche die Einrichtung erst nach dem Passwort ab, stünde ein halb
        // eingerichtetes Panel da, das sich nicht mehr aufrufen lässt.
        $error = 'Der Ordner keys/ ist für PHP nicht beschreibbar. Setze die Rechte '
               . 'auf 755 (notfalls 777) und rufe diese Seite noch einmal auf.';
    } else {
        try {
            create_tables();
            // Erst der Schlüssel, dann die Sperre, zuletzt das Passwort: geht
            // unterwegs etwas schief, ist noch nichts eingerichtet und die Seite
            // lässt sich ein zweites Mal aufrufen.
            $publicKey = has_key() ? public_key_base64() : create_keypair();
            if (file_put_contents($lock, date('c')) === false) {
                throw new RuntimeException('Sperrdatei nicht schreibbar: ' . $lock);
            }
            set_admin_password($pass);
            $done = $publicKey;
        } catch (Throwable $e) {
            // Der genaue Text geht ins Fehlerprotokoll, nicht auf die Seite: er
            // nennt Pfade und Datenbanknamen.
            error_log('[karacast install] ' . $e->getMessage());
            $error = 'Fehlgeschlagen. Der Grund steht im Fehlerprotokoll des Servers '
                   . '(error_log), damit er nicht hier im Browser landet.';
        }
    }
}

function create_tables(): void {
    $pdo = db();

    $pdo->exec("CREATE TABLE IF NOT EXISTS devices (
        id            INT AUTO_INCREMENT PRIMARY KEY,
        code          VARCHAR(16)  NOT NULL,
        first_seen    DATETIME     NOT NULL,
        last_seen     DATETIME     NOT NULL,
        model         VARCHAR(64)  DEFAULT '',
        version       VARCHAR(16)  DEFAULT '',
        note          VARCHAR(120) DEFAULT '',
        status        ENUM('trial','active','blocked') NOT NULL DEFAULT 'trial',
        -- Breit genug für 'stripe:' plus eine Stripe-Sitzungskennung. Die ist
        -- 66 Zeichen lang, und mit 64 hier brach die Freischaltung genau dann ab,
        -- wenn jemand gerade bezahlt hatte.
        order_ref     VARCHAR(191) DEFAULT '',
        ticket        TEXT         NULL,
        activated_at  DATETIME     NULL,
        transfers     INT          NOT NULL DEFAULT 0,
        -- Eindeutig, nicht nur indiziert: darauf beruht, dass zwei gleichzeitige
        -- Anfragen desselben Fernsehers per INSERT IGNORE nur eine Zeile ergeben.
        UNIQUE KEY (code), KEY (status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // Der Schlüssel liegt auf id_hash, nicht auf device_id: gesucht wird immer
    // über eine einzelne Kennung, und jede darf nur zu einem Gerät gehören.
    $pdo->exec("CREATE TABLE IF NOT EXISTS device_ids (
        id_hash   CHAR(12) NOT NULL PRIMARY KEY,
        device_id INT      NOT NULL,
        added_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        KEY (device_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // Kennungen, die zu einem bezahlten Gerät gehören könnten, aber noch nicht
    // bestätigt sind. Siehe may_extend() in lib/license.php — ohne diese Tabelle
    // wäre der öffentliche Gerätecode ein Schlüssel zu fremden Lizenzen.
    $pdo->exec("CREATE TABLE IF NOT EXISTS pending_ids (
        id_hash    CHAR(12) NOT NULL PRIMARY KEY,
        device_id  INT      NOT NULL,
        first_seen DATETIME NOT NULL,
        last_seen  DATETIME NOT NULL,
        seen_count INT      NOT NULL DEFAULT 1,
        KEY (device_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $pdo->exec("CREATE TABLE IF NOT EXISTS redeem_codes (
        code       VARCHAR(16) NOT NULL PRIMARY KEY,
        created_at DATETIME    NOT NULL,
        note       VARCHAR(120) DEFAULT '',
        used_at    DATETIME    NULL,
        device_id  INT         NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // Zahlungen. Der eindeutige Schlüssel auf session_id ist die Sperre gegen
    // doppelte Freischaltung: Webhook und Dankeseite kommen oft beide an.
    $pdo->exec("CREATE TABLE IF NOT EXISTS payments (
        id             INT AUTO_INCREMENT PRIMARY KEY,
        provider       VARCHAR(16)  NOT NULL DEFAULT 'stripe',
        session_id     VARCHAR(255) NOT NULL,
        payment_intent VARCHAR(255) DEFAULT '',
        device_id      INT          NOT NULL,
        device_code    VARCHAR(16)  NOT NULL,
        amount_cents   INT          NOT NULL DEFAULT 0,
        currency       VARCHAR(8)   NOT NULL DEFAULT 'eur',
        email          VARCHAR(120) DEFAULT '',
        consent_ip     VARCHAR(45)  DEFAULT '',
        note           VARCHAR(120) DEFAULT '',
        created_at     DATETIME     NOT NULL,
        refunded_at    DATETIME     NULL,
        UNIQUE KEY (session_id), KEY (device_id), KEY (payment_intent)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // Die Zustimmung zum sofortigen Leistungsbeginn — mit Zeitpunkt und Adresse.
    // Ohne diesen Nachweis steht im Streitfall Aussage gegen Aussage.
    $pdo->exec("CREATE TABLE IF NOT EXISTS consents (
        id          INT AUTO_INCREMENT PRIMARY KEY,
        session_id  VARCHAR(255) NOT NULL,
        device_id   INT          NOT NULL,
        device_code VARCHAR(16)  NOT NULL,
        ip          VARCHAR(45)  DEFAULT '',
        at          DATETIME     NOT NULL,
        UNIQUE KEY (session_id), KEY (device_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // Jede Stripe-Meldung genau einmal. Stripe wiederholt sie, bis wir antworten.
    $pdo->exec("CREATE TABLE IF NOT EXISTS webhook_events (
        event_id VARCHAR(255) NOT NULL PRIMARY KEY,
        type     VARCHAR(60)  NOT NULL DEFAULT '',
        at       DATETIME     NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $pdo->exec("CREATE TABLE IF NOT EXISTS settings (
        name  VARCHAR(40) NOT NULL PRIMARY KEY,
        value TEXT        NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $pdo->exec("CREATE TABLE IF NOT EXISTS login_attempts (
        id INT AUTO_INCREMENT PRIMARY KEY,
        ip VARCHAR(45) NOT NULL,
        at DATETIME    NOT NULL,
        KEY (ip, at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}
?>
<!doctype html>
<html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Karacast Panel — Installation</title>
<?php require __DIR__ . '/admin/style.php'; ?>
</head><body>
<div class="wrap narrow">
<h1>Karacast Panel einrichten</h1>

<?php if ($done !== null): ?>
    <div class="ok">
        <h2>Fertig.</h2>
        <p>Tabellen angelegt, Schlüsselpaar erzeugt, Passwort gesetzt.</p>
    </div>

    <h2>Der öffentliche Schlüssel</h2>
    <p>Diese Zeichenkette kommt in die App, in
       <code>util/LicenseVerifier.kt</code> → <code>PUBLIC_KEY_BASE64</code>.
       Sie ist nicht geheim — ein öffentlicher Schlüssel kann Unterschriften prüfen,
       aber keine erzeugen.</p>
    <textarea readonly rows="4" onclick="this.select()"><?= e($done) ?></textarea>

    <h2>Noch zwei Handgriffe</h2>
    <ol>
        <li><strong>install.php vom Server löschen.</strong> Sie hat ihre Arbeit getan.</li>
        <li>Prüfen, dass <code>keys/private.pem</code> nicht über den Browser
            erreichbar ist: <a href="keys/private.pem" target="_blank">hier klicken</a> —
            es muss ein Fehler kommen, kein Text.</li>
    </ol>
    <p><a class="btn" href="admin/">Zum Panel</a></p>

<?php else: ?>
    <p>Lege ein Passwort für die Verwaltung fest. Es wird sofort gehasht;
       im Klartext steht es nirgends.</p>
    <?php if ($error): ?><p class="err"><?= e($error) ?></p><?php endif; ?>
    <form method="post">
        <label>Passwort
            <input type="password" name="password" autocomplete="new-password" required>
        </label>
        <label>Wiederholen
            <input type="password" name="repeat" autocomplete="new-password" required>
        </label>
        <button class="btn" type="submit">Einrichten</button>
    </form>
<?php endif; ?>
</div>
</body></html>
