<?php
/** Die Geräteliste — die Seite, auf der du die meiste Zeit sein wirst. */
require __DIR__ . '/../lib/db.php';
require __DIR__ . '/../lib/util.php';
require __DIR__ . '/../lib/license.php';
require __DIR__ . '/../lib/stripe.php';
require __DIR__ . '/../lib/auth.php';
require_admin();

/*
 * Der Verbindungstest.
 *
 * Eine Fehlermeldung wie „Stripe war nicht erreichbar" nennt fünf mögliche Ursachen
 * und entscheidet keine davon. Dieser Knopf fragt Stripe einmal ganz schlicht nach
 * den letzten Sitzungen und schreibt hin, was tatsächlich zurückkam — fehlende
 * cURL-Erweiterung, gesperrter Ausgang, falscher oder eingeschränkter Schlüssel,
 * falsches Konto. Danach weiß man es, statt zu raten.
 */
$stripeTest = null;
if ($_SERVER['REQUEST_METHOD'] === 'POST' && ($_POST['action'] ?? '') === 'stripe_test') {
    csrf_check();
    try {
        $list  = stripe_api('GET', 'checkout/sessions', ['limit' => 3]);
        $count = count($list['data'] ?? []);
        $live  = ($list['data'][0]['livemode'] ?? null);
        $stripeTest = [
            'ok'   => true,
            'text' => 'Verbindung steht. ' . $count . ' Sitzung(en) abrufbar'
                    . ($live === null ? '.' : ($live ? ' — Konto im Live-Modus.' : ' — Konto im Testmodus.')),
        ];
    } catch (Throwable $e) {
        $stripeTest = ['ok' => false, 'text' => $e->getMessage()];
    }
}

// is_string, weil ?q[]=x aus einem Array eine TypeError-Seite machen würde.
$search = trim(is_string($_GET['q'] ?? null) ? $_GET['q'] : '');
$filter = is_string($_GET['s'] ?? null) ? $_GET['s'] : '';

$sql = 'SELECT * FROM devices';
$where = [];
$args  = [];
if ($search !== '') {
    $where[] = '(code LIKE ? OR model LIKE ? OR note LIKE ?)';
    $like = '%' . $search . '%';
    array_push($args, $like, $like, $like);
}
if (in_array($filter, ['trial', 'active', 'blocked'], true)) {
    $where[] = 'status = ?';
    $args[] = $filter;
}
if ($where) $sql .= ' WHERE ' . implode(' AND ', $where);
$sql .= ' ORDER BY last_seen DESC LIMIT 300';

$stmt = db()->prepare($sql);
$stmt->execute($args);
$devices = $stmt->fetchAll();

