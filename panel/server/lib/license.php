<?php
/**
 * Das Herzstück: ein Ticket unterschreiben.
 *
 * Der private Schlüssel liegt in keys/private.pem und verlässt diesen Server nie.
 * Die App trägt nur den passenden öffentlichen Schlüssel und prüft die Unterschrift
 * danach selbst — offline, für immer. Fällt dieser Server aus, verliert kein
 * zahlender Kunde sein Fernsehen.
 */

function private_key_path(): string {
    // Über config.php verschiebbar, damit der Schlüssel auch oberhalb des
    // Webverzeichnisses liegen kann, wo kein Browser je hinkommt.
    $custom = config()['key_path'] ?? '';
    return $custom !== '' ? $custom : __DIR__ . '/../keys/private.pem';
}

function has_key(): bool {
    return is_file(private_key_path()) && filesize(private_key_path()) > 0;
}

/**
 * Erzeugt das Schlüsselpaar. Wird genau einmal von install.php aufgerufen.
 *
 * @return string der öffentliche Schlüssel als Base64 — genau die Zeichenkette,
 *   die in der App in LicenseVerifier.kt eingetragen wird.
 */
function create_keypair(): string {
    $key = openssl_pkey_new([
        'private_key_type' => OPENSSL_KEYTYPE_EC,
        'curve_name'       => 'prime256v1',
    ]);
    if ($key === false) {
        throw new RuntimeException('OpenSSL konnte kein Schlüsselpaar erzeugen.');
    }

    // Beides geprüft: auf einem Hosting ohne brauchbare openssl.cnf schlägt der
    // Export still fehl, und ein leeres private.pem fällt sonst erst bei der
    // ersten Freischaltung auf — also genau dann, wenn ein Kunde wartet.
    if (!openssl_pkey_export($key, $privatePem)) {
        throw new RuntimeException('Schlüssel nicht exportierbar — OpenSSL-Konfiguration prüfen.');
    }
    if (file_put_contents(private_key_path(), $privatePem) === false) {
        throw new RuntimeException('Der Ordner keys/ ist nicht beschreibbar.');
    }
    @chmod(private_key_path(), 0600);

    return public_key_base64();
}

/** Der öffentliche Schlüssel im Format, das Java erwartet (X.509 SPKI, DER, Base64). */
function public_key_base64(): string {
    $details = openssl_pkey_get_details(openssl_pkey_get_private(
        file_get_contents(private_key_path())
    ));
    $pem = $details['key'] ?? '';
    // PEM-Rahmen weg, übrig bleibt genau das DER in Base64.
    return preg_replace('/-----[^-]+-----|\s+/', '', $pem) ?? '';
}

/**
 * Stellt ein Ticket aus.
 *
 * @param string[] $ids alle Kennungen, die zu diesem Gerät bekannt sind. Die App
 *   akzeptiert das Ticket, wenn **eine** davon passt — so überlebt die Lizenz den
 *   Wechsel von WLAN auf Kabel.
 */
function issue_ticket(array $ids, string $order): string {
    $payload = json_encode([
        'v'    => 1,
        'ids'  => array_values(array_map('strval', $ids)),
        'iat'  => time(),
        'plan' => 'lifetime',
        'ord'  => $order,
    ], JSON_UNESCAPED_SLASHES);

    $signed = 'KC1.' . b64url($payload);

    $key = openssl_pkey_get_private(file_get_contents(private_key_path()));
    if ($key === false) throw new RuntimeException('Privater Schlüssel nicht lesbar.');

    // Bei einem EC-Schlüssel liefert openssl_sign eine DER-Signatur — genau das,
    // was Javas SHA256withECDSA erwartet. Nichts umzurechnen.
    if (!openssl_sign($signed, $signature, $key, OPENSSL_ALGO_SHA256)) {
        throw new RuntimeException('Signieren fehlgeschlagen.');
    }

    return $signed . '.' . b64url($signature);
}

/** Die Kennungen, die in einem ausgestellten Ticket stehen. */
function ticket_ids(string $ticket): array {
    $parts = explode('.', $ticket);
    if (count($parts) !== 3) return [];
    $json = base64_decode(strtr($parts[1], '-_', '+/')) ?: '';
    $body = json_decode($json, true);
    return is_array($body['ids'] ?? null) ? $body['ids'] : [];
}

