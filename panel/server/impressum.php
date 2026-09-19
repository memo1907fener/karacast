<?php
/**
 * Impressum nach § 5 ECG / § 25 MedienG (Österreich).
 *
 * Die Angaben stammen aus der App (AboutScreen.kt) und sind damit deine eigenen.
 * Was hier noch fehlt und nur du wissen kannst, steht als Platzhalter in eckigen
 * Klammern — bitte ausfüllen oder die Zeile löschen, wenn sie auf dich nicht
 * zutrifft. Ein Impressum mit erfundenen Angaben ist schlimmer als keines.
 */
require __DIR__ . '/lib/db.php';
require __DIR__ . '/lib/util.php';
require __DIR__ . '/lib/lang.php';
require __DIR__ . '/lib/site.php';

$config = config();
site_head(t('legal.title'));
?>
<section><div class="wrap" style="max-width:760px">
<article class="legal">
    <h1 style="font-size:clamp(26px,4vw,40px)"><?= e(t('legal.title')) ?></h1>
    <div class="draft"><?= e(t('legal.draft')) ?></div>

    <h2>Diensteanbieter</h2>
    <p>
        sternweb IT-Dienstleistungen<br>
        Augasse 82<br>
        8053 Graz<br>
        Österreich
    </p>

    <h2>Kontakt</h2>
    <p>
        E-Mail: <a href="mailto:<?= e($config['support_mail']) ?>"><?= e($config['support_mail']) ?></a><br>
        Telefon: <?= e($config['support_phone']) ?><br>
        Erreichbarkeit: Mo–Fr 08:00–17:00 Uhr
    </p>

    <h2>Unternehmensgegenstand</h2>
    <p>IT-Dienstleistungen, Entwicklung und Vertrieb von Software.</p>

    <h2>Weitere Angaben</h2>
    <p class="dim">
        UID-Nummer: [ATU… — falls vorhanden, sonst diese Zeile löschen]<br>
        Firmenbuchnummer / Firmenbuchgericht: [falls eingetragen, sonst löschen]<br>
        Gewerbeaufsichtsbehörde: [zuständige Bezirksverwaltungsbehörde]<br>
        Mitgliedschaft: [WKO-Fachgruppe, falls zutreffend]
    </p>

    <h2>Online-Streitbeilegung</h2>
    <p>
        Die Europäische Kommission stellt eine Plattform zur Online-Streitbeilegung bereit:
        <a href="https://ec.europa.eu/consumers/odr" rel="noopener" target="_blank">ec.europa.eu/consumers/odr</a>.
        Wir sind weder verpflichtet noch bereit, an einem Streitbeilegungsverfahren vor einer
        Verbraucherschlichtungsstelle teilzunehmen.
    </p>

    <h2>Haftung für Inhalte Dritter</h2>
    <p>
        Karacast ist ein Abspielprogramm. Es enthält und vermittelt keine Fernsehsender,
        keine Filme und keine Zugänge zu solchen. Welche Quelle in der Software eingetragen
        wird, entscheidet allein die Nutzerin oder der Nutzer; für deren Rechtmäßigkeit ist
        ebenfalls sie oder er verantwortlich.
    </p>
</article>
</div></section>
<?php site_foot(); ?>
