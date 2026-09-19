<?php
/**
 * Deutsch — die Vorlage.
 *
 * Diese Datei ist die vollständige: fehlt ein Baustein in en.php oder tr.php,
 * springt automatisch der deutsche ein. Neue Texte kommen also immer zuerst hier
 * hinein, und %s / %d bleiben in jeder Sprache in derselben Reihenfolge stehen.
 */
return [

    // ---------------------------------------------------------------- Allgemein
    'site.tagline'      => 'Fernsehen, wie du es eingerichtet hast',
    'nav.features'      => 'Funktionen',
    'nav.how'           => 'So geht es',
    'nav.price'         => 'Preis',
    'nav.activate'      => 'Freischalten',
    'nav.status'        => 'Gerät prüfen',
    'footer.legal'      => 'Impressum',
    'footer.terms'      => 'AGB',
    'footer.privacy'    => 'Datenschutz',
    'footer.note'       => 'Karacast ist ein Abspielprogramm. Es enthält keine Sender, keine Filme und keine Zugänge — abgespielt wird ausschließlich, was du selbst einträgst.',

    // -------------------------------------------------------------------- Hero
    'hero.title'        => 'Dein Fernseher. Deine Liste. Kein Abo.',
    'hero.body'         => 'Karacast ist ein Abspielprogramm für Android TV und Google TV. Du trägst deinen eigenen Zugang ein, Karacast macht daraus ein Fernsehprogramm mit Senderliste, Programmführer, Filmen und Serien — bedienbar mit der Fernbedienung, auch von jemandem, der keine Lust auf Technik hat.',
    'hero.cta'          => 'Gerät freischalten',
    'hero.secondary'    => 'Status prüfen',
    'hero.trial'        => '14 Tage kostenlos testen. Danach einmalig %s — kein Abo, keine monatlichen Kosten.',

    // --------------------------------------------------------------- Funktionen
    'features.title'    => 'Was es kann',

    'f.live.title'      => 'Live-Fernsehen mit Programmführer',
    'f.live.body'       => 'Senderliste nach Gruppen, Kanalnummern, Favoriten und ein Programmführer, der zeigt, was gerade läuft und was danach kommt.',

    'f.vod.title'       => 'Filme und Serien',
    'f.vod.body'        => 'Plakatansicht, Suche, Favoriten und „nächste Folge" — eine Serie läuft weiter, ohne dass jemand zur Fernbedienung greift.',

    'f.parental.title'  => 'Kindersicherung',
    'f.parental.body'   => 'Ganze Gruppen hinter einer PIN. Was gesperrt ist, taucht in der Liste gar nicht erst auf.',

    'f.sound.title'     => 'Ton, der auch dann kommt',
    'f.sound.body'      => 'Viele Sender senden Dolby-Ton, den billige Geräte nicht entschlüsseln dürfen. Karacast bringt die Entschlüsselung selbst mit — der Sender bleibt hörbar.',

    'f.simple.title'    => 'Für alle bedienbar',
    'f.simple.body'     => 'Große Schrift, klare Wege, alles mit dem Steuerkreuz erreichbar. Deutsch, Englisch, Türkisch und mehr.',

    'f.updates.title'   => 'Hält sich selbst aktuell',
    'f.updates.body'    => 'Die App sagt Bescheid, wenn es eine neue Fassung gibt, und installiert sie auf Wunsch selbst. Entschieden wird am Gerät.',

    // ---------------------------------------------------------------- So geht es
    'how.title'         => 'In drei Schritten',
    'how.1.title'       => 'App installieren',
    'how.1.body'        => 'Karacast auf den Fernseher oder die TV-Box bringen. Wenn du die Datei von uns hast, geht das in zwei Minuten.',
    'how.2.title'       => 'Eigenen Zugang eintragen',
    'how.2.body'        => 'Deine Liste oder deine Zugangsdaten einmal eingeben. Karacast lädt Sender, Filme und den Programmführer und merkt sich alles.',
    'how.3.title'       => 'Testen, dann freischalten',
    'how.3.body'        => 'Vierzehn Tage vollständig kostenlos. Danach zeigt der Fernseher einen Code — den scannst du mit dem Handy und schaltest das Gerät dauerhaft frei.',

    // --------------------------------------------------------------------- Preis
    'price.title'       => 'Einmal zahlen, fertig',
    'price.amount'      => '%s',
    'price.once'        => 'einmalig, pro Gerät',
    'price.p1'          => 'Kein Abo und keine Verlängerung.',
    'price.p2'          => 'Gilt dauerhaft für dieses Gerät, auch für spätere Versionen.',
    'price.p3'          => 'Funktioniert danach auch ohne Verbindung zu uns — die Freischaltung liegt auf dem Gerät selbst, nicht auf unserem Server.',
    'price.p4'          => 'Neuer Fernseher? Die Lizenz lässt sich auf Anfrage übertragen.',
    'price.cta'         => 'Jetzt freischalten',

    // -------------------------------------------------------------------- Status
    'status.title'      => 'Gerät prüfen',
    'status.intro'      => 'Gib die Gerätekennung ein, dann siehst du, ob dieses Gerät freigeschaltet ist. Du findest sie am Fernseher unter <em>Einstellungen → Lizenz</em>.',
    'status.label'      => 'Gerätekennung',
    'status.submit'     => 'Prüfen',
    'status.invalid'    => 'Eine Gerätekennung besteht aus zwölf Zeichen (0–9 und A–F). Doppelpunkte darfst du mitschreiben.',
    'status.unknown'    => 'Dieses Gerät hat sich bei uns noch nie gemeldet. Prüf die Kennung, und ob der Fernseher mit dem Internet verbunden ist.',
    'status.active'     => 'Dieses Gerät ist dauerhaft freigeschaltet.',
    'status.active.hint'=> 'Falls am Fernseher noch etwas anderes steht: einmal <em>Jetzt prüfen</em> drücken.',
    'status.trial'      => 'Testphase — noch %d Tage.',
    'status.trial.over' => 'Die Testphase ist abgelaufen.',
    'status.blocked'    => 'Dieses Gerät ist gesperrt. Melde dich bitte bei uns, dann klären wir das.',
    'status.toobusy'    => 'Zu viele Abfragen von dieser Verbindung. Versuch es in ein paar Minuten noch einmal.',

    // ---------------------------------------------------------------- Aktivieren
    'act.title'         => 'Karacast freischalten',
    'act.device'        => 'Dein Gerät',
    'act.already'       => 'Dieses Gerät ist bereits freigeschaltet.',
    'act.already.hint'  => 'Am Fernseher einmal <em>Jetzt prüfen</em> drücken, dann verschwindet der Aktivierungsbildschirm.',
    'act.oneoff'        => 'Einmalige Freischaltung',
    'act.noabo'         => 'Kein Abo. Keine monatlichen Kosten. Gilt für dieses Gerät.',
    'act.how.title'     => 'So bekommst du die Freischaltung',
    'act.how.body'      => 'Schreib uns kurz mit deiner Gerätekennung — du bekommst die Zahlungsdaten, und danach schaltet sich dein Fernseher von selbst frei.',
    'act.mail'          => 'E-Mail schreiben',
    'act.phone'         => 'oder telefonisch: %s',
    'act.nocode.title'  => 'Diese Seite ruft man vom Fernseher aus auf',
    'act.nocode.body'   => 'Der Fernseher zeigt dafür einen QR-Code, den du mit dem Handy abscannst. Am Gerät: <em>Einstellungen → Lizenz</em>, dort steht die Gerätekennung.',

    // -------------------------------------------------------------------- Bezahlen
    'pay.button'        => 'Jetzt kaufen — %s',
    'pay.secure'        => 'Bezahlung über Stripe. Karten, Apple Pay und Google Pay. Deine Kartendaten sehen wir nie.',
    'pay.consent'       => 'Ich stimme zu, dass die Freischaltung sofort nach der Zahlung erfolgt, und weiß, dass mein Widerrufsrecht damit erlischt. Die %s habe ich gelesen.',
    'pay.consent.link'  => 'AGB',
    'pay.consent.missing' => 'Ohne diese Zustimmung dürfen wir nicht sofort freischalten. Bitte setz den Haken.',
    'pay.off'           => 'Die Bezahlung über die Website ist gerade nicht eingerichtet. Schreib uns kurz, dann geht es von Hand.',
    'pay.failed'        => 'Die Bezahlung ließ sich nicht starten. Versuch es bitte noch einmal oder schreib uns.',
    'pay.or'            => 'oder',
    'pay.thanks.title'  => 'Danke.',
    'pay.thanks.done'   => 'Bezahlt und freigeschaltet.',
    'pay.thanks.tv'     => 'Dein Fernseher merkt es von selbst. Steht dort noch der Aktivierungsbildschirm, drück einmal <em>Jetzt prüfen</em>.',
    'pay.thanks.wait'   => 'Zahlung wird bestätigt …',
    'pay.thanks.wait.body' => 'Das dauert selten länger als ein paar Sekunden. Du kannst diese Seite neu laden — und dein Fernseher fragt ohnehin von allein nach.',
    'pay.receipt'       => 'Die Rechnung kommt per E-Mail von Stripe.',

    // ------------------------------------------------------------------ Rechtlich
    'legal.title'       => 'Impressum',
    'terms.title'       => 'Allgemeine Geschäftsbedingungen',
    'privacy.title'     => 'Datenschutzerklärung',
    'legal.draft'       => 'Entwurf — vor dem Verkaufsstart von einer Anwältin oder einem Anwalt prüfen lassen.',
];
