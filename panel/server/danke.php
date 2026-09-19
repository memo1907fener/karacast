<?php
/**
 * Nach der Zahlung.
 *
 * Diese Seite glaubt dem Browser nichts. Sie nimmt die Sitzungskennung, fragt damit
 * bei Stripe nach, und nur wenn Stripe „paid" sagt, wird freigeschaltet. Jemand, der
 * die Adresse mit erfundener Kennung aufruft, bekommt genau nichts.
 *
 * Warum überhaupt, wo doch der Webhook kommt: der Webhook braucht manchmal ein paar
 * Sekunden, und in diesen Sekunden steht jemand vor seinem Fernseher und wartet.
 * Beide Wege enden in derselben Funktion, und die zweite Ausführung tut nichts mehr.
 */
require __DIR__ . '/lib/db.php';
require __DIR__ . '/lib/util.php';
require __DIR__ . '/lib/license.php';
require __DIR__ . '/lib/stripe.php';
require __DIR__ . '/lib/lang.php';
require __DIR__ . '/lib/site.php';

$sessionId = is_string($_GET['session_id'] ?? null) ? $_GET['session_id'] : '';
$given     = is_string($_GET['d'] ?? null) ? $_GET['d'] : '';
$code      = strtoupper(substr(preg_replace('/[^0-9A-Fa-f]/', '', $given) ?? '', 0, 12));

$state = 'pending';

if ($sessionId !== '' && preg_match('/^cs_[A-Za-z0-9_]+$/', $sessionId) && stripe_enabled()) {
    try {
        $session = stripe_get_session($sessionId);
        $result  = stripe_fulfil($session);
        $state   = in_array($result, ['activated', 'repaired', 'already'], true) ? 'done' : 'pending';
        if ($code === '') {
            $code = (string) ($session['metadata']['device_code'] ?? '');
        }
    } catch (Throwable $e) {
        error_log('[karacast] Dankeseite: ' . $e->getMessage());
        // Kein Grund zur Panik und erst recht keiner für eine Fehlermeldung: der
        // Webhook erledigt es gleich, die Seite sagt nur „einen Moment noch".
        $state = 'pending';
    }
}

// Falls die Sitzung gar nicht mitkam: wenigstens nachsehen, wie das Gerät dasteht.
if ($state !== 'done' && strlen($code) === 12) {
    $device = device_by_code($code);
    if ($device && $device['status'] === 'active') $state = 'done';
}

site_head(t('pay.thanks.title'));
?>
<section><div class="wrap" style="max-width:640px">
    <h1 style="font-size:clamp(26px,4vw,40px)"><?= e(t('pay.thanks.title')) ?></h1>

    <div class="card">
        <?php if ($state === 'done'): ?>
            <p><span class="tag active"><?= e(t('pay.thanks.done')) ?></span></p>
            <p class="dim"><?= t('pay.thanks.tv') ?></p>
        <?php else: ?>
            <p><span class="tag trial"><?= e(t('pay.thanks.wait')) ?></span></p>
            <p class="dim"><?= e(t('pay.thanks.wait.body')) ?></p>
        <?php endif; ?>

        <?php if (strlen($code) === 12): ?>
            <p class="mono dim" style="margin:16px 0 0; font-size:15px"><?= e(implode(':', str_split($code, 2))) ?></p>
            <p style="margin:14px 0 0">
                <a class="btn ghost" href="status.php?d=<?= e($code) ?>"><?= e(t('nav.status')) ?></a>
            </p>
        <?php endif; ?>
    </div>

    <p class="dim" style="margin-top:18px"><?= e(t('pay.receipt')) ?></p>
</div></section>
<?php site_foot(); ?>
