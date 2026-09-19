<?php
/**
 * Die Schnittstelle, mit der die Fernseher sprechen. Zwei Aufrufe, beide klein.
 *
 * Erreichbar als  https://deine-domain/api/license/hello  — die .htaccess daneben
 * leitet alles hierher. Ohne mod_rewrite funktioniert auch
 * .../api/index.php/license/hello, dann trägst du in der App diese Adresse ein.
 */
require __DIR__ . '/../lib/db.php';
require __DIR__ . '/../lib/util.php';
require __DIR__ . '/../lib/license.php';

header('Cache-Control: no-store');

$route = trim(route_from_request(), '/');

/**
 * Welcher der beiden Aufrufe ist gemeint?
 *
 * PATH_INFO ist der einfache Weg, aber nicht jede Serverkonfiguration füllt es —
 * bei nginx mit PHP-FPM fehlt es regelmäßig. Dann wird die Adresse selbst
 * auseinandergenommen, und als letzte Möglichkeit bleibt ?r=license/hello.
 */
function route_from_request(): string {
    $info = $_SERVER['PATH_INFO'] ?? '';
    if ($info !== '') return $info;

    $uri = (string) ($_SERVER['REQUEST_URI'] ?? '');
    $uri = explode('?', $uri, 2)[0];
    $script = (string) ($_SERVER['SCRIPT_NAME'] ?? '');

    // .../api/index.php/license/hello → alles hinter dem Skriptnamen.
    if ($script !== '' && str_starts_with($uri, $script)) {
        $rest = substr($uri, strlen($script));
        if ($rest !== '') return $rest;
    }
    // .../api/license/hello → alles hinter dem Ordner.
    $dir = rtrim(str_replace('\\', '/', dirname($script)), '/');
    if ($dir !== '' && $dir !== '.' && str_starts_with($uri, $dir . '/')) {
        $rest = substr($uri, strlen($dir));
        if ($rest !== '' && trim($rest, '/') !== 'index.php') return $rest;
    }

    $fallback = $_GET['r'] ?? '';
    return is_string($fallback) ? $fallback : '';
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_out(['error' => 'POST erwartet'], 405);
}

try {
    match ($route) {
        'license/hello'  => handle_hello(),
        'license/redeem' => handle_redeem(),
        default          => json_out(['error' => 'Unbekannter Aufruf'], 404),
    };
} catch (Throwable $e) {
    // Nach außen nur, dass etwas schiefging. Ein Fehlertext vom Server ist eine
    // Landkarte für jemanden, der ihn aufmachen will.
    error_log('[karacast] ' . $e->getMessage());
    json_out(['error' => 'Serverfehler'], 500);
}

/**
 * Der tägliche Gruß eines Fernsehers.
 *
 * Antwortet immer freundlich: Status, seit wann die Testphase läuft, die eigene
 * Uhrzeit, und das Ticket, sobald es eines gibt.
 */
function handle_hello(): never {
    $in  = json_in();
    $ids = clean_ids($in['ids'] ?? []);
    if (!$ids) json_out(['error' => 'Keine Kennung'], 400);

    $device = find_or_create_device($ids, [
        'model'   => clean_text($in['model'] ?? '', 64),
        'version' => clean_text($in['versionName'] ?? '', 16),
    ]);

    // Hat der Fernseher inzwischen Kennungen, die im alten Ticket noch nicht
    // stehen, wird es hier neu ausgestellt — sonst verliert ein bezahltes Gerät
    // seine Lizenz, sobald die Kennung wegfällt, mit der es freigeschaltet wurde.
    $device = refresh_ticket_if_needed($device);

    json_out(reply_for($device));
}

/** Acht Zeichen einlösen, für alle, die den QR-Code nicht scannen konnten. */
function handle_redeem(): never {
    $in   = json_in();
    $ids  = clean_ids($in['ids'] ?? []);
    $code = normalize_redeem_code(clean_text($in['code'] ?? '', 16));
    if (!$ids) json_out(['error' => 'Keine Kennung'], 400);

    $device = find_or_create_device($ids);

    // Ein gesperrtes Gerät kann sich nicht freikaufen. Gesperrt wird bei
    // Rückbuchung; wäre der Weg hier offen, genügte ein zweiter Code, um denselben
    // Fernseher wieder laufen zu lassen. Die Prüfung steht vor dem Einlösen, damit
    // ein gültiger Code dabei nicht verbrannt wird.
    if ($device['status'] !== 'blocked' && $code !== '') {
        // Das Entwerten ist selbst die Prüfung: ein SELECT davor und ein UPDATE
        // danach wären zwei Schritte, und der Aktivierungsbildschirm fragt alle
        // zehn Sekunden. Zwei gleichzeitige Anfragen mit demselben Code hätten den
        // SELECT beide bestanden — aus einem bezahlten Code würden zwei Lizenzen.
        // So gewinnt genau eine Anfrage die Zeile, und nur sie schaltet frei.
        $claim = db()->prepare(
            'UPDATE redeem_codes SET used_at = NOW(), device_id = ?
             WHERE code = ? AND used_at IS NULL'
        );
        $claim->execute([$device['id'], $code]);

        if ($claim->rowCount() === 1) {
            activate_device((int) $device['id'], 'code:' . $code);
            $fresh = db()->prepare('SELECT * FROM devices WHERE id = ?');
            $fresh->execute([$device['id']]);
            $device = array_merge($device, $fresh->fetch() ?: []);
        }
    }

    // Ein falscher Code bekommt dieselbe Antwort wie ein Fernseher ohne Lizenz —
    // kein Ticket. Die App sagt dann „Hat nicht geklappt", und wer Codes raten
    // will, erfährt aus der Antwort nichts über den Unterschied.
    json_out(reply_for($device));
}

function reply_for(array $device): array {
    $trialDays = (int) (config()['trial_days'] ?? 14);
    return [
        'status'         => $device['status'],
        'trialStartedAt' => strtotime($device['first_seen']),
        'serverTime'     => time(),
        'trialDays'      => $trialDays,
        // Nur ein freigeschaltetes Gerät bekommt sein Ticket zu sehen.
        //
        // Dass hier eine passende Kennung genügt, ist Absicht: ein Ticket nützt
        // niemandem, in dessen Ticket die eigenen Kennungen nicht stehen — die App
        // prüft es gegen die Kennungen, die sie selbst aus der Hardware bildet.
        // Der wunde Punkt war nie das Herausgeben des Tickets, sondern das
        // Hineinschreiben fremder Kennungen; davor steht may_extend().
        'ticket'         => $device['status'] === 'active' ? (string) $device['ticket'] : '',
    ];
}
