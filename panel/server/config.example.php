<?php
/**
 * Die einzige Datei, die du von Hand ausfüllst.
 *
 * Kopiere sie zu  config.php  und trage deine Werte ein. config.php selbst gehört
 * in kein Backup, das jemand anderes sehen kann, und in kein Git-Repository.
 */
return [

    // Zugangsdaten der MySQL-Datenbank aus deinem Hosting-Panel.
    'db' => [
        'host' => 'localhost',
        'name' => 'karacast',
        'user' => '',
        'pass' => '',
    ],

    // Die Adresse, unter der dieses Panel erreichbar ist — ohne Schrägstrich am
    // Ende. Nur hier steht die Domain; alles andere im Panel leitet sich daraus ab.
    'base_url' => 'https://karacast.de',

    // Was auf der Aktivierungsseite steht.
    'price'        => '9,99 €',
    'support_mail' => 'office@sternweb.at',
    'support_phone'=> '+43 660 3743933',

    // Länge der kostenlosen Testphase. Muss zur App passen (dort TRIAL_LENGTH_MS).
    'trial_days' => 14,

    /*
     * Stripe. Beide Werte holst du dir selbst im Stripe-Dashboard und trägst sie
     * hier ein — sie gehören in keine E-Mail, in keinen Chat und in kein Git.
     *
     *   secret_key      Entwickler → API-Schlüssel → „Geheimer Schlüssel".
     *                   sk_test_… zum Ausprobieren, sk_live_… für echtes Geld.
     *   webhook_secret  Entwickler → Webhooks → deinen Endpunkt anlegen auf
     *                   https://karacast.de/webhook.php, dann steht dort
     *                   „Signing secret": whsec_…
     *
     * Solange secret_key leer ist, gibt es keinen Bezahlknopf und die Seiten zeigen
     * weiter den Kontaktweg. Du kannst also gefahrlos zuerst hochladen.
     *
     * amount_cents muss zu 'price' oben passen — das eine steht auf der Seite,
     * das andere wird abgebucht.
     */
    'stripe' => [
        'secret_key'     => '',
        'webhook_secret' => '',
        'currency'       => 'eur',
        'amount_cents'   => 999,
    ],

    // Wo der private Schlüssel liegt. Leer heißt: keys/private.pem neben dieser
    // Datei. Am sichersten ist ein Pfad oberhalb des Webverzeichnisses, wohin
    // kein Browser je kommt — z. B. '/home/dein-account/karacast-keys/private.pem'.
    'key_path' => '',
];
