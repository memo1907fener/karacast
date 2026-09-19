<?php
/** Ein Gerät: freischalten, sperren, Notiz, Lizenz auf ein anderes Gerät übertragen. */
require __DIR__ . '/../lib/db.php';
require __DIR__ . '/../lib/util.php';
require __DIR__ . '/../lib/license.php';
require __DIR__ . '/../lib/stripe.php';
require __DIR__ . '/../lib/auth.php';
require_admin();

$id = (int) ($_GET['id'] ?? 0);
$stmt = db()->prepare('SELECT * FROM devices WHERE id = ?');
$stmt->execute([$id]);
$device = $stmt->fetch();
if (!$device) exit('Gerät nicht gefunden.');

$message = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    csrf_check();
    $action = $_POST['action'] ?? '';

    if ($action === 'activate') {
        activate_device($id, clean_text($_POST['order'] ?? 'manuell', 64));
        $message = 'Freigeschaltet. Der Fernseher übernimmt es beim nächsten Nachfragen — '
                 . 'auf dem Aktivierungsbildschirm binnen zehn Sekunden.';
    } elseif ($action === 'block') {
        db()->prepare("UPDATE devices SET status = 'blocked' WHERE id = ?")->execute([$id]);
        $message = 'Gesperrt.';
    } elseif ($action === 'unblock') {
        // Zurück in den Zustand, der zum Ticket passt: mit Ticket frei, sonst Testphase.
        $back = !empty($device['ticket']) ? 'active' : 'trial';
        db()->prepare('UPDATE devices SET status = ? WHERE id = ?')->execute([$back, $id]);
        $message = 'Entsperrt.';
    } elseif ($action === 'note') {
        db()->prepare('UPDATE devices SET note = ? WHERE id = ?')
            ->execute([clean_text($_POST['note'] ?? '', 120), $id]);
        $message = 'Notiz gespeichert.';
    } elseif ($action === 'recheck') {
        // Bezahlt, aber nicht freigeschaltet? Dann hat der Webhook nicht
        // hergefunden. Hier wird Stripe direkt gefragt — und was Stripe sagt,
        // zählt genauso viel wie über den Webhook.
        try {
            $paid = stripe_find_paid_session((string) $device['code']);
            if (!$paid) {
                $message = 'Bei Stripe liegt keine bezahlte Zahlung für dieses Gerät. '
                         . 'Wenn du sicher bist, dass bezahlt wurde: steht im Stripe-Konto '
                         . 'derselbe Modus (Test oder Live) wie in config.php?';
            } else {
                $result = stripe_fulfil($paid);
                $message = match ($result) {
                    'activated' => 'Zahlung gefunden und freigeschaltet.',
                    'repaired'  => 'Die Zahlung war verbucht, das Gerät aber nicht '
                                 . 'freigeschaltet. Das ist jetzt nachgeholt.',
                    'already'   => 'Diese Zahlung war bereits verbucht, das Gerät ist frei.',
                    'unpaid'    => 'Die Sitzung bei Stripe ist nicht (vollständig) bezahlt.',
                    default     => 'Die Zahlung ließ sich keinem Gerät zuordnen.',
                };
            }
        } catch (Throwable $e) {
            error_log('[karacast] Nachprüfen: ' . $e->getMessage());
            // Hier, hinter der Anmeldung, steht der echte Grund. Nach außen wäre er
            // eine Landkarte für Angreifer; für dich ist er der Unterschied zwischen
            // „irgendwas klemmt" und „der Schlüssel ist vom falschen Konto".
            $message = 'Stripe meldet: ' . $e->getMessage();
        }
    } elseif ($action === 'confirm_id' || $action === 'drop_id') {
        $hash = strtolower(clean_text($_POST['hash'] ?? '', 12));
        if (preg_match('/^[0-9a-f]{12}$/', $hash)) {
            db()->prepare('DELETE FROM pending_ids WHERE id_hash = ? AND device_id = ?')
                ->execute([$hash, $id]);
            if ($action === 'confirm_id') {
                db()->prepare('INSERT IGNORE INTO device_ids (device_id, id_hash) VALUES (?, ?)')
                    ->execute([$id, $hash]);
                // Das Ticket muss die neue Kennung enthalten, sonst hätte die
                // Bestätigung nichts bewirkt.
                if ($device['status'] === 'active') {
                    activate_device($id, (string) $device['order_ref']);
                }
                $message = 'Kennung übernommen. Der Fernseher bekommt beim nächsten '
                         . 'Nachfragen ein Ticket, das sie enthält.';
            } else {
                $message = 'Kennung verworfen.';
            }
        }
    } elseif ($action === 'transfer') {
        // Erst die Trennzeichen weg, dann kürzen — nicht umgekehrt. Das Panel
        // zeigt den Code als A1:B2:C3:D4:E5:F6, und das sind siebzehn Zeichen;
        // wer ihn von dort kopiert, hätte sonst nur elf davon eingetippt und
        // bekäme die irreführende Auskunft, es gebe kein solches Gerät.
        $raw = is_string($_POST['target'] ?? null) ? $_POST['target'] : '';
        $target = strtoupper(substr(preg_replace('/[^0-9A-Fa-f]/', '', $raw) ?? '', 0, 12));
        // Dieselbe Suche wie überall sonst: erst über die Kennungen, dann über den
        // angezeigten Code. Ein Gerät, dessen Zeilen zusammengeführt wurden, wird
        // sonst nicht gefunden, obwohl es im Panel sichtbar ist.
        $other = $target !== '' ? device_by_code($target) : null;
        if (strlen($target) !== 12) {
            $message = 'Ein Gerätecode hat zwölf Zeichen (0-9, A-F). Doppelpunkte darfst '
                     . 'du mit eintippen, alles andere nicht.';
        } elseif (!$other || (int) $other['id'] === $id) {
            $message = 'Kein Gerät mit diesem Code. Es muss sich mindestens einmal gemeldet haben.';
        } elseif ($other['status'] === 'blocked') {
            // Gesperrt wird bei Rückbuchung. Eine Übertragung wäre der bequeme Weg,
            // das aus Versehen rückgängig zu machen — also erst entsperren, dann
            // übertragen, und beides bewusst.
            $message = 'Das Zielgerät ist gesperrt. Erst dort entsperren, dann übertragen.';
        } else {
            // Erst dem neuen geben, dann dem alten nehmen: bricht etwas ab, steht
            // der Kunde schlimmstenfalls mit zwei Lizenzen da statt mit keiner.
            activate_device((int) $other['id'], 'transfer:' . $device['code']);
            // Das alte Gerät wird gesperrt, nicht in die Testphase zurückgesetzt.
            // „trial" hieße: es läuft noch vierzehn Tage weiter und stünde dann mit
            // derselben Lizenz zweimal da. Das Ticket in seinem Speicher gilt zwar
            // weiter — offline lässt sich eine Unterschrift nicht zurücknehmen —,
            // aber beim nächsten Nachfragen erfährt es, dass es gesperrt ist.
            db()->prepare(
                "UPDATE devices SET status = 'blocked', ticket = NULL,
                        transfers = transfers + 1 WHERE id = ?"
            )->execute([$id]);
            $message = 'Auf ' . $target . ' übertragen. Dieses Gerät ist damit gesperrt.';
        }
    }

    $stmt->execute([$id]);
    $device = $stmt->fetch();
}

