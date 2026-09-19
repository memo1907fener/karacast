<?php
/**
 * Die Selbstauskunft des Panels.
 *
 * Aufrufen, wenn irgendetwas „Fehler 500" sagt oder sich seltsam verhält. Sie prüft
 * drei Dinge, die zusammen fast jede Ursache abdecken:
 *
 *  1. **Ist die PHP-Umgebung geeignet?** Version und die vier Erweiterungen, ohne
 *     die Teile des Panels nicht laufen können.
 *  2. **Sind die Dateien heil angekommen?** Jede Datei wird gegen die Prüfsumme aus
 *     manifest.json gehalten. Ein abgebrochener FTP-Upload hinterlässt eine halbe
 *     Datei, und eine halbe PHP-Datei ist genau ein weißer Bildschirm ohne Erklärung.
 *  3. **Stehen die Tabellen?** Fehlt eine, sagt sie welche.
 *
 * Danach darf sie stehen bleiben — sie ist hinter der Anmeldung — oder gelöscht
 * werden. Sie ändert nichts.
 */
require __DIR__ . '/lib/db.php';
require __DIR__ . '/lib/util.php';
require __DIR__ . '/lib/auth.php';
require_once __DIR__ . '/lib/license.php';

session_start_safe();
if (!is_logged_in()) {
    header('Location: admin/login.php');
    exit;
}

// ------------------------------------------------------------------ 1. PHP
$php = PHP_VERSION;
$phpOk = version_compare($php, '8.1.0', '>=');
$extensions = [
    'curl'      => 'Stripe — ohne sie keine Zahlungen',
    'openssl'   => 'Lizenz-Unterschriften — ohne sie gar nichts',
    'pdo_mysql' => 'Datenbank',
    'mbstring'  => 'Texte mit Umlauten',
];

// ------------------------------------------------------- 2. Dateien unversehrt
$files = [];
$manifestPath = __DIR__ . '/manifest.json';
$manifest = is_file($manifestPath) ? json_decode((string) file_get_contents($manifestPath), true) : null;

if (is_array($manifest)) {
    foreach ($manifest as $name => $hash) {
        $path = __DIR__ . '/' . $name;
        if (!is_file($path)) {
            $files[$name] = ['state' => 'fehlt', 'note' => 'nicht hochgeladen'];
            continue;
        }
        $own = hash_file('sha256', $path);
        if ($own === $hash) {
            $files[$name] = ['state' => 'ok', 'note' => number_format(filesize($path)) . ' Bytes'];
        } else {
            $files[$name] = [
                'state' => 'anders',
                'note'  => number_format(filesize($path)) . ' Bytes — verändert oder unvollständig',
            ];
        }
    }
}

// --------------------------------------------------- 3. Der private Schlüssel
//
// Das Wertvollste der ganzen Anlage. Ohne ihn lässt sich keine Lizenz mehr
// ausstellen — weder über Stripe noch von Hand. Und weil er absichtlich nicht in
// der ZIP liegt, ist er die eine Datei, die ein synchronisierendes FTP-Programm
// gern aufräumt.
$keyPath = private_key_path();
$key = [
    'path'     => $keyPath,
    'exists'   => is_file($keyPath),
    'readable' => is_file($keyPath) && is_readable($keyPath),
    'size'     => is_file($keyPath) ? (int) filesize($keyPath) : 0,
    'perms'    => is_file($keyPath) ? substr(sprintf('%o', fileperms($keyPath)), -4) : '',
    'owner'    => '',
    'phpUser'  => '',
    'public'   => '',
    'roundtrip'=> null,
    'error'    => '',
];

if (function_exists('posix_getpwuid')) {
    if ($key['exists']) {
        $info = @posix_getpwuid(fileowner($keyPath));
        $key['owner'] = $info['name'] ?? (string) fileowner($keyPath);
    }
    $me = function_exists('posix_geteuid') ? @posix_getpwuid(posix_geteuid()) : null;
    $key['phpUser'] = $me['name'] ?? '';
}

// Was liegt überhaupt im Ordner? Ein „private.pem.txt" vom Dateimanager sieht man
// sonst nie — die Datei ist da, heißt aber anders, und nichts findet sie.
$keyDir = [];
foreach (@scandir(dirname($keyPath)) ?: [] as $entry) {
    if ($entry === '.' || $entry === '..') continue;
    $keyDir[$entry] = (int) @filesize(dirname($keyPath) . '/' . $entry);
}