/**
 * Sucht ein Gerät über **irgendeine** seiner Kennungen und legt es sonst an.
 *
 * Hier hängt die ganze Fingerabdruck-Idee: ein Fernseher meldet sich mit mehreren
 * Kennungen, von denen mit der Zeit welche wegfallen und neue dazukommen. Gefunden
 * wird über jede einzelne, und was neu ist, wird ergänzt.
 *
 * @return array die Gerätezeile aus der Datenbank.
 */
function find_or_create_device(array $ids, array $info = []): array {
    $pdo = db();
    if (!$ids) throw new InvalidArgumentException('Keine Kennung übermittelt.');

    // Der angezeigte Gerätecode wird hier hergeleitet, nicht vom Fernseher
    // übernommen: er ist genau die stärkste Kennung in Großbuchstaben, und die
    // App bildet ihn genauso. Würde man dem Gerät glauben, könnte es sich als ein
    // fremdes ausgeben und in die Nähe einer fremden Lizenz kommen.
    $code = strtoupper($ids[0]);

    $marks = implode(',', array_fill(0, count($ids), '?'));
    $stmt = $pdo->prepare(
        "SELECT d.*, COUNT(i.id_hash) AS matched FROM devices d
         JOIN device_ids i ON i.device_id = d.id
         WHERE i.id_hash IN ($marks)
         GROUP BY d.id ORDER BY matched DESC, d.id"
    );
    $stmt->execute($ids);
    $rows = $stmt->fetchAll();

    if (count($rows) > 1) {
        $device = resolve_collision($rows);
    } elseif ($rows) {
        $device = $rows[0];
    } else {
        // INSERT IGNORE plus eindeutiger code: melden sich zwei Anfragen desselben
        // Fernsehers gleichzeitig — der Aktivierungsbildschirm fragt alle zehn
        // Sekunden —, entsteht trotzdem nur eine Zeile.
        $pdo->prepare(
            "INSERT IGNORE INTO devices (code, first_seen, last_seen, model, version, status)
             VALUES (?, NOW(), NOW(), ?, ?, 'trial')"
        )->execute([$code, $info['model'] ?? '', $info['version'] ?? '']);
        $find = $pdo->prepare('SELECT * FROM devices WHERE code = ? ORDER BY id LIMIT 1');
        $find->execute([$code]);
        $device = $find->fetch();
        if (!$device) throw new RuntimeException('Gerät konnte nicht angelegt werden.');
        $device['matched'] = 0;
    }

    $update = $pdo->prepare(
        'UPDATE devices SET last_seen = NOW(), model = ?, version = ? WHERE id = ?'
    );
    $update->execute([
        // Ein leeres Feld darf nie überschreiben, was schon dasteht.
        ($info['model'] ?? '') ?: $device['model'],
        ($info['version'] ?? '') ?: $device['version'],
        $device['id'],
    ]);

    $matched = (int) ($device['matched'] ?? 0);
    $trusted = may_extend($device, $matched);

    if ($trusted) {
        $add = $pdo->prepare('INSERT IGNORE INTO device_ids (device_id, id_hash) VALUES (?, ?)');
        foreach ($ids as $id) $add->execute([$device['id'], $id]);
    } else {
        // Nicht abgewiesen, nur zurückgestellt: die unbekannte Kennung landet in
        // pending_ids und wartet auf einen Klick im Panel. Der Fernseher merkt
        // davon nichts und läuft normal weiter.
        note_pending_ids((int) $device['id'], $ids);
    }

    $again = $pdo->prepare('SELECT * FROM devices WHERE id = ?');
    $again->execute([$device['id']]);
    $fresh = $again->fetch();
    $fresh['matched']  = $matched;
    $fresh['_trusted'] = $trusted;
    return $fresh;
}

/**
 * Darf dieser Anrufer die Kennungsliste dieses Geräts erweitern?
 *
 * Hier sitzt der wunde Punkt der ganzen Konstruktion, deshalb ausführlich:
 * Der **Gerätecode ist öffentlich**. Er steht auf dem Fernsehschirm, er steckt im
 * QR-Code, er steht auf der Aktivierungsseite und in jeder Support-Mail — und er
 * ist zugleich eine der Kennungen des Geräts. Würde eine einzige passende Kennung
 * genügen, um weitere anzuhängen, dann könnte jeder, der einen fremden Code
 * abliest, seine eigene Kennung an eine bezahlte Lizenz hängen; das Ticket würde
 * beim nächsten Nachfragen über beide ausgestellt, und sein Fernseher wäre gratis
 * für immer freigeschaltet. Dem Bestohlenen fiele nie etwas auf.
 *
 * Zwei übereinstimmende Kennungen hat dagegen nur, wer wirklich dieser Fernseher
 * ist: die zweite steht nirgends geschrieben. Ein echtes Gerät meldet ohnehin
 * immer mehrere — MAC, ANDROID_ID, die eigene Zufallskennung.
 *
 * In der Testphase gilt die Regel nicht: dort gibt es nichts zu holen, und ein
 * Gerät, das seine Kennungen sammelt, bevor es bezahlt, erspart hinterher Ärger.
 */
