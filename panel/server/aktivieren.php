<?php
/**
 * Die Seite, auf der der QR-Code vom Fernseher landet.
 *
 * In dieser Fassung ohne Zahlungsanbieter: sie zeigt die Kennung, den Preis und
 * wie man sich meldet. Sobald Stripe dazukommt, wird aus dem Kontaktblock ein
 * Bezahlknopf — die Kennung steht dann schon an der richtigen Stelle und alles
 * darum herum bleibt, wie es ist.
 *
 * Hier wird Software verkauft: ein Abspielprogramm. Keine Sender, keine Anbieter,
 * keine Logos. Das ist nicht Kosmetik — Zahlungsanbieter sperren Konten, wenn ihre
 * Prüfer den Eindruck gewinnen, es gehe um Zugang zu fremden Inhalten.
 */
require __DIR__ . '/lib/db.php';
require __DIR__ . '/lib/util.php';
require __DIR__ . '/lib/license.php';
require __DIR__ . '/lib/stripe.php';
require __DIR__ . '/lib/lang.php';
require __DIR__ . '/lib/site.php';

// Kommt jemand von checkout.php zurück, weil der Haken fehlte, steht der Grund
// in der Adresse — sonst stünde der Käufer vor derselben Seite ohne Erklärung.
$error = ($_GET['e'] ?? '') === 'consent' ? t('pay.consent.missing') : null;

$given = $_GET['d'] ?? '';
$code = strtoupper(preg_replace('/[^0-9A-Fa-f]/', '', is_string($given) ? $given : '') ?? '');
$code = substr($code, 0, 12);

// Über dieselbe Suche wie das Panel: erst die Kennungen, dann der angezeigte
// Code. Nach einem Zusammenführen zweier Zeilen trägt der Fernseher unter
// Umständen noch den alten Code im QR-Code, und die Seite muss ihn trotzdem
// wiedererkennen.
$device = strlen($code) === 12 ? device_by_code($code) : null;

$config = config();
$pretty = $code ? implode(':', str_split($code, 2)) : '';

site_head(t('act.title'));
?>

<section>
<div class="wrap" style="max-width:720px">
    <h1 style="font-size:clamp(26px,4vw,40px)"><?= e(t('act.title')) ?></h1>

    <?php if ($device && $device['status'] === 'active'): ?>
        <div class="card">
            <p><span class="tag active"><?= e(t('act.already')) ?></span></p>
            <p class="dim" style="margin:0"><?= t('act.already.hint') ?></p>
        </div>

    <?php elseif ($pretty): ?>
        <div class="card">
            <p class="dim"><?= e(t('act.device')) ?></p>
            <p class="mono" style="font-size:26px; margin:0"><?= e($pretty) ?></p>
            <p class="dim" style="margin:18px 0 0"><?= e(t('act.oneoff')) ?></p>
            <p style="font-size:26px; font-weight:700; margin:0"><?= e($config['price']) ?></p>
            <p class="dim" style="margin:6px 0 0"><?= e(t('act.noabo')) ?></p>
        </div>

        <?php if (stripe_enabled()): ?>
        <div class="card" style="margin-top:18px">
            <form method="post" action="checkout.php">
                <input type="hidden" name="d" value="<?= e($code) ?>">
                <input type="hidden" name="lang" value="<?= e(current_lang()) ?>">

                <?php if ($error): ?><p class="err"><?= e($error) ?></p><?php endif; ?>

                <label class="check">
                    <input type="checkbox" name="consent" value="1" required>
                    <span><?= t('pay.consent', '<a href="agb.php" target="_blank">' . e(t('pay.consent.link')) . '</a>') ?></span>
                </label>

                <button class="btn" type="submit"><?= e(t('pay.button', $config['price'])) ?></button>
            </form>
            <p class="dim" style="margin:14px 0 0; font-size:14px"><?= e(t('pay.secure')) ?></p>
        </div>

        <p class="dim" style="text-align:center; margin:18px 0"><?= e(t('pay.or')) ?></p>
        <?php endif; ?>

        <div class="card" style="margin-top:18px">
            <h3><?= e(t('act.how.title')) ?></h3>
            <p class="dim"><?= e(t('act.how.body')) ?></p>
            <p>
                <a class="btn ghost" href="mailto:<?= e($config['support_mail']) ?>?subject=Karacast%20<?= e($pretty) ?>&amp;body=<?= rawurlencode(t('act.device') . ': ' . $pretty) ?>">
                    <?= e(t('act.mail')) ?>
                </a>
            </p>
            <p class="dim" style="margin:0"><?= e(t('act.phone', $config['support_phone'])) ?></p>
        </div>

    <?php else: ?>
        <div class="card">
            <h3><?= e(t('act.nocode.title')) ?></h3>
            <p class="dim" style="margin:0"><?= t('act.nocode.body') ?></p>
            <p style="margin:16px 0 0"><a class="btn ghost" href="status.php"><?= e(t('nav.status')) ?></a></p>
        </div>
    <?php endif; ?>
</div>
</section>

<?php site_foot(); ?>
