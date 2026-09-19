<?php
require __DIR__ . '/../lib/db.php';
require __DIR__ . '/../lib/util.php';
require __DIR__ . '/../lib/auth.php';
session_start_safe();

$error = null;
$ip = $_SERVER['REMOTE_ADDR'] ?? '?';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    // Auch das Anmeldeformular: ohne diese Prüfung könnte eine fremde Seite den
    // Browser des Besitzers still anmelden lassen und die Sitzung danach benutzen.
    csrf_check();
    if (login_blocked($ip)) {
        $error = 'Zu viele Versuche. In fünfzehn Minuten wieder probieren.';
    } else {
        login_delay($ip);
        $hash = admin_password_hash();
        if ($hash && password_verify($_POST['password'] ?? '', $hash)) {
            session_regenerate_id(true);
            $_SESSION['admin'] = true;
            clear_logins($ip);
            header('Location: index.php');
            exit;
        }
        note_failed_login($ip);
        $error = 'Falsches Passwort.';
    }
}
?>
<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Karacast Panel</title><?php require __DIR__ . '/style.php'; ?></head><body>
<div class="wrap narrow">
    <h1>Karacast Panel</h1>
    <?php if ($error): ?><p class="err"><?= e($error) ?></p><?php endif; ?>
    <form method="post" class="card">
        <input type="hidden" name="csrf" value="<?= e(csrf_token()) ?>">
        <label>Passwort<input type="password" name="password" autocomplete="current-password" autofocus required></label>
        <button class="btn" type="submit">Anmelden</button>
    </form>
</div></body></html>