$counts = [];
foreach (db()->query('SELECT status, COUNT(*) n FROM devices GROUP BY status') as $row) {
    $counts[$row['status']] = (int) $row['n'];
}
$trialDays = (int) (config()['trial_days'] ?? 14);
?>
<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Geräte — Karacast Panel</title><?php require __DIR__ . '/style.php'; ?></head><body>
<div class="wrap">
    <div class="bar">
        <h1>Geräte</h1>
        <div class="spacer"></div>
        <a class="btn ghost" href="codes.php">Einlösecodes</a>
        <a class="btn ghost" href="schluessel.php">Schlüssel</a>
        <a class="btn ghost" href="logout.php">Abmelden</a>
    </div>

    <div class="bar">
        <span class="dim">
            <?= (int) ($counts['active'] ?? 0) ?> freigeschaltet ·
            <?= (int) ($counts['trial'] ?? 0) ?> in der Testphase ·
            <?= (int) ($counts['blocked'] ?? 0) ?> gesperrt
        </span>
        <div class="spacer"></div>
        <form method="get" class="bar" style="margin:0">
            <input type="text" name="q" placeholder="Code, Modell, Notiz" value="<?= e($search) ?>" style="width:240px">
            <button class="btn ghost" type="submit">Suchen</button>
            <?php if ($search !== '' || $filter !== ''): ?>
                <a class="btn ghost" href="index.php">Alle</a>
            <?php endif; ?>
        </form>
    </div>

    <details class="card">
        <summary>Stripe — wie es gerade eingerichtet ist</summary>
        <?php
        $stripe = config()['stripe'] ?? [];
        $key    = trim((string) ($stripe['secret_key'] ?? ''));
        $hook   = trim((string) ($stripe['webhook_secret'] ?? ''));
        $mode   = str_starts_with($key, 'sk_live_') ? 'Live — es fließt echtes Geld'
                : (str_starts_with($key, 'sk_test_') ? 'Testmodus' : '—');
        $webhookUrl = rtrim((string) (config()['base_url'] ?? ''), '/') . '/webhook.php';
        ?>
        <p class="dim">Zwei Fehler kosten hier am meisten Zeit, und beide sieht man sonst nicht:
           ein Schlüssel im Testmodus bei einer echten Zahlung, und ein Webhook, der nie
           eingetragen wurde. Deshalb steht es hier.</p>
        <p><span class="dim">Modus:</span> <strong><?= e($mode) ?></strong></p>
        <p><span class="dim">Geheimer Schlüssel:</span>
            <?= $key !== '' ? '<span class="tag active">gesetzt</span>' : '<span class="tag blocked">fehlt</span>' ?></p>
        <p><span class="dim">Webhook-Geheimnis:</span>
            <?= $hook !== '' ? '<span class="tag active">gesetzt</span>' : '<span class="tag blocked">fehlt</span>' ?></p>
        <p class="dim">Diese Adresse gehört im Stripe-Dashboard unter <em>Entwickler → Webhooks</em>
           hinterlegt, mit den Ereignissen <code>checkout.session.completed</code>,
           <code>charge.refunded</code>, <code>charge.dispute.created</code>:</p>
        <p class="mono"><?= e($webhookUrl) ?></p>
        <p class="dim">Hat eine Zahlung ihr Gerät trotzdem nicht erreicht: das Gerät öffnen
           und dort <em>Bei Stripe nachsehen</em> drücken.</p>

        <form method="post" class="inline" style="margin-top:12px">
            <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
            <input type="hidden" name="action" value="stripe_test">
            <button class="btn ghost" type="submit">Verbindung zu Stripe testen</button>
        </form>

        <?php if ($stripeTest): ?>
            <p class="<?= $stripeTest['ok'] ? 'ok' : 'err' ?>" style="margin-top:12px">
                <?= e($stripeTest['text']) ?>
            </p>
            <?php if (!$stripeTest['ok']): ?>
                <ul class="dim" style="font-size:14px">
                    <li><strong>„cURL fehlt"</strong> — die PHP-Erweiterung ist beim Hoster nicht aktiv.
                        Im Hosting-Panel einschalten.</li>
                    <li><strong>„Invalid API Key" / „No such …"</strong> — der Schlüssel gehört zu einem
                        anderen Konto, ist ein eingeschränkter Schlüssel ohne Leserecht, oder Test- und
                        Live-Modus sind vermischt.</li>
                    <li><strong>„nicht erreichbar" / Zeitüberschreitung</strong> — der Hoster lässt keine
                        ausgehenden Verbindungen zu api.stripe.com zu. Dann hilft nur der Hoster.</li>
                </ul>
            <?php endif; ?>
        <?php endif; ?>
    </details>

    <details class="card">
        <summary>Öffentlicher Schlüssel für die App</summary>
        <p class="dim">Dieselbe Zeichenkette, die install.php nach der Einrichtung
           gezeigt hat. Sie steht hier, damit sie nicht verloren geht: ohne sie lässt
           sich keine neue App-Version bauen, die deine Tickets anerkennt. Sie ist
           nicht geheim — prüfen kann man damit, unterschreiben nicht.</p>
        <p class="dim">Gehört in <code>util/LicenseVerifier.kt</code> →
           <code>PUBLIC_KEY_BASE64</code>.</p>
        <textarea readonly rows="3" onclick="this.select()"><?= e(has_key() ? public_key_base64() : 'Noch kein Schlüssel vorhanden.') ?></textarea>
    </details>

    <?php if (!$devices): ?>
        <p class="empty">
            Noch kein Gerät gemeldet.<br>
            Trage in der App unter <em>Einstellungen → Lizenz</em> die Adresse
            <code><?= e(config()['base_url']) ?>/api</code> ein und drücke
            <em>Jetzt prüfen</em> — dann erscheint der Fernseher hier.
        </p>
    <?php else: ?>
    <table>
        <tr>
            <th>Gerätecode</th><th>Status</th><th>Modell</th><th>Version</th>
            <th>Notiz</th><th>Zuletzt gesehen</th><th></th>
        </tr>
        <?php foreach ($devices as $d):
            $left = $trialDays - (int) ((time() - strtotime($d['first_seen'])) / 86400);
        ?>
        <tr>
            <td class="mono"><?= e(implode(':', str_split($d['code'], 2))) ?></td>
            <td>
                <span class="tag <?= e($d['status']) ?>">
                    <?= ['trial' => 'Testphase', 'active' => 'Frei', 'blocked' => 'Gesperrt'][$d['status']] ?>
                </span>
                <?php if ($d['status'] === 'trial'): ?>
                    <span class="dim"><?= $left > 0 ? "noch $left T" : 'abgelaufen' ?></span>
                <?php endif; ?>
            </td>
            <td><?= e($d['model']) ?></td>
            <td class="dim"><?= e($d['version']) ?></td>
            <td class="dim"><?= e($d['note']) ?></td>
            <td class="dim"><?= e(date('d.m.Y H:i', strtotime($d['last_seen']))) ?></td>
            <td><a href="device.php?id=<?= (int) $d['id'] ?>">öffnen</a></td>
        </tr>
        <?php endforeach; ?>
    </table>
    <?php endif; ?>
</div></body></html>