$ids = device_ids($id);

$pend = db()->prepare(
    'SELECT * FROM pending_ids WHERE device_id = ? ORDER BY last_seen DESC'
);
$pend->execute([$id]);
$pending = $pend->fetchAll();

// Zusammengeführte Karteileiche? Dann führt nur ein Hinweis weiter, keine Knöpfe.
$mergedInto = str_starts_with((string) $device['order_ref'], 'merged:')
    ? (int) substr((string) $device['order_ref'], 7)
    : 0;
$trialDays = (int) (config()['trial_days'] ?? 14);
$left = $trialDays - (int) ((time() - strtotime($device['first_seen'])) / 86400);
?>
<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title><?= e($device['code']) ?> — Karacast Panel</title><?php require __DIR__ . '/style.php'; ?></head><body>
<div class="wrap narrow">
    <div class="bar">
        <a href="index.php">← Geräte</a>
        <div class="spacer"></div>
    </div>

    <h1 class="mono"><?= e(implode(':', str_split($device['code'], 2))) ?></h1>
    <p>
        <span class="tag <?= e($device['status']) ?>">
            <?= ['trial' => 'Testphase', 'active' => 'Freigeschaltet', 'blocked' => 'Gesperrt'][$device['status']] ?>
        </span>
        <?php if ($device['status'] === 'trial'): ?>
            <span class="dim"><?= $left > 0 ? "noch $left Tage" : 'abgelaufen' ?></span>
        <?php endif; ?>
    </p>

    <?php if ($message): ?><p class="ok"><?= e($message) ?></p><?php endif; ?>

    <?php if ($mergedInto): ?>
        <div class="card">
            <p class="err" style="margin-top:0">Diese Zeile gehört zu keinem Fernseher mehr.</p>
            <p class="dim">Sie war ein zweiter Eintrag desselben Geräts und wurde
               zusammengeführt. Alle Kennungen, die Lizenz und die Testphase stehen
               jetzt drüben. Hier gibt es nichts mehr freizuschalten.</p>
            <p><a class="btn" href="device.php?id=<?= $mergedInto ?>">Zum richtigen Gerät</a></p>
        </div>
    <?php endif; ?>

    <div class="card">
        <p class="dim">Modell</p><p><?= e($device['model'] ?: '—') ?></p>
        <p class="dim">App-Version</p><p><?= e($device['version'] ?: '—') ?></p>
        <p class="dim">Erstmals gesehen</p><p><?= e(date('d.m.Y H:i', strtotime($device['first_seen']))) ?></p>
        <p class="dim">Zuletzt gesehen</p><p><?= e(date('d.m.Y H:i', strtotime($device['last_seen']))) ?></p>
        <?php if ($device['activated_at']): ?>
            <p class="dim">Freigeschaltet am</p><p><?= e(date('d.m.Y H:i', strtotime($device['activated_at']))) ?>
               <span class="dim">(<?= e($device['order_ref']) ?>)</span></p>
        <?php endif; ?>
        <p class="dim">Kennungen (<?= count($ids) ?>)</p>
        <p class="mono" style="font-size:12px"><?= e(implode('  ·  ', $ids)) ?></p>
    </div>

    <?php
    // Auch das prepare() gehört in den Versuch hinein: gibt es die Tabelle noch
    // nicht — etwa weil upgrade.php noch nicht lief —, scheitert schon dieser
    // Schritt, und die ganze Geräteseite wäre wegen einer Nebensache unbenutzbar.
    try {
        $pay = db()->prepare('SELECT * FROM payments WHERE device_id = ? ORDER BY id DESC');
        $pay->execute([$id]);
        $payments = $pay->fetchAll();
    } catch (Throwable $e) {
        $payments = [];
    }
    ?>
    <?php if ($payments): ?>
    <div class="card">
        <h2 style="margin-top:0">Zahlungen</h2>
        <table>
            <tr><th>Wann</th><th>Betrag</th><th>E-Mail</th><th>Vorgang</th><th></th></tr>
            <?php foreach ($payments as $p): ?>
            <tr>
                <td class="dim"><?= e(date('d.m.Y H:i', strtotime((string) $p['created_at']))) ?></td>
                <td><?= e(number_format($p['amount_cents'] / 100, 2, ',', '.')) ?>&nbsp;<?= e(strtoupper((string) $p['currency'])) ?></td>
                <td class="dim"><?= e((string) $p['email']) ?></td>
                <td class="mono dim" style="font-size:11px"><?= e((string) $p['session_id']) ?></td>
                <td><?= $p['refunded_at']
                        ? '<span class="tag blocked">' . e((string) ($p['note'] ?: 'erstattet')) . '</span>'
                        : '<span class="tag active">bezahlt</span>' ?></td>
            </tr>
            <?php endforeach; ?>
        </table>
    </div>
    <?php endif; ?>

    <?php if ($pending && !$mergedInto): ?>
    <div class="card">
        <h2 style="margin-top:0">Neue Kennungen warten</h2>
        <p class="dim">Jemand hat sich mit dem Code dieses Geräts gemeldet und dabei
           Kennungen mitgebracht, die hier noch nicht bekannt sind. Bei einem
           freigeschalteten Gerät wird das nicht von allein übernommen: der
           Gerätecode steht auf dem Fernsehschirm und ist damit kein Geheimnis.</p>
        <p class="dim"><strong>Übernimm eine Kennung nur, wenn du weißt, warum sie
           dazugekommen ist</strong> — etwa weil du die App-Daten gelöscht, das Gerät
           neu aufgesetzt oder von WLAN auf Kabel gewechselt hast. Sonst verwerfen.</p>
        <table>
            <tr><th>Kennung</th><th>Zuerst</th><th>Zuletzt</th><th>Mal</th><th></th></tr>
            <?php foreach ($pending as $p): ?>
            <tr>
                <td class="mono"><?= e($p['id_hash']) ?></td>
                <td class="dim"><?= e(date('d.m.Y H:i', strtotime($p['first_seen']))) ?></td>
                <td class="dim"><?= e(date('d.m.Y H:i', strtotime($p['last_seen']))) ?></td>
                <td class="dim"><?= (int) $p['seen_count'] ?></td>
                <td>
                    <form method="post" class="inline">
                        <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
                        <input type="hidden" name="hash" value="<?= e($p['id_hash']) ?>">
                        <button class="btn ghost" name="action" value="confirm_id" type="submit">Gehört dazu</button>
                        <button class="btn bad" name="action" value="drop_id" type="submit">Verwerfen</button>
                    </form>
                </td>
            </tr>
            <?php endforeach; ?>
        </table>
    </div>
    <?php endif; ?>

    <?php if ($device['status'] !== 'active' && !$mergedInto && stripe_enabled()): ?>
    <div class="card">
        <h2 style="margin-top:0">Zahlung bei Stripe nachprüfen</h2>
        <p class="dim">Wenn der Kunde bezahlt hat, hier aber noch „Testphase" steht.
           Fragt Stripe nach den letzten Zahlungen und schaltet frei, wenn eine zu
           diesem Gerät gehört und bezahlt ist.</p>
        <form method="post" class="inline">
            <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
            <input type="hidden" name="action" value="recheck">
            <button class="btn ghost" type="submit">Bei Stripe nachsehen</button>
        </form>
    </div>
    <?php endif; ?>

    <?php if ($device['status'] !== 'active' && !$mergedInto): ?>
    <div class="card">
        <h2 style="margin-top:0">Freischalten</h2>
        <p class="dim">Stellt das Ticket aus. Für Barzahlung, eigene Geräte oder Kulanz.</p>
        <form method="post">
            <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
            <input type="hidden" name="action" value="activate">
            <label>Vermerk<input type="text" name="order" placeholder="z. B. bar, eigenes Gerät, Rechnung 2026-014" value="manuell"></label>
            <button class="btn" type="submit">Dauerhaft freischalten</button>
        </form>
    </div>
    <?php endif; ?>

    <div class="card">
        <h2 style="margin-top:0">Notiz</h2>
        <form method="post">
            <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
            <input type="hidden" name="action" value="note">
            <label>Wem gehört dieser Fernseher?
                <input type="text" name="note" value="<?= e($device['note']) ?>" placeholder="Wohnzimmer Oma / Kunde Müller">
            </label>
            <button class="btn ghost" type="submit">Speichern</button>
        </form>
    </div>

    <?php if ($device['status'] === 'active'): ?>
    <div class="card">
        <h2 style="margin-top:0">Auf ein anderes Gerät übertragen</h2>
        <p class="dim">Bei einem neuen Fernseher. Das Zielgerät muss sich einmal gemeldet
           haben, damit es hier bekannt ist. Bisher übertragen: <?= (int) $device['transfers'] ?>×</p>
        <form method="post">
            <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
            <input type="hidden" name="action" value="transfer">
            <label>Gerätecode des neuen Fernsehers
                <input type="text" name="target" placeholder="A1B2C3D4E5F6" required>
            </label>
            <button class="btn ghost" type="submit">Übertragen</button>
        </form>
    </div>
    <?php endif; ?>

    <?php if (!$mergedInto): ?>
    <div class="card">
        <h2 style="margin-top:0">Sperren</h2>
        <p class="dim">Bei Rückbuchung. Der Fernseher zeigt dann „Diese Lizenz ist gesperrt"
           statt „Testphase vorbei" — eine ehrlichere Auskunft.</p>
        <form method="post" class="inline">
            <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
            <input type="hidden" name="action" value="<?= $device['status'] === 'blocked' ? 'unblock' : 'block' ?>">
            <button class="btn <?= $device['status'] === 'blocked' ? 'ghost' : 'bad' ?>" type="submit">
                <?= $device['status'] === 'blocked' ? 'Entsperren' : 'Sperren' ?>
            </button>
        </form>
    </div>
    <?php endif; ?>
</div></body></html>