function may_extend(array $device, int $matched): bool {
    if (($device['status'] ?? '') === 'trial') return true;
    return $matched >= 2;
}

/**
 * Merkt sich Kennungen, die zu einem bezahlten Gerät gehören könnten — aber es
 * eben auch nicht müssen. Im Panel steht daneben ein Knopf.
 */
function note_pending_ids(int $deviceId, array $ids): void {
    $new = array_diff($ids, device_ids($deviceId));
    if (!$new) return;

    $stmt = db()->prepare('SELECT COUNT(*) FROM pending_ids WHERE device_id = ?');
    $stmt->execute([$deviceId]);
    $have = (int) $stmt->fetchColumn();

    $insert = db()->prepare(
        'INSERT INTO pending_ids (id_hash, device_id, first_seen, last_seen, seen_count)
         VALUES (?, ?, NOW(), NOW(), 1)
         ON DUPLICATE KEY UPDATE last_seen = NOW(), seen_count = seen_count + 1'
    );
    foreach ($new as $id) {
        // Gedeckelt, damit niemand die Liste mit erfundenen Kennungen vollschreibt.
        if ($have >= 12) return;
        $insert->execute([$id, $deviceId]);
        $have++;
    }
}

/**
 * Mehrere Zeilen passen auf dieselbe Anfrage. Das ist entweder ein Gerät, das
 * doppelt angelegt wurde — oder jemand, der zwei fremde Kennungen zusammenwirft.
 * Zusammengeführt wird nur, wenn nichts zu verlieren ist.
 */
function resolve_collision(array $rows): array {
    $paid = array_values(array_filter($rows, fn($r) => $r['status'] === 'active'));

    // Keine bezahlte Zeile dabei: gefahrlos, das ist der häufige Fall.
    if (!$paid) return merge_devices($rows);

    // Genau eine bezahlte Zeile, und der Anrufer weist sich ihr gegenüber mit
    // mindestens zwei Kennungen aus: dann ist er dieser Fernseher, und die
    // bezahlte Zeile führt — unabhängig davon, welche zuerst angelegt wurde.
    if (count($paid) === 1 && (int) $paid[0]['matched'] >= 2) {
        $others = array_values(array_filter($rows, fn($r) => $r['id'] !== $paid[0]['id']));
        return merge_devices(array_merge([$paid[0]], $others));
    }

    // Alles andere: nichts verschieben, nichts zusammenführen, nur vermerken.
    // Geantwortet wird der unverfänglichsten Zeile — einer fremden Lizenz kommt
    // der Anrufer so gar nicht erst nahe.
    foreach ($rows as $row) {
        if ($row['status'] !== 'active') return $row;
    }
    return $paid[0];
}

/**
 * Führt zwei Zeilen zusammen, die derselbe Fernseher sind.
 *
 * Passiert wirklich: ein Gerät meldet sich erst nur mit seiner Zufallskennung,
 * weil die MAC-Adresse nicht lesbar war, und nach einem Firmware-Update plötzlich
 * mit beiden. Ohne dieses Zusammenführen bliebe eine gekaufte Lizenz auf der
 * verwaisten Zeile liegen und der Fernseher stünde für immer in der Testphase.
 */
function merge_devices(array $rows): array {
    $pdo  = db();
    $keep = $rows[0];

    foreach (array_slice($rows, 1) as $dup) {
        $pdo->prepare('UPDATE device_ids SET device_id = ? WHERE device_id = ?')
            ->execute([$keep['id'], $dup['id']]);

        // Eine Sperre überträgt sich, eine Freischaltung nicht. Gesperrt wird nach
        // einer Rückbuchung; wäre es andersherum, könnte ein gesperrter Fernseher
        // sich freimachen, indem er sich mit einer zweiten Kennung neu meldet.
        if ($dup['status'] === 'blocked' && $keep['status'] !== 'blocked'
            && !str_starts_with((string) $dup['order_ref'], 'merged:')) {
            $pdo->prepare("UPDATE devices SET status = 'blocked', ticket = NULL WHERE id = ?")
                ->execute([$keep['id']]);
            $keep['status'] = 'blocked';
        }
        // Die leergeräumte Zeile bleibt stehen, aber sie sagt selbst, wohin sie
        // gehört: sucht jemand im Panel nach dem alten Code, soll er nicht aus
        // Versehen eine Karteileiche freischalten.
        $pdo->prepare(
            "UPDATE devices SET status = 'blocked', ticket = NULL, order_ref = ?,
                    note = CONCAT(note, ' [zusammengeführt mit ', ?, ']') WHERE id = ?"
        )->execute(['merged:' . $keep['id'], $keep['code'], $dup['id']]);
    }

    $stmt = $pdo->prepare('SELECT * FROM devices WHERE id = ?');
    $stmt->execute([$keep['id']]);
    return $stmt->fetch();
}

