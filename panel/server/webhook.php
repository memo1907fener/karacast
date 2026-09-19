<?php
/**
 * Stripes Meldung an uns: „bezahlt".
 *
 * Diese Adresse trägst du im Stripe-Dashboard ein (Entwickler → Webhooks) und
 * abonnierst dort genau diese Ereignisse:
 *
 *     checkout.session.completed      → freischalten
 *     charge.refunded                 → sperren
 *     charge.dispute.created          → sperren
 *
 * Dass diese Datei öffentlich erreichbar sein muss, ist unvermeidlich — Stripe muss
 * sie ja aufrufen können. Geschützt ist sie nicht durch Geheimhaltung, sondern durch
 * die Unterschrift unter jeder Meldung: ohne das Webhook-Geheimnis kommt hier
 * niemand durch, auch wenn er die Adresse kennt.
 *
 * Antwortet immer schnell und mit 200, wenn die Meldung verstanden wurde. Ein
 * Fehlercode führt bei Stripe zu Wiederholungen — die sind erwünscht, wenn wirklich
 * etwas schiefging, und lästig, wenn es nur ein Ereignis war, das uns nicht angeht.
 */
require __DIR__ . '/lib/db.php';
require __DIR__ . '/lib/util.php';
require __DIR__ . '/lib/license.php';
require __DIR__ . '/lib/stripe.php';

// Der rohe Rumpf, unverändert: jede Umformung — und sei es nur ein Leerzeichen —
// macht die Unterschrift ungültig.
$payload = file_get_contents('php://input') ?: '';
$header  = (string) ($_SERVER['HTTP_STRIPE_SIGNATURE'] ?? '');
$secret  = trim((string) (stripe_config()['webhook_secret'] ?? ''));

if (!stripe_verify_signature($payload, $header, $secret)) {
    error_log('[karacast] Webhook mit ungültiger Unterschrift abgewiesen.');
    http_response_code(400);
    exit('invalid signature');
}

$event = json_decode($payload, true);
if (!is_array($event)) {
    http_response_code(400);
    exit('bad payload');
}

$eventId = (string) ($event['id'] ?? '');
$type    = (string) ($event['type'] ?? '');
$object  = is_array($event['data']['object'] ?? null) ? $event['data']['object'] : [];

// Stripe wiederholt Meldungen, wenn die Antwort ausbleibt — auch dann, wenn wir sie
// in Wahrheit schon verarbeitet hatten. Jede Ereigniskennung wird deshalb genau
// einmal angenommen.
$seen = db()->prepare('INSERT IGNORE INTO webhook_events (event_id, type, at) VALUES (?, ?, NOW())');
$seen->execute([$eventId, substr($type, 0, 60)]);
if ($seen->rowCount() === 0) {
    http_response_code(200);
    exit('duplicate');
}

try {
    switch ($type) {
        case 'checkout.session.completed':
        case 'checkout.session.async_payment_succeeded':
            // Nicht dem Rumpf der Meldung glauben, sondern die Sitzung frisch holen:
            // so steht der Betrag, den wir prüfen, sicher aus Stripes Mund.
            $session = stripe_get_session((string) ($object['id'] ?? ''));
            $result  = stripe_fulfil($session);
            error_log('[karacast] Webhook ' . $type . ' → ' . $result);
            break;

        case 'charge.refunded':
            if (($object['refunded'] ?? false) === true) {
                stripe_revoke((string) ($object['payment_intent'] ?? ''), 'Erstattung');
            }
            break;

        case 'charge.dispute.created':
            stripe_revoke((string) ($object['payment_intent'] ?? ''), 'Rückbuchung');
            break;

        default:
            // Alles andere ist in Ordnung und geht uns nichts an.
            break;
    }
} catch (Throwable $e) {
    error_log('[karacast] Webhook ' . $type . ': ' . $e->getMessage());
    // 500 heißt für Stripe: bitte noch einmal versuchen. Genau das wollen wir hier —
    // die Ereigniskennung wird deshalb wieder freigegeben.
    db()->prepare('DELETE FROM webhook_events WHERE event_id = ?')->execute([$eventId]);
    http_response_code(500);
    exit('retry please');
}

http_response_code(200);
echo 'ok';
