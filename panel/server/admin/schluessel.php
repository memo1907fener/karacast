<?php
/**
 * Der Schlüssel — sichern, im Notfall neu erzeugen, Lizenzen neu ausstellen.
 *
 * Diese Seite gibt es, weil genau das passiert ist, wogegen sie hilft: ein FTP-Abgleich
 * hat `keys/private.pem` weggeräumt, und damit war keine einzige Lizenz mehr
 * ausstellbar. Drei Knöpfe, in der Reihenfolge ihrer Dringlichkeit:
 *
 *  1. **Sichern.** Solange es einen Schlüssel gibt, kann man ihn herunterladen. Eine
 *     Kopie in deinem Passwortspeicher kostet nichts und rettet alles.
 *  2. **Neu erzeugen.** Nur wenn keiner mehr da ist — und mit voller Ansage, was das
 *     kostet: jede bisher ausgestellte Lizenz wird wertlos, bis die App eine neue
 *     Fassung mit dem neuen öffentlichen Schlüssel bekommt.
 *  3. **Alles neu ausstellen.** Nach einem Schlüsselwechsel bekommen alle bezahlten
 *     Geräte ein frisches Ticket, das sie beim nächsten Nachfragen abholen.
 */
require __DIR__ . '/../lib/db.php';
require __DIR__ . '/../lib/util.php';
require __DIR__ . '/../lib/license.php';
require __DIR__ . '/../lib/auth.php';
require_admin();

$message = null;
$newKey  = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    csrf_check();
    $action = $_POST['action'] ?? '';

    if ($action === 'download' && has_key()) {
        // Der eine Ort, an dem der Schlüssel den Server verlassen darf: auf Zuruf
        // seines Besitzers, über HTTPS, hinter der Anmeldung.
        $data = (string) file_get_contents(private_key_path());
        header('Content-Type: application/x-pem-file');
        header('Content-Disposition: attachment; filename="karacast-private-'
             . date('Y-m-d') . '.pem"');
        header('Content-Length: ' . strlen($data));
        header('Cache-Control: no-store');
        echo $data;
        exit;
    }

    if ($action === 'create') {
        if (has_key()) {
            // Niemals einen vorhandenen Schlüssel überschreiben. Das wäre derselbe
            // Verlust noch einmal, nur diesmal auf Knopfdruck.
            $message = 'Es gibt bereits einen Schlüssel. Er wird nicht überschrieben — '
                     . 'sichere ihn lieber.';
        } elseif (($_POST['verstanden'] ?? '') !== '1') {
            $message = 'Bitte bestätige zuerst, dass du die Folgen kennst.';
        } else {
            try {
                $newKey  = create_keypair();
                $message = 'Neues Schlüsselpaar erzeugt. Jetzt kommt der wichtige Teil: '
                         . 'der öffentliche Schlüssel muss in die App, und die App muss neu '
                         . 'gebaut und auf jedes Gerät gebracht werden.';
            } catch (Throwable $e) {
                error_log('[karacast] Schlüssel: ' . $e->getMessage());
                $message = 'Fehlgeschlagen: ' . $e->getMessage();
            }
        }
    }

    if ($action === 'reissue') {
        if (!has_key()) {
            $message = 'Ohne Schlüssel lässt sich nichts ausstellen.';
        } else {
            $devices = db()->query("SELECT id, order_ref FROM devices WHERE status = 'active'")
                           ->fetchAll();
            $done = 0;
            $failed = 0;
            foreach ($devices as $device) {
                try {
                    activate_device((int) $device['id'], (string) $device['order_ref']);
                    $done++;
                } catch (Throwable $e) {
                    error_log('[karacast] Neuausstellung ' . $device['id'] . ': ' . $e->getMessage());
                    $failed++;
                }
            }
            $message = $done . ' Lizenz(en) neu ausgestellt'
                     . ($failed ? ', ' . $failed . ' fehlgeschlagen (siehe Fehlerprotokoll)' : '')
                     . '. Die Geräte holen sie sich beim nächsten Nachfragen von selbst.';
        }
    }
}

