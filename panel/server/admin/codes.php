<?php
/** Einlösecodes: acht Zeichen, die jemand am Fernseher eintippen kann. */
require __DIR__ . '/../lib/db.php';
require __DIR__ . '/../lib/util.php';
require __DIR__ . '/../lib/auth.php';
require_admin();

$created = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    csrf_check();
    if (($_POST['action'] ?? '') === 'create') {
        $code = new_redeem_code();
        db()->prepare('INSERT INTO redeem_codes (code, created_at, note) VALUES (?, NOW(), ?)')
            ->execute([$code, clean_text($_POST['note'] ?? '', 120)]);
        $created = $code;
    }
}

$codes = db()->query(
    'SELECT c.*, d.code AS device_code FROM redeem_codes c
     LEFT JOIN devices d ON d.id = c.device_id
     ORDER BY c.created_at DESC LIMIT 200'
)->fetchAll();
?>
<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Einlösecodes — Karacast Panel</title><?php require __DIR__ . '/style.php'; ?></head><body>
<div class="wrap">
    <div class="bar">
        <a href="index.php">← Geräte</a>
        <div class="spacer"></div>
        <a class="btn ghost" href="logout.php">Abmelden</a>
    </div>

    <h1>Einlösecodes</h1>
    <p class="dim">Für alle, die den QR-Code nicht scannen können. Ein Code gilt einmal
       und bindet sich beim Einlösen an den Fernseher, der ihn eingibt.</p>

    <?php if ($created): ?>
        <div class="card">
            <p class="dim">Neuer Code</p>
            <p class="mono" style="font-size:28px; letter-spacing:.08em"><?= e($created) ?></p>
        </div>
    <?php endif; ?>

    <form method="post" class="card">
        <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
        <input type="hidden" name="action" value="create">
        <label>Wofür? <input type="text" name="note" placeholder="Kunde Müller, Rechnung 2026-014"></label>
        <button class="btn" type="submit">Code erzeugen</button>
    </form>

    <?php if ($codes): ?>
    <table>
        <tr><th>Code</th><th>Erstellt</th><th>Notiz</th><th>Eingelöst</th><th>Von Gerät</th></tr>
        <?php foreach ($codes as $c): ?>
        <tr>
            <td class="mono"><?= e($c['code']) ?></td>
            <td class="dim"><?= e(date('d.m.Y', strtotime($c['created_at']))) ?></td>
            <td class="dim"><?= e($c['note']) ?></td>
            <td><?= $c['used_at']
                    ? '<span class="tag active">' . e(date('d.m.Y', strtotime($c['used_at']))) . '</span>'
                    : '<span class="tag trial">offen</span>' ?></td>
            <td class="mono dim"><?= e($c['device_code'] ?? '') ?></td>
        </tr>
        <?php endforeach; ?>
    </table>
    <?php endif; ?>
</div></body></html>
