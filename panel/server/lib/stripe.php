<?php
/**
 * Stripe — ohne Bibliothek, ohne Composer.
 *
 * Die offizielle PHP-Bibliothek zieht ein halbes Dutzend Pakete nach und setzt
 * Composer auf dem Webspace voraus. Gebraucht werden hier aber genau drei Dinge:
 * eine Bezahlsitzung anlegen, eine abfragen, und eine Unterschrift prüfen. Das sind
 * zwei cURL-Aufrufe und ein HMAC — also steht es hier, sichtbar und nachvollziehbar,
 * statt in 12.000 Zeilen fremdem Code.
 *
 * ### Wer schaltet frei
 *
 * Nicht der Browser des Käufers. Der kann alles behaupten. Freigeschaltet wird nur
 * auf zwei Wegen, und beide fragen Stripe selbst:
 *
 *  1. Der **Webhook**: Stripe meldet die bezahlte Sitzung an unseren Server, mit
 *     einer Unterschrift, die nur jemand erzeugen kann, der das Webhook-Geheimnis
 *     kennt. Das ist der verlässliche Weg — er kommt auch dann, wenn der Käufer den
 *     Browser sofort zuklappt.
 *  2. Die **Dankeseite**: sie fragt mit der Sitzungskennung bei Stripe nach, ob
 *     wirklich bezahlt wurde. Das ist der schnelle Weg, damit der Fernseher nicht auf
 *     den Webhook warten muss.
 *
 * Beide landen in derselben Funktion, und die ist so gebaut, dass zehn Aufrufe
 * dasselbe bewirken wie einer.
 */

function stripe_config(): array {
    $stripe = config()['stripe'] ?? [];
    return is_array($stripe) ? $stripe : [];
}

/** Ist Stripe eingerichtet? Wenn nicht, zeigen die Seiten weiter den Kontaktweg. */
function stripe_enabled(): bool {
    return trim((string) (stripe_config()['secret_key'] ?? '')) !== '';
}

function stripe_amount_cents(): int {
    return max(50, (int) (stripe_config()['amount_cents'] ?? 999));
}

function stripe_currency(): string {
    return strtolower((string) (stripe_config()['currency'] ?? 'eur'));
}

/**
 * Ein Aufruf bei Stripe.
 *
 * @param string $method GET oder POST
 * @param string $path   z. B. 'checkout/sessions'
 * @param array  $params Formularfelder; verschachtelte Arrays werden zu a[b][c]
 * @param string $idempotencyKey Gleiche Kennung = Stripe führt den Aufruf nur einmal
 *   aus. Wichtig, wenn jemand den Bezahlknopf zweimal drückt.
 */
function stripe_api(string $method, string $path, array $params = [], string $idempotencyKey = ''): array {
    if (!function_exists('curl_init')) {
        throw new RuntimeException('Die PHP-Erweiterung cURL fehlt auf diesem Server.');
    }
    $secret = trim((string) (stripe_config()['secret_key'] ?? ''));
    if ($secret === '') throw new RuntimeException('Kein Stripe-Schlüssel in config.php.');

    $url  = 'https://api.stripe.com/v1/' . ltrim($path, '/');
    $body = http_build_query($params, '', '&', PHP_QUERY_RFC3986);

    $headers = [
        'Authorization: Bearer ' . $secret,
        'Content-Type: application/x-www-form-urlencoded',
        'Stripe-Version: 2024-06-20',
    ];
    if ($idempotencyKey !== '') $headers[] = 'Idempotency-Key: ' . $idempotencyKey;

    $ch = curl_init();
    curl_setopt_array($ch, [
        CURLOPT_URL            => $method === 'GET' && $body !== '' ? $url . '?' . $body : $url,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT        => 20,
        CURLOPT_HTTPHEADER     => $headers,
        // Nicht abschaltbar, und das ist der Punkt: über diese Verbindung geht der
        // Schlüssel, mit dem man in deinem Namen Geld bewegen kann.
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_SSL_VERIFYHOST => 2,
    ]);
    if ($method === 'POST') {
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, $body);
    }

    $raw    = curl_exec($ch);
    $status = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
    $error  = curl_error($ch);
    curl_close($ch);

    if ($raw === false) throw new RuntimeException('Stripe nicht erreichbar: ' . $error);

    $data = json_decode((string) $raw, true);
    if (!is_array($data)) throw new RuntimeException('Unverständliche Antwort von Stripe.');

    if ($status >= 400) {
        // Die Fehlermeldung von Stripe gehört ins Protokoll, nicht auf die Seite:
        // sie nennt unter Umständen Kontodetails.
        throw new RuntimeException('Stripe: ' . ($data['error']['message'] ?? 'Fehler ' . $status));
    }
    return $data;
}