if ($key['readable']) {
    // Die härteste Probe: einmal wirklich unterschreiben und die Unterschrift
    // gegen den eigenen öffentlichen Schlüssel prüfen. Was hier durchgeht,
    // funktioniert auch beim Freischalten.
    try {
        $key['public'] = public_key_base64();
        $ticket = issue_ticket(['aabbccddeeff'], 'pruefung');
        $parts  = explode('.', $ticket);
        $signed = $parts[0] . '.' . $parts[1];
        $sig    = base64_decode(strtr($parts[2], '-_', '+/'));
        $pem    = "-----BEGIN PUBLIC KEY-----\n"
                . chunk_split($key['public'], 64, "\n") . "-----END PUBLIC KEY-----\n";
        $key['roundtrip'] = openssl_verify($signed, $sig, $pem, OPENSSL_ALGO_SHA256) === 1;
    } catch (Throwable $e) {
        $key['roundtrip'] = false;
        $key['error'] = $e->getMessage();
    }
}

// ------------------------------------------------------------- 4. Tabellen
$wanted = ['devices', 'device_ids', 'pending_ids', 'redeem_codes', 'payments',
           'consents', 'webhook_events', 'settings', 'login_attempts'];
$tables = [];
try {
    $have = $conn = db()->query('SHOW TABLES')->fetchAll(PDO::FETCH_COLUMN);
    foreach ($wanted as $name) $tables[$name] = in_array($name, $have, true);
} catch (Throwable $e) {
    $tables = null;
    $dbError = $e->getMessage();
}

