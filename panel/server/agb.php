<?php
/**
 * AGB — Entwurf.
 *
 * Ich bin keine Anwältin und das hier ist keine Rechtsberatung. Der Text deckt ab,
 * was dein Geschäft tatsächlich ist, und er enthält die eine Klausel, ohne die du
 * bei digitalen Gütern jeden Widerruf hinnehmen musst: die ausdrückliche Zustimmung
 * zum sofortigen Beginn der Leistung. Vor dem Verkaufsstart gehört er auf den Tisch
 * von jemandem mit Zulassung — das kostet einmal ein paar hundert Euro und erspart
 * dir im Zweifel ein Vielfaches.
 */
require __DIR__ . '/lib/db.php';
require __DIR__ . '/lib/util.php';
require __DIR__ . '/lib/lang.php';
require __DIR__ . '/lib/site.php';

$config = config();
site_head(t('terms.title'));
?>
<section><div class="wrap" style="max-width:760px">
<article class="legal">
    <h1 style="font-size:clamp(26px,4vw,40px)"><?= e(t('terms.title')) ?></h1>
    <div class="draft"><?= e(t('legal.draft')) ?></div>

    <h2>1. Geltungsbereich und Vertragspartner</h2>
    <p>
        Diese Bedingungen gelten für alle Verträge über die Software „Karacast", die über
        diese Website geschlossen werden. Vertragspartner ist sternweb IT-Dienstleistungen,
        Augasse 82, 8053 Graz, Österreich.
    </p>

    <h2>2. Gegenstand des Vertrags</h2>
    <p>
        Gegenstand ist die zeitlich unbefristete Nutzung der Software auf <strong>einem</strong>
        Endgerät. Die Software ist ein Abspielprogramm; sie enthält keine Inhalte, keine
        Fernsehsender und keine Zugänge zu solchen und vermittelt diese auch nicht. Welche
        Quelle in der Software eingetragen wird und ob deren Nutzung zulässig ist, liegt
        allein im Verantwortungsbereich der Nutzerin oder des Nutzers.
    </p>

    <h2>3. Testphase</h2>
    <p>
        Die Software kann ab der ersten Inbetriebnahme vierzehn Tage kostenlos und
        vollständig genutzt werden. Nach Ablauf dieser Frist ist eine Freischaltung
        erforderlich; ohne sie bleibt die Software installiert, spielt aber nicht mehr ab.
        Es entsteht durch die Testphase keinerlei Zahlungspflicht.
    </p>

    <h2>4. Preis und Zahlung</h2>
    <p>
        Die Freischaltung kostet einmalig <?= e($config['price']) ?> pro Endgerät, inklusive
        allfälliger Umsatzsteuer. Es handelt sich nicht um ein Abonnement; es entstehen keine
        wiederkehrenden Kosten. Die Freischaltung erfolgt nach vollständigem Zahlungseingang.
    </p>

    <h2>5. Umfang der Lizenz</h2>
    <p>
        Die Lizenz gilt dauerhaft für das Endgerät, für das sie ausgestellt wurde,
        einschließlich künftiger Versionen der Software. Sie ist an dieses Gerät gebunden.
        Bei einem Gerätewechsel kann die Lizenz auf Anfrage auf ein anderes Gerät übertragen
        werden; die Lizenz des bisherigen Geräts erlischt damit. Eine Weitergabe an Dritte,
        eine Vervielfältigung oder ein Umgehen der Freischaltung sind nicht gestattet.
    </p>

    <h2>6. Widerrufsrecht und dessen Erlöschen</h2>
    <p>
        Verbraucherinnen und Verbrauchern steht grundsätzlich ein vierzehntägiges
        Rücktrittsrecht zu. Bei digitalen Inhalten, die nicht auf einem körperlichen
        Datenträger geliefert werden, erlischt dieses Recht vorzeitig, wenn die Ausführung
        mit ausdrücklicher Zustimmung und in Kenntnis des dadurch eintretenden
        Rechtsverlusts begonnen hat. Auf diese Zustimmung wird beim Kauf ausdrücklich
        hingewiesen und sie ist gesondert zu bestätigen.
    </p>
    <p>
        Da die Software vor dem Kauf vierzehn Tage vollständig getestet werden kann, ist der
        Leistungsumfang zum Zeitpunkt des Kaufs bereits bekannt.
    </p>

    <h2>7. Gewährleistung</h2>
    <p>
        Es gelten die gesetzlichen Gewährleistungsbestimmungen. Die Software wird laufend
        weiterentwickelt; ein Anspruch auf bestimmte Funktionen, auf eine bestimmte
        Verfügbarkeit von Diensten Dritter oder auf Abspielbarkeit einer konkreten Quelle
        besteht nicht. Fehler werden nach Maßgabe des Zumutbaren behoben.
    </p>

    <h2>8. Verfügbarkeit des Lizenzservers</h2>
    <p>
        Eine einmal erteilte Freischaltung wird auf dem Endgerät gespeichert und bleibt auch
        dann gültig, wenn dieser Server nicht erreichbar ist oder dauerhaft eingestellt wird.
        Eine ständige Internetverbindung ist für die Gültigkeit der Lizenz nicht erforderlich.
    </p>

    <h2>9. Sperre</h2>
    <p>
        Eine Lizenz kann gesperrt werden, wenn die Zahlung rückgängig gemacht wurde oder wenn
        die Software nachweislich manipuliert oder unberechtigt vervielfältigt wurde.
    </p>

    <h2>10. Anwendbares Recht und Gerichtsstand</h2>
    <p>
        Es gilt österreichisches Recht unter Ausschluss der Verweisungsnormen. Zwingende
        Verbraucherschutzbestimmungen des Staates, in dem die Verbraucherin oder der
        Verbraucher ihren oder seinen gewöhnlichen Aufenthalt hat, bleiben unberührt.
    </p>

    <p class="dim">Stand: <?= date('m/Y') ?></p>
</article>
</div></section>
<?php site_foot(); ?>