/**
 * Die Adresse, auf die der Käufer nach dem Bezahlen zurückkommt.
 *
 * Genommen wird der Rechnername, unter dem der Käufer *gerade* hier ist — nicht
 * `base_url` aus der Konfiguration. Der Grund ist eine Stunde Fehlersuche wert:
 * steht in `base_url` aus Versehen eine andere Domain (etwa die alte, oder eine mit
 * www davor), dann bezahlt der Kunde, Stripe schickt ihn brav zurück — und zwar auf
 * eine Seite, die es dort nicht gibt. Die Zahlung ist erfolgt, die Freischaltung
 * bleibt aus, und niemand sieht warum. Der Rechnername der laufenden Anfrage kann
 * dagegen gar nicht falsch sein.
 *
 * `base_url` bleibt der Rückfall für Aufrufe ohne Browser (etwa von der Kommandozeile).
 */
function site_base(): string {
    $host = (string) ($_SERVER['HTTP_HOST'] ?? '');
    if ($host !== '' && preg_match('/^[A-Za-z0-9.\-]+(:\d+)?$/', $host)) {
        $https = (!empty($_SERVER['HTTPS']) && strtolower((string) $_SERVER['HTTPS']) !== 'off')
            || strtolower((string) ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '')) === 'https'
            || ($_SERVER['SERVER_PORT'] ?? '') === '443';
        $dir = rtrim(str_replace('\\', '/', dirname((string) ($_SERVER['SCRIPT_NAME'] ?? '/'))), '/');
        return ($https ? 'https://' : 'http://') . $host . ($dir === '.' ? '' : $dir);
    }
    return rtrim((string) (config()['base_url'] ?? ''), '/');
}

/**
 * Sorgt dafür, dass die Tabellen für Zahlungen da sind.
 *
 * Eigentlich legt sie install.php beziehungsweise upgrade.php an. Aber wenn hier
 * eine Zahlung ankommt und die Tabelle fehlt, ist der denkbar schlechteste Moment
 * für eine Fehlermeldung: der Kunde hat bezahlt. Also wird sie eben jetzt angelegt.
 * Kostet im Normalfall nichts — CREATE TABLE IF NOT EXISTS auf eine vorhandene
 * Tabelle ist eine Abfrage ohne Wirkung — und rettet den einen Fall, auf den es
 * ankommt.
 */
