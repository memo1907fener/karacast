<?php
/**
 * Der Knopfdruck „jetzt bezahlen" — mehr steht hier nicht.
 *
 * Diese Datei legt die Bezahlsitzung an und schickt den Käufer zu Stripe. Sie nimmt
 * keine Kartennummer entgegen, sieht keine, speichert keine: das gesamte Bezahlen
 * findet auf Stripes eigener Seite statt. Deshalb kommt dein Server mit
 * Kartendaten nie in Berührung, und deshalb ist diese Datei so kurz.
 */
require __DIR__ . '/lib/db.php';
require __DIR__ . '/lib/util.php';
require __DIR__ . '/lib/license.php';
require __DIR__ . '/lib/stripe.php';
require __DIR__ . '/lib/lang.php';
require __DIR__ . '/lib/site.php';

function bail(string $message): never {
    site_head(t('act.title'));
    echo '<section><div class="wrap" style="max-width:640px"><div class="card"><p class="err">'
       . e($message) . '</p><p><a class="btn ghost" href="index.php">Karacast</a></p></div></div></section>';
    site_foot();
    exit;
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Location: aktivieren.php');
    exit;
}

$given = is_string($_POST['d'] ?? null) ? $_POST['d'] : '';
$code  = strtoupper(substr(preg_replace('/[^0-9A-Fa-f]/', '', $given) ?? '', 0, 12));

if (strlen($code) !== 12) bail(t('status.invalid'));
if (!stripe_enabled())    bail(t('pay.off'));

$device = device_by_code($code);
if (!$device)                            bail(t('status.unknown'));
if ($device['status'] === 'active')      bail(t('act.already'));
if ($device['status'] === 'blocked')     bail(t('status.blocked'));

// Der Haken unter „ich stimme zu, dass sofort geliefert wird und mein Widerrufs-
// recht damit erlischt" ist keine Formalie: ohne ihn bleibt das vierzehntägige
// Rücktrittsrecht bei digitalen Gütern bestehen. Also wird er hier verlangt und
// mit Zeitpunkt und Adresse festgehalten, bevor überhaupt eine Sitzung entsteht.
if (($_POST['consent'] ?? '') !== '1') {
    header('Location: aktivieren.php?d=' . $code . '&lang=' . current_lang() . '&e=consent', true, 303);
    exit;
}

$ip = substr((string) ($_SERVER['REMOTE_ADDR'] ?? ''), 0, 45);

try {
    $session = stripe_create_session($device, current_lang(), $ip);
} catch (Throwable $e) {
    error_log('[karacast] Checkout: ' . $e->getMessage());
    bail(t('pay.failed'));
}

$url = (string) ($session['url'] ?? '');
if ($url === '') bail(t('pay.failed'));

// Die Zustimmung wird hier vermerkt, nicht erst nach der Zahlung: sie ist auch dann
// erteilt worden, wenn der Kauf hinterher abgebrochen wird.
db()->prepare(
    'INSERT IGNORE INTO consents (session_id, device_id, device_code, ip, at) VALUES (?, ?, ?, ?, NOW())'
)->execute([(string) ($session['id'] ?? ''), $device['id'], $device['code'], $ip]);

header('Location: ' . $url, true, 303);
exit;
