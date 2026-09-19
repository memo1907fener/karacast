<?php
/**
 * Nachrüsten, wenn schon eine ältere Fassung des Panels läuft.
 *
 * Legt nur an, was fehlt — bestehende Tabellen und alle Daten bleiben unberührt
 * (`CREATE TABLE IF NOT EXISTS`). Einmal aufrufen, dann vom Server löschen.
 *
 * Hinter der Panel-Anmeldung, obwohl das Anlegen fehlender Tabellen harmlos ist:
 * ein Skript, das ohne Passwort an der Datenbank arbeitet, hat auf einem Webserver
 * nichts verloren, auch kein harmloses.
 */
require __DIR__ . '/lib/db.php';
require __DIR__ . '/lib/util.php';
require __DIR__ . '/lib/auth.php';
require __DIR__ . '/lib/license.php';

session_start_safe();
if (!is_logged_in()) {
    header('Location: admin/login.php');
    exit;
}

$done = [];

// Seit Version 2: zurückgestellte Kennungen. Ohne diese Tabelle bricht die
// Schnittstelle ab, sobald sich ein freigeschaltetes Gerät mit einer unbekannten
// Kennung meldet.
db()->exec("CREATE TABLE IF NOT EXISTS pending_ids (
    id_hash    CHAR(12) NOT NULL PRIMARY KEY,
    device_id  INT      NOT NULL,
    first_seen DATETIME NOT NULL,
    last_seen  DATETIME NOT NULL,
    seen_count INT      NOT NULL DEFAULT 1,
    KEY (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
$done[] = 'Tabelle pending_ids geprüft';

    // Zahlungen. Der eindeutige Schlüssel auf session_id ist die Sperre gegen
    // doppelte Freischaltung: Webhook und Dankeseite kommen oft beide an.
    db()->exec("CREATE TABLE IF NOT EXISTS payments (
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
    db()->exec("CREATE TABLE IF NOT EXISTS consents (
        id          INT AUTO_INCREMENT PRIMARY KEY,
        session_id  VARCHAR(255) NOT NULL,
        device_id   INT          NOT NULL,
        device_code VARCHAR(16)  NOT NULL,
        ip          VARCHAR(45)  DEFAULT '',
        at          DATETIME     NOT NULL,
        UNIQUE KEY (session_id), KEY (device_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // Jede Stripe-Meldung genau einmal. Stripe wiederholt sie, bis wir antworten.
    db()->exec("CREATE TABLE IF NOT EXISTS webhook_events (
        event_id VARCHAR(255) NOT NULL PRIMARY KEY,
        type     VARCHAR(60)  NOT NULL DEFAULT '',
        at       DATETIME     NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

$done[] = 'Tabellen für Zahlungen geprüft';

// Seit der Stripe-Anbindung: der Vermerk trägt eine Stripe-Sitzungskennung, und die
// ist 66 Zeichen lang. Mit den alten 64 brach das Freischalten genau dann ab, wenn
// gerade jemand bezahlt hatte.
try {
    $width = db()->query(
        "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'devices'
           AND COLUMN_NAME = 'order_ref'"
    )->fetchColumn();
    if ($width !== false && (int) $width < 191) {
        db()->exec("ALTER TABLE devices MODIFY order_ref VARCHAR(191) DEFAULT ''");
        $done[] = 'Spalte order_ref auf 191 Zeichen verbreitert';
    } else {
        $done[] = 'Spalte order_ref ist breit genug';
    }
} catch (Throwable $e) {
    error_log('[karacast upgrade] ' . $e->getMessage());
    $done[] = 'Spalte order_ref konnte nicht geprüft werden — steht im Fehlerprotokoll';
}

// Der eindeutige Schlüssel auf dem Gerätecode. Darauf beruht, dass zwei
// gleichzeitige Anfragen desselben Fernsehers nur eine Zeile ergeben.
try {
    $has = db()->query(
        "SELECT COUNT(*) FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'devices'
           AND INDEX_NAME = 'code' AND NON_UNIQUE = 0"
    )->fetchColumn();
    if (!$has) {
        db()->exec('ALTER TABLE devices DROP INDEX code');
        db()->exec('ALTER TABLE devices ADD UNIQUE KEY (code)');
        $done[] = 'Gerätecode auf eindeutig umgestellt';
    } else {
        $done[] = 'Gerätecode ist bereits eindeutig';
    }
} catch (Throwable $e) {
    // Doppelte Codes in einer alten Datenbank: dann bleibt es beim einfachen
    // Schlüssel, und der Rest funktioniert trotzdem.
    error_log('[karacast upgrade] ' . $e->getMessage());
    $done[] = 'Gerätecode konnte nicht umgestellt werden — steht im Fehlerprotokoll';
}
?>
<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Karacast Panel — Nachrüsten</title><?php require __DIR__ . '/admin/style.php'; ?></head><body>
<div class="wrap narrow">
    <h1>Nachgerüstet</h1>
    <div class="card">
        <ul><?php foreach ($done as $line): ?><li><?= e($line) ?></li><?php endforeach; ?></ul>
    </div>
    <p class="dim">Jetzt <code>upgrade.php</code> vom Server löschen.</p>
    <p><a class="btn" href="admin/">Zum Panel</a></p>
</div></body></html>