$active = (int) db()->query("SELECT COUNT(*) FROM devices WHERE status = 'active'")->fetchColumn();
?>
<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Schlüssel — Karacast Panel</title><?php require __DIR__ . '/style.php'; ?></head><body>
<div class="wrap narrow">
    <div class="bar"><a href="index.php">← Geräte</a><div class="spacer"></div></div>
    <h1>Der Schlüssel</h1>

    <?php if ($message): ?><p class="ok"><?= e($message) ?></p><?php endif; ?>

    <?php if ($newKey !== null): ?>
        <div class="card">
            <h2 style="margin-top:0">Der neue öffentliche Schlüssel</h2>
            <p class="dim">Gehört in <code>util/LicenseVerifier.kt</code> →
               <code>PUBLIC_KEY_BASE64</code>. Danach App neu bauen und auf alle Geräte
               bringen — vorher erkennt kein Gerät die neuen Tickets an.</p>
            <textarea readonly rows="3" onclick="this.select()"><?= e($newKey) ?></textarea>
        </div>
    <?php endif; ?>

    <div class="card">
        <h2 style="margin-top:0">Zustand</h2>
        <?php if (has_key()): ?>
            <p><span class="tag active">Schlüssel vorhanden</span></p>
            <p class="dim mono" style="font-size:12px"><?= e(private_key_path()) ?></p>
        <?php else: ?>
            <p><span class="tag blocked">kein Schlüssel</span></p>
            <p class="dim">Erwartet unter <code><?= e(private_key_path()) ?></code></p>
        <?php endif; ?>
        <p class="dim"><?= $active ?> Gerät(e) sind derzeit freigeschaltet.</p>
    </div>

    <?php if (has_key()): ?>
    <div class="card">
        <h2 style="margin-top:0">Sichern</h2>
        <p class="dim">Lade die Datei herunter und lege sie in deinen Passwortspeicher
           oder auf einen Stick — irgendwohin, wo kein FTP-Programm sie findet. Wer sie
           hat, kann Lizenzen ausstellen; behandle sie wie ein Bankpasswort. Und: beim
           Hochladen des Panels den Ordner <code>keys/</code> künftig auslassen.</p>
        <form method="post" class="inline">
            <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
            <input type="hidden" name="action" value="download">
            <button class="btn" type="submit">private.pem herunterladen</button>
        </form>
    </div>
    <?php else: ?>
    <div class="card">
        <h2 style="margin-top:0">Neues Schlüsselpaar erzeugen</h2>
        <p class="dim">Erst wenn feststeht, dass es keine Sicherung mehr gibt. Was danach gilt:</p>
        <ul class="dim">
            <li>Alle bisher ausgestellten Tickets sind wertlos — sie sind mit dem alten
                Schlüssel unterschrieben.</li>
            <li>Die App braucht eine neue Fassung mit dem neuen öffentlichen Schlüssel,
                und die muss auf <strong>jedes</strong> Gerät.</li>
            <li>Danach hier <em>Alle Lizenzen neu ausstellen</em> drücken — die Geräte holen
                sich die neuen Tickets von selbst.</li>
            <li>Bezahlte Kunden verlieren nichts: ihre Zahlung steht in der Datenbank, die
                Freischaltung bleibt bestehen.</li>
        </ul>
        <form method="post">
            <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
            <input type="hidden" name="action" value="create">
            <label class="check" style="display:flex; gap:10px; align-items:flex-start">
                <input type="checkbox" name="verstanden" value="1" required>
                <span>Ich habe nachgesehen: es gibt keine Sicherung. Mir ist klar, dass die
                      App neu gebaut und verteilt werden muss.</span>
            </label>
            <button class="btn bad" type="submit">Neues Schlüsselpaar erzeugen</button>
        </form>
    </div>
    <?php endif; ?>

    <div class="card">
        <h2 style="margin-top:0">Alle Lizenzen neu ausstellen</h2>
        <p class="dim">Stellt für jedes freigeschaltete Gerät ein frisches Ticket mit dem
           aktuellen Schlüssel aus. Nach einem Schlüsselwechsel nötig, sonst überflüssig.
           Schadet nie: der Status bleibt, nur die Unterschrift ist neu.</p>
        <form method="post" class="inline">
            <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
            <input type="hidden" name="action" value="reissue">
            <button class="btn ghost" type="submit"><?= $active ?> Lizenz(en) neu ausstellen</button>
        </form>
    </div>
</div></body></html>
