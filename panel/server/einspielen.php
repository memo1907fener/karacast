<?php
/**
 * Updates einspielen — ohne Dateimanager, ohne FTP-Abgleich.
 *
 * Der Grund für diese Seite ist eine Erfahrung: Wer eine ZIP im Dateimanager
 * entpackt, bekommt bei jedem Namenskonflikt einen zweiten Ordner — `admin.198`,
 * `api.4497` — statt einer aktualisierten Datei. Nach fünf Updates liegen fünfzehn
 * halbtote Kopien im Webverzeichnis, jede davon über das Internet erreichbar. Und
 * wer stattdessen per FTP abgleicht, riskiert, dass der private Schlüssel als
 * „überflüssig" gelöscht wird, weil er in der ZIP nicht vorkommt.
 *
 * Also: ZIP hochladen, hier auswählen, Knopf drücken. Diese Seite schreibt jede
 * Datei an ihren Platz und rührt zwei Dinge unter keinen Umständen an —
 * **`config.php`** und den Ordner **`keys/`**.
 */
require __DIR__ . '/lib/db.php';
require __DIR__ . '/lib/util.php';
require __DIR__ . '/lib/auth.php';

// Nicht require_admin(): das leitet auf `login.php` neben sich weiter, und diese
// Seite liegt im Hauptverzeichnis, nicht in admin/.
session_start_safe();
if (!is_logged_in()) {
    header('Location: admin/login.php');
    exit;
}

$root = __DIR__;
$message = null;
$details = [];

/** Darf diese Datei aus der ZIP geschrieben werden? */
function darf_schreiben(string $rel): bool {
    // Kein Ausbrechen aus dem Verzeichnis, keine absoluten Pfade.
    if ($rel === '' || str_contains($rel, '..') || str_starts_with($rel, '/')) return false;

    // Die beiden Unantastbaren. Der Schlüssel ist das Wertvollste der Anlage, und
    // config.php enthält Datenbank- und Stripe-Zugangsdaten — beides darf ein
    // Update nie berühren, auch nicht versehentlich.
    if ($rel === 'config.php') return false;
    if (str_starts_with($rel, 'keys/')) return false;

    $name = basename($rel);
    if ($name === '.htaccess') return true;

    return in_array(strtolower(pathinfo($rel, PATHINFO_EXTENSION)), ['php', 'md', 'json'], true);
}

/** Aus `karacast-panel/lib/db.php` wird `lib/db.php`. */
function ohne_oberordner(string $path): string {
    $parts = explode('/', trim($path, '/'));
    if (count($parts) > 1 && str_starts_with($parts[0], 'karacast-panel')) array_shift($parts);
    return implode('/', $parts);
}

// ------------------------------------------------------------------ Aktionen
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    csrf_check();
    $action = $_POST['action'] ?? '';

    if ($action === 'entpacken') {
        $zipName = basename((string) ($_POST['zip'] ?? ''));
        $zipPath = $root . '/' . $zipName;

        if (!class_exists('ZipArchive')) {
            $message = 'Die PHP-Erweiterung zip fehlt auf diesem Server.';
        } elseif (!is_file($zipPath) || strtolower(pathinfo($zipPath, PATHINFO_EXTENSION)) !== 'zip') {
            $message = 'Diese ZIP gibt es hier nicht.';
        } else {
            $zip = new ZipArchive();
            if ($zip->open($zipPath) !== true) {
                $message = 'Die Datei lässt sich nicht öffnen — unvollständig hochgeladen?';
            } else {
                $written = 0; $skipped = 0;
                for ($i = 0; $i < $zip->numFiles; $i++) {
                    $entry = (string) $zip->getNameIndex($i);
                    if (str_ends_with($entry, '/')) continue;

                    $rel = ohne_oberordner($entry);
                    if (!darf_schreiben($rel)) {
                        $skipped++;
                        $details[] = ['übersprungen', $rel];
                        continue;
                    }

                    $target = $root . '/' . $rel;
                    $dir = dirname($target);
                    if (!is_dir($dir) && !@mkdir($dir, 0755, true) && !is_dir($dir)) {
                        $details[] = ['Ordner nicht anlegbar', $rel];
                        continue;
                    }

                    $data = $zip->getFromIndex($i);
                    if ($data === false || file_put_contents($target, $data) === false) {
                        $details[] = ['nicht schreibbar', $rel];
                        continue;
                    }
                    $written++;
                }
                $zip->close();
                $message = $written . ' Datei(en) eingespielt, ' . $skipped . ' bewusst ausgelassen '
                         . '(config.php und keys/ werden nie angefasst).';
            }
        }
    }

    if ($action === 'aufraeumen') {
        // Nur Ordner, deren Name genau dem Muster „name.Zahl" folgt — das ist die
        // Handschrift des Dateimanagers und kein Name, den hier je etwas anlegt.
        $removed = [];
        foreach (scandir($root) ?: [] as $entry) {
            if ($entry === '.' || $entry === '..') continue;
            $path = $root . '/' . $entry;
            if (!is_dir($path)) continue;
            if (!preg_match('/^[A-Za-z0-9_\-]+\.\d+$/', $entry)) continue;

            if (loeschen_rekursiv($path)) $removed[] = $entry;
        }
        $message = $removed
            ? count($removed) . ' Kopie(n) gelöscht: ' . implode(', ', $removed)
            : 'Nichts zu löschen — es gibt keine solchen Kopien.';
    }
}