function stripe_ensure_tables(): void {
    static $done = false;
    if ($done) return;
    $done = true;

    // Ältere Installationen haben order_ref noch mit 64 Zeichen. Da passt eine
    // Stripe-Sitzungskennung nicht hinein, und MySQL kürzt im strengen Modus nicht,
    // sondern bricht ab — mitten im Freischalten einer bezahlten Lizenz. Geprüft
    // wird zuerst, damit nicht bei jedem Aufruf eine Tabelle umgebaut wird.
    try {
        $width = db()->query(
            "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'devices'
               AND COLUMN_NAME = 'order_ref'"
        )->fetchColumn();
        if ($width !== false && (int) $width < 191) {
            db()->exec("ALTER TABLE devices MODIFY order_ref VARCHAR(191) DEFAULT ''");
            error_log('[karacast] Spalte order_ref auf 191 Zeichen verbreitert.');
        }
    } catch (Throwable $e) {
        error_log('[karacast] order_ref konnte nicht verbreitert werden: ' . $e->getMessage());
    }

    db()->exec("CREATE TABLE IF NOT EXISTS payments (
        id             INT AUTO_INCREMENT PRIMARY KEY,
        provider       VARCHAR(16)  NOT NULL DEFAULT 'stripe',
        session_id     VARCHAR(255) NOT NULL,
        payment_intent VARCHAR(255) DEFAULT '',
        device_id      INT          NOT NULL,
        device_code    VARCHAR(16)  NOT NULL,
        amount_cents   INT          NOT NULL DEFAULT 0,
        currency       VARCHAR(8)   NOT NULL DEFAULT 'eur',
        email          VARCHAR(120) DEFAULT '',
        consent_ip     VARCHAR(45)  DEFAULT '',
        note           VARCHAR(120) DEFAULT '',
        created_at     DATETIME     NOT NULL,
        refunded_at    DATETIME     NULL,
        UNIQUE KEY (session_id), KEY (device_id), KEY (payment_intent)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    db()->exec("CREATE TABLE IF NOT EXISTS consents (
        id          INT AUTO_INCREMENT PRIMARY KEY,
        session_id  VARCHAR(255) NOT NULL,
        device_id   INT          NOT NULL,
        device_code VARCHAR(16)  NOT NULL,
        ip          VARCHAR(45)  DEFAULT '',
        at          DATETIME     NOT NULL,
        UNIQUE KEY (session_id), KEY (device_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    db()->exec("CREATE TABLE IF NOT EXISTS webhook_events (
        event_id VARCHAR(255) NOT NULL PRIMARY KEY,
        type     VARCHAR(60)  NOT NULL DEFAULT '',
        at       DATETIME     NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}

/**
 * Sucht bei Stripe nach einer bezahlten Sitzung für dieses Gerät.
 *
 * Der Rettungsanker, wenn der Webhook nicht ankam — falsche Adresse eingetragen,
 * Geheimnis vertippt, Seite beim Bezahlen zugeklappt. Gefragt wird Stripe, nicht
 * der Kunde, also ist die Auskunft so belastbar wie beim normalen Weg.
 */
function stripe_find_paid_session(string $code): ?array {
    $list = stripe_api('GET', 'checkout/sessions', ['limit' => 100]);
    foreach (($list['data'] ?? []) as $session) {
        if (!is_array($session)) continue;
        $belongs = ($session['client_reference_id'] ?? '') === $code
            || ($session['metadata']['device_code'] ?? '') === $code;
        if ($belongs && ($session['payment_status'] ?? '') === 'paid') {
            return $session;
        }
    }
    return null;
}

/**
 * Legt die Bezahlsitzung an und gibt die Adresse zurück, auf die der Käufer geht.
 *
 * Der Betrag wird hier gesetzt, nicht im Browser — sonst bestimmte der Käufer den
 * Preis. Die Gerätekennung reist in `client_reference_id` und in `metadata` mit,
 * damit die Zahlung später einem Gerät zugeordnet werden kann.
 */
function stripe_create_session(array $device, string $lang, string $consentIp): array {
    stripe_ensure_tables();
    $base = site_base();
    $code = (string) $device['code'];

    $params = [
        'mode'                 => 'payment',
        'client_reference_id'  => $code,
        'success_url'          => $base . '/danke.php?d=' . $code . '&session_id={CHECKOUT_SESSION_ID}',
        'cancel_url'           => $base . '/aktivieren.php?d=' . $code,
        'locale'               => in_array($lang, ['de', 'en', 'tr'], true) ? $lang : 'auto',
        'line_items' => [[
            'quantity'   => 1,
            'price_data' => [
                'currency'     => stripe_currency(),
                'unit_amount'  => stripe_amount_cents(),
                'product_data' => [
                    'name'        => 'Karacast — Lizenz für ein Gerät',
                    'description' => 'Einmalige, dauerhafte Freischaltung für Gerät '
                                   . implode(':', str_split($code, 2)),
                ],
            ],
        ]],
        'metadata' => [
            'device_code' => $code,
            'device_id'   => (string) $device['id'],
            'consent_ip'  => $consentIp,
        ],
        // Für die Rechnung und für den Fall, dass jemand Hilfe braucht.
        'payment_intent_data' => [
            'description' => 'Karacast Lizenz ' . $code,
            'metadata'    => ['device_code' => $code],
        ],
    ];

    // Zweimal auf den Knopf gedrückt darf nicht zweimal abbuchen. Der Schlüssel
    // gilt pro Gerät und Minute — schnell genug für einen Doppelklick, kurz genug
    // für einen ernst gemeinten zweiten Versuch nach einem Fehlschlag.
    $key = 'karacast-' . $code . '-' . floor(time() / 60);

    return stripe_api('POST', 'checkout/sessions', $params, $key);
}

/** Holt eine Sitzung zurück — die Wahrheit über die Zahlung steht bei Stripe, nicht bei uns. */
function stripe_get_session(string $id): array {
    return stripe_api('GET', 'checkout/sessions/' . urlencode($id));
}

/**
 * Prüft die Unterschrift unter einem Webhook.
 *
 * Ohne diese Prüfung wäre webhook.php ein Formular, in das jeder „bezahlt"
 * hineinschreiben könnte. Der Zeitstempel gehört mit in die Prüfung: sonst ließe
 * sich eine echte, alte Meldung beliebig oft wiederholen.
 *
 * @param string $payload der **rohe** Rumpf der Anfrage, unverändert
 */
function stripe_verify_signature(string $payload, string $header, string $secret, int $tolerance = 300): bool {
    if ($secret === '' || $header === '') return false;

    $timestamp = 0;
    $signatures = [];
    foreach (explode(',', $header) as $part) {
        $pair = explode('=', trim($part), 2);
        if (count($pair) !== 2) continue;
        if ($pair[0] === 't') $timestamp = (int) $pair[1];
        if ($pair[0] === 'v1') $signatures[] = $pair[1];
    }
    if ($timestamp <= 0 || !$signatures) return false;
    if (abs(time() - $timestamp) > $tolerance) return false;

    $expected = hash_hmac('sha256', $timestamp . '.' . $payload, $secret);
    foreach ($signatures as $given) {
        if (hash_equals($expected, $given)) return true;
    }
    return false;
}

/**
 * Eine bezahlte Sitzung in eine Freischaltung verwandeln.
 *
 * Diese Funktion ist der einzige Ort, an dem eine Zahlung zur Lizenz wird, und sie
 * ist mit Absicht misstrauisch:
 *
 *  - bezahlt sein muss sie („paid", nicht bloß „complete"),
 *  - der Betrag muss stimmen, sonst hätte jemand die Sitzung untergeschoben,
 *  - das Gerät muss es geben,
 *  - und dieselbe Sitzung darf nur einmal wirken. Webhook und Dankeseite kommen
 *    regelmäßig beide an; die zweite findet die Zahlung bereits verbucht und tut
 *    nichts mehr.
 *
 * @return string 'activated' | 'already' | 'unpaid' | 'unknown'
 */
function stripe_fulfil(array $session): string {
    $sessionId = (string) ($session['id'] ?? '');
    if ($sessionId === '') return 'unknown';

    stripe_ensure_tables();

    if (($session['payment_status'] ?? '') !== 'paid') return 'unpaid';

    if ((int) ($session['amount_total'] ?? 0) < stripe_amount_cents()) {
        error_log('[karacast] Sitzung ' . $sessionId . ' mit zu kleinem Betrag abgelehnt.');
        return 'unpaid';
    }

    $code = (string) ($session['metadata']['device_code'] ?? $session['client_reference_id'] ?? '');
    $device = $code !== '' ? device_by_code($code) : null;
    if (!$device) {
        error_log('[karacast] Zahlung ohne auffindbares Gerät: ' . $sessionId);
        return 'unknown';
    }

    // Verbuchen und Freischalten gehören zusammen — entweder beides oder keines.
    //
    // Ohne diese Klammer entstand der übelste Fehler dieses Projekts: das INSERT
    // gelang, das Freischalten scheiterte danach an einer zu schmalen Spalte, und
    // weil die Zahlung nun verbucht war, antwortete jeder weitere Versuch „schon
    // erledigt". Bezahlt, nicht freigeschaltet, und keine Selbstheilung. Eine
    // Transaktion macht daraus wieder einen einzigen Vorgang: geht irgendetwas
    // schief, ist auch die Zahlung nicht verbucht, und der nächste Anlauf — vom
    // Webhook, von der Dankeseite oder vom Knopf im Panel — beginnt von vorn.
    $pdo = db();
    $pdo->beginTransaction();

    $insert = $pdo->prepare(
        "INSERT IGNORE INTO payments
            (provider, session_id, payment_intent, device_id, device_code,
             amount_cents, currency, email, consent_ip, created_at)
         VALUES ('stripe', ?, ?, ?, ?, ?, ?, ?, ?, NOW())"
    );
    $insert->execute([
        $sessionId,
        // Erstattungen und Rückbuchungen melden nur die Zahlungsabsicht, nicht die
        // Sitzung. Ohne diese Spalte fände stripe_revoke() das Gerät nicht wieder.
        (string) ($session['payment_intent'] ?? ''),
        $device['id'],
        $device['code'],
        (int) ($session['amount_total'] ?? 0),
        strtolower((string) ($session['currency'] ?? stripe_currency())),
        clean_text($session['customer_details']['email'] ?? '', 120),
        clean_text($session['metadata']['consent_ip'] ?? '', 45),
    ]);

    $fresh    = $insert->rowCount() > 0;
    $repaired = false;

    try {
        // Auch bei einer bereits verbuchten Zahlung wird noch einmal hingesehen:
        // steht das Gerät aus irgendeinem Grund nicht auf „freigeschaltet", wird es
        // das jetzt. Bezahlt ist bezahlt — und eine Funktion, die den Schaden
        // repariert, ist mehr wert als eine, die ihn nur nicht anrichtet.
        $current  = device_by_code((string) $device['code']);
        $repaired = !$fresh && $current && $current['status'] !== 'active';
        if ($fresh || $repaired) {
            activate_device((int) $device['id'], 'stripe:' . $sessionId);
        }
        $pdo->commit();
    } catch (Throwable $e) {
        // Nichts halb Fertiges stehen lassen: ohne den Rücksprung wäre die Zahlung
        // verbucht und das Gerät trotzdem gesperrt — der Zustand, der diesen ganzen
        // Block veranlasst hat.
        if ($pdo->inTransaction()) $pdo->rollBack();
        throw $e;
    }

    if ($fresh)    return 'activated';
    if ($repaired) return 'repaired';
    return 'already';
}

/**
 * Rückbuchung oder Erstattung: die Lizenz wird gesperrt.
 *
 * Das Ticket im Speicher des Fernsehers gilt zwar weiter — eine Unterschrift lässt
 * sich offline nicht zurücknehmen —, aber beim nächsten Nachfragen erfährt das Gerät,
 * dass es gesperrt ist, und der Bildschirm sagt es ehrlich.
 */
function stripe_revoke(string $sessionOrPaymentIntent, string $reason): void {
    $stmt = db()->prepare(
        'SELECT device_id FROM payments WHERE session_id = ? OR payment_intent = ? LIMIT 1'
    );
    $stmt->execute([$sessionOrPaymentIntent, $sessionOrPaymentIntent]);
    $deviceId = (int) ($stmt->fetchColumn() ?: 0);
    if (!$deviceId) return;

    db()->prepare("UPDATE devices SET status = 'blocked', ticket = NULL WHERE id = ?")
        ->execute([$deviceId]);
    db()->prepare('UPDATE payments SET refunded_at = NOW(), note = ? WHERE device_id = ?')
        ->execute([clean_text($reason, 120), $deviceId]);

    error_log('[karacast] Gerät ' . $deviceId . ' gesperrt (' . $reason . ').');
}