/** Alle bekannten Kennungen eines Geräts — das, was ins Ticket kommt. */
function device_ids(int $deviceId): array {
    $stmt = db()->prepare('SELECT id_hash FROM device_ids WHERE device_id = ?');
    $stmt->execute([$deviceId]);
    return array_map('strval', array_column($stmt->fetchAll(), 'id_hash'));
}

/**
 * Schaltet ein Gerät frei: Ticket ausstellen, Status setzen.
 *
 * Der Vermerk wird gekürzt, bevor er in die Spalte geht. Das klingt nach einer
 * Kleinigkeit und war es nicht: eine Stripe-Sitzungskennung ist 66 Zeichen lang,
 * die Spalte fasste 64, und MySQL weist im strengen Modus einen zu langen Wert
 * nicht etwa gekürzt ein, sondern mit einem Fehler ab. Die Zahlung war da, die
 * Freischaltung brach ab, und der Kunde stand vor einem Fernseher, der weiter nach
 * Geld fragte. Die Spalte ist inzwischen breiter — aber sich darauf zu verlassen,
 * dass ein Fremdsystem seine Kennungen nie verlängert, wäre genau derselbe Fehler
 * noch einmal.
 */
function activate_device(int $deviceId, string $order): string {
    $order  = mb_substr($order, 0, 180);
    $ticket = issue_ticket(device_ids($deviceId), $order);
    db()->prepare(
        "UPDATE devices SET status = 'active', ticket = ?, order_ref = ?, activated_at = NOW()
         WHERE id = ?"
    )->execute([$ticket, $order, $deviceId]);
    return $ticket;
}

/**
 * Stellt das Ticket neu aus, wenn der Fernseher inzwischen Kennungen hat, die
 * noch nicht darin stehen.
 *
 * Ohne das verliert ein bezahltes Gerät irgendwann seine Lizenz: aktiviert wurde
 * es, als nur die Zufallskennung lesbar war; die geht beim Löschen der App-Daten
 * verloren, und das alte Ticket kennt die inzwischen lesbare MAC-Adresse nicht.
 */
function refresh_ticket_if_needed(array $device): array {
    if ($device['status'] !== 'active' || empty($device['ticket'])) return $device;

    $known = device_ids((int) $device['id']);
    if (!array_diff($known, ticket_ids((string) $device['ticket']))) return $device;

    activate_device((int) $device['id'], (string) $device['order_ref']);
    $stmt = db()->prepare('SELECT * FROM devices WHERE id = ?');
    $stmt->execute([$device['id']]);
    $fresh = $stmt->fetch();
    // Die Vertrauensmerkmale gehören zur Anfrage, nicht zur Zeile — beim erneuten
    // Lesen aus der Datenbank sind sie sonst weg, und die Antwort gäbe das Ticket
    // heraus, obwohl sie es gerade nicht darf.
    foreach (['matched', '_trusted'] as $key) {
        if (array_key_exists($key, $device)) $fresh[$key] = $device[$key];
    }
    return $fresh;
}

/** Findet ein Gerät über eine einzelne Kennung oder über seinen angezeigten Code. */
function device_by_code(string $code): ?array {
    $stmt = db()->prepare(
        'SELECT d.* FROM devices d
         JOIN device_ids i ON i.device_id = d.id
         WHERE i.id_hash = ? ORDER BY d.id LIMIT 1'
    );
    $stmt->execute([strtolower($code)]);
    $row = $stmt->fetch();
    if ($row) return $row;

    $byCode = db()->prepare('SELECT * FROM devices WHERE code = ? ORDER BY id LIMIT 1');
    $byCode->execute([strtoupper($code)]);
    return $byCode->fetch() ?: null;
}