$problems = 0;
if ($key['roundtrip'] !== true) $problems++;
if (!$phpOk) $problems++;
foreach ($extensions as $name => $_) if (!extension_loaded($name)) $problems++;
foreach ($files as $f) if ($f['state'] !== 'ok') $problems++;
if ($tables === null) $problems++;
else foreach ($tables as $ok) if (!$ok) $problems++;
?>
<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Panel prüfen</title><?php require __DIR__ . '/admin/style.php'; ?></head><body>
<div class="wrap narrow">
    <div class="bar"><a href="admin/">← Panel</a><div class="spacer"></div></div>
    <h1>Panel prüfen</h1>

    <?php if ($problems === 0): ?>
        <p class="ok">Alles in Ordnung — PHP, Dateien und Tabellen.</p>
    <?php else: ?>
        <p class="err"><?= (int) $problems ?> Punkt(e) stimmen nicht. Sie stehen unten in Rot.</p>
    <?php endif; ?>

    <div class="card">
        <h2 style="margin-top:0">Der private Schlüssel</h2>
        <p class="dim">Ohne ihn lässt sich keine Lizenz ausstellen — weder über Stripe
           noch von Hand. Er liegt bewusst nicht in der ZIP.</p>

        <p><span class="dim">Erwartet unter</span><br><code><?= e($key['path']) ?></code></p>

        <?php if ($key['roundtrip'] === true): ?>
            <p><span class="tag active">in Ordnung</span>
               <span class="dim">— Unterschreiben und Prüfen wurden gerade erfolgreich durchgespielt.</span></p>
            <p class="dim">Öffentlicher Schlüssel (muss mit dem in der App übereinstimmen):</p>
            <textarea readonly rows="3" onclick="this.select()"><?= e($key['public']) ?></textarea>

        <?php elseif (!$key['exists']): ?>
            <p><span class="tag blocked">Datei fehlt</span></p>
            <p>Die Datei ist nicht da. Das passiert, wenn ein FTP-Programm den Ordner
               <code>keys/</code> mit der ZIP abgleicht und dabei aufräumt, was in der ZIP
               nicht vorkommt.</p>
            <p><strong>Wenn du eine Sicherung hast:</strong> <code>private.pem</code> zurück
               nach <code>keys/</code> legen — und nur diese Datei, nichts sonst anfassen.</p>
            <p><strong>Wenn nicht:</strong> melde dich, bevor du irgendetwas neu erzeugst.
               Ein neues Schlüsselpaar macht alle bisher ausgestellten Lizenzen ungültig und
               verlangt eine neue App-Version. Das ist zu klären, nicht zu klicken.</p>

        <?php elseif (!$key['readable']): ?>
            <p><span class="tag blocked">vorhanden, aber nicht lesbar</span></p>
            <p>Die Datei ist da (<?= number_format($key['size']) ?> Bytes, Rechte
               <code><?= e($key['perms']) ?></code><?= $key['owner'] !== '' ? ', gehört ' . e($key['owner']) : '' ?>),
               aber PHP<?= $key['phpUser'] !== '' ? ' (läuft als ' . e($key['phpUser']) . ')' : '' ?>
               darf sie nicht öffnen.</p>
            <p><strong>Zu tun:</strong> im Dateimanager deines Hosters die Rechte auf
               <code>600</code> setzen <em>und</em> sicherstellen, dass die Datei demselben
               Benutzer gehört wie die übrigen Dateien. Notfalls: Inhalt kopieren, Datei
               löschen, neu anlegen und einfügen — dann stimmt der Besitzer automatisch.</p>

        <?php else: ?>
            <p><span class="tag blocked">lesbar, aber unbrauchbar</span></p>
            <p>Die Datei lässt sich öffnen, taugt aber nicht als Schlüssel
               (<?= number_format($key['size']) ?> Bytes).
               <?= $key['error'] !== '' ? '<br>Meldung: <code>' . e($key['error']) . '</code>' : '' ?></p>
            <p>Meist ist sie beim Hochladen im Textmodus verstümmelt worden. Die Datei muss
               mit <code>-----BEGIN</code> beginnen und als <em>binär</em> übertragen werden.</p>
        <?php endif; ?>

        <p class="dim" style="margin-top:16px">Im Ordner <code>keys/</code> liegt gerade:</p>
        <p class="mono" style="font-size:12px">
            <?php if (!$keyDir): ?>— nichts —<?php else: ?>
                <?php foreach ($keyDir as $name => $size): ?>
                    <?= e($name) ?> (<?= number_format($size) ?> B)<br>
                <?php endforeach; ?>
            <?php endif; ?>
        </p>
    </div>

    <div class="card">
        <h2 style="margin-top:0">PHP</h2>
        <p>Version <strong><?= e($php) ?></strong>
            <?= $phpOk ? '<span class="tag active">genügt</span>'
                       : '<span class="tag blocked">zu alt — 8.1 oder neuer nötig</span>' ?></p>
        <?php foreach ($extensions as $name => $why): ?>
            <p><code><?= e($name) ?></code>
                <?= extension_loaded($name) ? '<span class="tag active">da</span>'
                                            : '<span class="tag blocked">fehlt</span>' ?>
                <span class="dim">— <?= e($why) ?></span></p>
        <?php endforeach; ?>
    </div>

    <div class="card">
        <h2 style="margin-top:0">Dateien</h2>
        <?php if (!is_array($manifest)): ?>
            <p class="dim">Keine <code>manifest.json</code> vorhanden — dann kann ich die Dateien
               nicht vergleichen. Sie liegt in der ZIP; lade sie mit hoch.</p>
        <?php else: ?>
            <p class="dim">Verglichen wird gegen die Prüfsummen aus der ZIP. „Anders" heißt:
               beim Hochladen abgebrochen, im Texteditor verändert, oder eine ältere Fassung.</p>
            <table>
                <tr><th>Datei</th><th>Zustand</th><th></th></tr>
                <?php foreach ($files as $name => $f): ?>
                <tr>
                    <td class="mono" style="font-size:12px"><?= e($name) ?></td>
                    <td><?= $f['state'] === 'ok'
                            ? '<span class="tag active">heil</span>'
                            : '<span class="tag blocked">' . e($f['state']) . '</span>' ?></td>
                    <td class="dim"><?= e($f['note']) ?></td>
                </tr>
                <?php endforeach; ?>
            </table>
        <?php endif; ?>
    </div>

    <div class="card">
        <h2 style="margin-top:0">Tabellen</h2>
        <?php if ($tables === null): ?>
            <p class="err">Datenbank nicht lesbar: <?= e($dbError ?? '') ?></p>
        <?php else: ?>
            <?php foreach ($tables as $name => $ok): ?>
                <p><code><?= e($name) ?></code>
                    <?= $ok ? '<span class="tag active">da</span>'
                            : '<span class="tag blocked">fehlt — upgrade.php aufrufen</span>' ?></p>
            <?php endforeach; ?>
        <?php endif; ?>
    </div>
</div></body></html>
