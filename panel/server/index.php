<?php
/**
 * Die Startseite.
 *
 * Verkauft ausdrücklich **Software** — ein Abspielprogramm. Keine Sender, keine
 * Anbieternamen, keine Logos, keine Versprechen über Inhalte. Das ist nicht
 * Zimperlichkeit: Zahlungsanbieter prüfen genau das, und eine Seite, die auch nur
 * nach Zugang zu fremden Inhalten aussieht, kostet das Zahlungskonto.
 *
 * Und es steht hier nichts, was die App nicht kann. Eine Startseite, die mehr
 * verspricht als das Programm hält, erzeugt Rückbuchungen, keine Kundschaft.
 */
require __DIR__ . '/lib/db.php';
require __DIR__ . '/lib/util.php';
require __DIR__ . '/lib/lang.php';
require __DIR__ . '/lib/site.php';

$config = config();
$price  = (string) ($config['price'] ?? '9,99 €');

site_head(t('site.tagline'));
?>

<section style="border-bottom:0; padding-bottom:36px">
    <div class="wrap">
        <h1><?= e(t('hero.title')) ?></h1>
        <p class="lead"><?= e(t('hero.body')) ?></p>
        <div class="row" style="margin:28px 0 16px">
            <a class="btn" href="aktivieren.php"><?= e(t('hero.cta')) ?></a>
            <a class="btn ghost" href="status.php"><?= e(t('hero.secondary')) ?></a>
        </div>
        <p class="dim"><?= e(t('hero.trial', $price)) ?></p>
    </div>
</section>

<section id="features">
    <div class="wrap">
        <h2><?= e(t('features.title')) ?></h2>
        <div class="grid" style="margin-top:26px">
            <?php foreach (['live', 'vod', 'parental', 'sound', 'simple', 'updates'] as $key): ?>
                <div class="card">
                    <h3><?= e(t("f.$key.title")) ?></h3>
                    <p><?= e(t("f.$key.body")) ?></p>
                </div>
            <?php endforeach; ?>
        </div>
    </div>
</section>

<section id="how">
    <div class="wrap">
        <h2><?= e(t('how.title')) ?></h2>
        <div class="grid" style="margin-top:26px">
            <?php foreach ([1, 2, 3] as $step): ?>
                <div class="card">
                    <span class="step-no"><?= $step ?></span>
                    <h3><?= e(t("how.$step.title")) ?></h3>
                    <p><?= e(t("how.$step.body")) ?></p>
                </div>
            <?php endforeach; ?>
        </div>
    </div>
</section>

<section id="price">
    <div class="wrap">
        <h2><?= e(t('price.title')) ?></h2>
        <div class="price" style="margin-top:22px">
            <div class="amount"><?= e($price) ?></div>
            <p class="dim" style="margin-top:6px"><?= e(t('price.once')) ?></p>
            <ul>
                <li><?= e(t('price.p1')) ?></li>
                <li><?= e(t('price.p2')) ?></li>
                <li><?= e(t('price.p3')) ?></li>
                <li><?= e(t('price.p4')) ?></li>
            </ul>
            <a class="btn" href="aktivieren.php"><?= e(t('price.cta')) ?></a>
        </div>
    </div>
</section>

<?php site_foot(); ?>
