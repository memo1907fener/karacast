<?php
/**
 * Datenschutzerklärung — Entwurf.
 *
 * Sie beschreibt genau das, was Panel und App wirklich tun; ich habe nichts
 * hineingeschrieben, was der Code nicht macht, und nichts weggelassen, was er macht.
 * Wenn sich das ändert — Stripe, Playlist-Ablage —, ändert sich diese Seite mit,
 * sonst stimmt sie nicht mehr.
 */
require __DIR__ . '/lib/db.php';
require __DIR__ . '/lib/util.php';
require __DIR__ . '/lib/lang.php';
require __DIR__ . '/lib/site.php';

$config = config();
site_head(t('privacy.title'));
?>
<section><div class="wrap" style="max-width:760px">
<article class="legal">
    <h1 style="font-size:clamp(26px,4vw,40px)"><?= e(t('privacy.title')) ?></h1>
    <div class="draft"><?= e(t('legal.draft')) ?></div>

    <h2>Verantwortlich</h2>
    <p>
        sternweb IT-Dienstleistungen, Augasse 82, 8053 Graz, Österreich.<br>
        <a href="mailto:<?= e($config['support_mail']) ?>"><?= e($config['support_mail']) ?></a>
    </p>

    <h2>Was die App übermittelt</h2>
    <p>
        Zur Verwaltung der Lizenz meldet sich die App in großen Abständen bei diesem Server.
        Übermittelt werden dabei:
    </p>
    <ul>
        <li>
            <strong>Gerätekennungen in gekürzter, gehashter Form.</strong> Aus Merkmalen des
            Geräts — Netzwerkadressen, Android-Kennung, einer beim ersten Start erzeugten
            Zufallskennung — wird jeweils ein Hash gebildet und davon werden sechs Byte
            übertragen. Die ursprünglichen Merkmale verlassen das Gerät nicht und lassen
            sich aus dem Hash nicht zurückrechnen.
        </li>
        <li><strong>Gerätemodell und App-Version</strong>, um bei Störungen helfen zu können.</li>
        <li><strong>Die eingestellte Sprache.</strong></li>
        <li>Technisch unvermeidlich: die <strong>IP-Adresse</strong> der Verbindung.</li>
    </ul>
    <p>
        <strong>Nicht übermittelt werden:</strong> die Zugangsdaten oder Adressen, die in der
        App eingetragen sind, die Senderliste, das Sehverhalten, Favoriten oder irgendein
        anderer Inhalt. Diese Daten bleiben ausschließlich auf dem Gerät.
    </p>

    <h2>Was gespeichert wird</h2>
    <p>
        Zu jedem Gerät werden gespeichert: die genannten Kennungen, Modell und Version,
        Zeitpunkt der ersten und der letzten Meldung, der Lizenzstatus und — sofern
        freigeschaltet — ein Vermerk zur Zahlung sowie eine freiwillige Notiz zur Zuordnung
        (etwa „Wohnzimmer"). Rechtsgrundlage ist Art. 6 Abs. 1 lit. b DSGVO: ohne diese Daten
        lässt sich eine gerätegebundene Lizenz weder erteilen noch prüfen.
    </p>

    <h2>Server-Protokolle</h2>
    <p>
        Der Webserver protokolliert Aufrufe mit IP-Adresse, Zeitpunkt und aufgerufener
        Adresse. Diese Protokolle dienen dem Betrieb und der Abwehr von Angriffen
        (Art. 6 Abs. 1 lit. f DSGVO) und werden kurzfristig gelöscht. Fehlversuche bei der
        Anmeldung am Verwaltungsbereich und Häufungen von Statusabfragen werden bis zu
        24 Stunden festgehalten, um automatisiertes Durchprobieren zu bremsen.
    </p>

    <h2>Cookies</h2>
    <p>
        Diese Website setzt ein einziges Cookie, und zwar nur, wenn eine Sprache ausgewählt
        wird: darin steht das Sprachkürzel. Kein Zählwerk, keine Reichweitenmessung, keine
        Werbenetzwerke, keine Dienste Dritter, die beim Aufruf mitgeladen würden.
        Der Verwaltungsbereich verwendet zusätzlich ein Sitzungscookie für die Anmeldung.
    </p>

    <h2>Speicherdauer</h2>
    <p>
        Gerätedaten werden gespeichert, solange die Lizenz besteht — sie ist unbefristet, also
        dauerhaft; die Lizenz ließe sich sonst nicht mehr nachweisen. Auf Wunsch wird ein
        Gerät samt zugehöriger Daten gelöscht; die auf dem Gerät gespeicherte Freischaltung
        bleibt davon unberührt und funktioniert weiter.
    </p>

    <h2>Bezahlung</h2>
    <p>
        Die Bezahlung wickelt Stripe Payments Europe, Ltd. ab. Der Bezahlvorgang findet
        vollständig auf deren Seiten statt: <strong>Kartendaten erreichen diesen Server
        nie</strong> und werden hier weder verarbeitet noch gespeichert. An Stripe
        übermittelt werden der Betrag, die Gerätekennung als Verwendungszweck und die
        Sprache; alles Weitere — Name, E-Mail-Adresse, Zahlungsmittel — gibst du dort direkt
        ein. Rechtsgrundlage ist Art. 6 Abs. 1 lit. b DSGVO.
    </p>
    <p>
        Von Stripe zurück erhalten und gespeichert werden: die Kennung des Bezahlvorgangs,
        der Betrag, die Währung und die E-Mail-Adresse, die beim Bezahlen angegeben wurde —
        Letztere, um im Streitfall oder bei einer Erstattung überhaupt antworten zu können.
        Diese Angaben unterliegen der siebenjährigen abgabenrechtlichen Aufbewahrungspflicht.
        Stripes eigene Datenschutzerklärung:
        <a href="https://stripe.com/privacy" rel="noopener" target="_blank">stripe.com/privacy</a>.
    </p>
    <p>
        Vor dem Kauf wird die Zustimmung zum sofortigen Beginn der Leistung abgefragt. Als
        Nachweis werden Zeitpunkt, Gerätekennung und IP-Adresse dieser Zustimmung gespeichert
        (Art. 6 Abs. 1 lit. c DSGVO — ohne diesen Nachweis lässt sich das Erlöschen des
        Widerrufsrechts nicht belegen).
    </p>

    <h2>Empfänger</h2>
    <p>
        Außer dem oben genannten Zahlungsdienstleister findet keine Weitergabe an Dritte
        statt. Der Server wird bei einem Hoster in der Europäischen Union betrieben, der
        insoweit als Auftragsverarbeiter tätig ist.
    </p>

    <h2>Deine Rechte</h2>
    <p>
        Auskunft, Berichtigung, Löschung, Einschränkung, Datenübertragbarkeit und Widerspruch
        — eine Nachricht an die oben genannte Adresse genügt. Für eine Auskunft zu einem
        bestimmten Gerät gib bitte dessen Gerätekennung an; sie ist das Einzige, worüber sich
        ein Eintrag zuordnen lässt. Außerdem besteht ein Beschwerderecht bei der
        Österreichischen Datenschutzbehörde.
    </p>

    <p class="dim">Stand: <?= date('m/Y') ?></p>
</article>
</div></section>
<?php site_foot(); ?>