/** Löscht einen Ordner samt Inhalt. Nur für die Kopien oben. */
function loeschen_rekursiv(string $path): bool {
    foreach (scandir($path) ?: [] as $entry) {
        if ($entry === '.' || $entry === '..') continue;
        $child = $path . '/' . $entry;
        is_dir($child) ? loeschen_rekursiv($child) : @unlink($child);
    }
    return @rmdir($path);
}

// --------------------------------------------------------------- Bestandsaufnahme
$zips = [];
foreach (glob($root . '/*.zip') ?: [] as $file) {
    $zips[basename($file)] = ['size' => filesize($file), 'at' => filemtime($file)];
}
arsort($zips);

$kopien = [];
foreach (scandir($root) ?: [] as $entry) {
    if ($entry === '.' || $entry === '..') continue;
    if (is_dir($root . '/' . $entry) && preg_match('/^[A-Za-z0-9_\-]+\.\d+$/', $entry)) {
        $kopien[] = $entry;
    }
}
?>
<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Update einspielen</title><?php require __DIR__ . '/admin/style.php'; ?></head><body>
<div class="wrap narrow">
    <div class="bar"><a href="admin/">← Panel</a><div class="spacer"></div></div>
    <h1>Update einspielen</h1>

    <?php if ($message): ?><p class="ok"><?= e($message) ?></p><?php endif; ?>

    <div class="card">
        <h2 style="margin-top:0">So geht es künftig</h2>
        <ol class="dim">
            <li>Die ZIP <strong>unentpackt</strong> ins Hauptverzeichnis hochladen — dorthin,
                wo auch <code>index.php</code> liegt.</li>
            <li>Hier auswählen und auf <em>Einspielen</em> drücken.</li>
            <li>Fertig. <code>config.php</code> und <code>keys/</code> bleiben unberührt,
                und es entstehen keine Kopien mit Zahlen im Namen.</li>
        </ol>
    </div>

    <div class="card">
        <h2 style="margin-top:0">Gefundene ZIP-Dateien</h2>
        <?php if (!$zips): ?>
            <p class="dim">Keine. Lade eine ZIP ins Hauptverzeichnis hoch, dann erscheint sie hier.</p>
        <?php else: ?>
            <?php foreach ($zips as $name => $info): ?>
                <form method="post" class="inline" style="margin-bottom:10px">
                    <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
                    <input type="hidden" name="action" value="entpacken">
                    <input type="hidden" name="zip" value="<?= e($name) ?>">
                    <button class="btn" type="submit">Einspielen</button>
                    <span class="mono" style="font-size:13px"><?= e($name) ?></span>
                    <span class="dim"><?= number_format($info['size'] / 1024, 0) ?> KB,
                        <?= e(date('d.m.Y H:i', $info['at'])) ?></span>
                </form>
            <?php endforeach; ?>
        <?php endif; ?>
    </div>

    <div class="card">
        <h2 style="margin-top:0">Doppelte Ordner aufräumen</h2>
        <p class="dim">Ordner wie <code>admin.198</code> oder <code>api.4497</code> stammen vom
           Dateimanager, wenn er beim Entpacken einen Namenskonflikt findet. Sie sind über das
           Internet erreichbar und enthalten veralteten Code — also weg damit.</p>
        <?php if (!$kopien): ?>
            <p class="ok">Keine solchen Ordner vorhanden.</p>
        <?php else: ?>
            <p class="mono" style="font-size:13px"><?= e(implode(' · ', $kopien)) ?></p>
            <p class="dim"><strong><?= count($kopien) ?></strong> Ordner werden gelöscht.
               Deine echten Ordner (<code>admin</code>, <code>api</code>, <code>lib</code>,
               <code>lang</code>, <code>keys</code>) sind nicht betroffen — gelöscht wird nur,
               was auf einen Punkt und eine Zahl endet.</p>
            <form method="post" class="inline">
                <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
                <input type="hidden" name="action" value="aufraeumen">
                <button class="btn bad" type="submit">Kopien löschen</button>
            </form>
        <?php endif; ?>
    </div>

    <?php if ($details): ?>
    <div class="card">
        <h2 style="margin-top:0">Was ausgelassen wurde</h2>
        <p class="mono" style="font-size:12px">
            <?php foreach (array_slice($details, 0, 40) as [$grund, $datei]): ?>
                <?= e($datei) ?> — <?= e($grund) ?><br>
            <?php endforeach; ?>
        </p>
    </div>
    <?php endif; ?>
</div></body></html>
