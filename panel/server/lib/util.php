<?php
/** Kleinkram, den sonst jede Datei doppelt hätte. */

/** Base64 in der URL-tauglichen Form, ohne Füllzeichen — so wie im Ticket. */
function b64url(string $raw): string {
    return rtrim(strtr(base64_encode($raw), '+/', '-_'), '=');
}

/**
 * Derselbe Kurz-Hash wie in der App: die ersten sechs Bytes von SHA-256, hex.
 * Muss mit DeviceIdentity.kt übereinstimmen, sonst erkennt sich kein Gerät wieder.
 */
function short_hash(string $value): string {
    return substr(hash('sha256', strtolower(trim($value))), 0, 12);
}

/** Antwortet als JSON und beendet den Aufruf. */
function json_out(array $data, int $status = 200): never {
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($data, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

/** Liest den JSON-Rumpf einer Anfrage. Kaputtes JSON gibt ein leeres Array. */
function json_in(): array {
    $raw = file_get_contents('php://input') ?: '';
    $data = json_decode($raw, true);
    return is_array($data) ? $data : [];
}

/**
 * Nimmt aus einer Anfrage nur, was wie ein Kennungs-Hash aussieht.
 *
 * Alles, was von außen kommt, wird hier zurechtgestutzt, bevor es in die Nähe
 * der Datenbank kommt: höchstens acht Stück, jeweils genau zwölf Hex-Zeichen.
 */
function clean_ids(mixed $input): array {
    if (!is_array($input)) return [];
    $out = [];
    foreach ($input as $id) {
        if (!is_string($id)) continue;
        $id = strtolower(trim($id));
        if (preg_match('/^[0-9a-f]{12}$/', $id)) $out[$id] = true;
        if (count($out) >= 8) break;
    }
    return array_keys($out);
}

function clean_text(mixed $value, int $max = 64): string {
    if (!is_string($value)) return '';
    $value = preg_replace('/[^\PC\s]/u', '', $value) ?? '';
    return mb_substr(trim($value), 0, $max);
}

/** Ein Code, den man am Fernseher mit der Fernbedienung eintippen kann. */
function new_redeem_code(): string {
    // Ohne 0/O und 1/I — die verwechselt am Telefon jeder.
    $alphabet = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
    $code = '';
    for ($i = 0; $i < 8; $i++) {
        $code .= $alphabet[random_int(0, strlen($alphabet) - 1)];
        if ($i === 3) $code .= '-';
    }
    return $code;
}

/**
 * Bringt einen eingetippten Code auf die Form, in der er in der Datenbank steht.
 *
 * Am Fernseher wird mit der Fernbedienung getippt: mal mit Bindestrich, mal ohne,
 * mal mit einem Leerzeichen dazwischen, oft klein. Das ist alles derselbe Code,
 * und daran soll eine Freischaltung nicht scheitern.
 */
function normalize_redeem_code(string $input): string {
    $raw = strtoupper(preg_replace('/[^0-9A-Za-z]/', '', $input) ?? '');
    if (strlen($raw) === 8) {
        return substr($raw, 0, 4) . '-' . substr($raw, 4);
    }
    return substr($raw, 0, 16);
}

function csrf_token(): string {
    if (empty($_SESSION['csrf'])) $_SESSION['csrf'] = bin2hex(random_bytes(16));
    return $_SESSION['csrf'];
}

function csrf_check(): void {
    $given  = $_POST['csrf'] ?? '';
    $stored = $_SESSION['csrf'] ?? '';
    // Der leere Fall muss ausdrücklich scheitern: hash_equals('', '') ist wahr,
    // und ohne diese Zeile käme eine Anfrage ohne Sitzung glatt durch — also
    // genau der Fall, gegen den das Ganze gedacht ist.
    if (!is_string($given) || !is_string($stored) || $stored === '' || $given === ''
        || !hash_equals($stored, $given)) {
        http_response_code(400);
        exit('Ungültige Anfrage.');
    }
}

function e(?string $value): string {
    return htmlspecialchars($value ?? '', ENT_QUOTES, 'UTF-8');
}

/**
 * „Fehler 500" ist keine Auskunft.
 *
 * Auf einem normalen Webspace steht `display_errors` aus — zu Recht, denn eine
 * PHP-Fehlermeldung nennt Pfade, Datenbanknamen und manchmal Schlüssel. Nur: dann
 * bleibt bei einem Absturz genau eine leere weiße Seite übrig, und die Fehlersuche
 * beginnt mit „schau mal im Fehlerprotokoll deines Hosters", was oft eine halbe
 * Stunde kostet.
 *
 * Also beides: ins Protokoll kommt immer alles, und **auf die Seite** kommt der
 * Grund genau dann, wenn gerade jemand am Panel angemeldet ist. Für alle anderen
 * bleibt es bei einem neutralen Satz.
 */
(function (): void {
    $show = static function (string $kind, string $message, string $file, int $line): void {
        error_log('[karacast ' . $kind . '] ' . $message . ' in ' . $file . ':' . $line);

        if (!headers_sent()) {
            http_response_code(500);
            header('Content-Type: text/html; charset=utf-8');
        }

        $isAdmin = session_status() === PHP_SESSION_ACTIVE && !empty($_SESSION['admin']);
        if (!$isAdmin) {
            echo '<p style="font:16px system-ui;padding:24px">Es ist ein Fehler aufgetreten. '
               . 'Bitte versuch es später noch einmal.</p>';
            return;
        }

        echo '<div style="font:14px/1.6 ui-monospace,monospace;background:#1a0f12;color:#ffd7d7;'
           . 'border:1px solid #5a2a2a;border-radius:12px;padding:18px;margin:18px">'
           . '<strong>Panel-Fehler (' . htmlspecialchars($kind) . ')</strong><br><br>'
           . htmlspecialchars($message) . '<br><br>'
           . '<span style="opacity:.7">' . htmlspecialchars($file) . ':' . $line . '</span><br><br>'
           . '<span style="opacity:.7">Diese Einzelheiten siehst du, weil du angemeldet bist. '
           . 'Besucher bekommen nur einen neutralen Satz.</span></div>';
    };

    set_exception_handler(static function (Throwable $e) use ($show): void {
        $show('Ausnahme', get_class($e) . ': ' . $e->getMessage(), $e->getFile(), $e->getLine());
    });

    register_shutdown_function(static function () use ($show): void {
        $last = error_get_last();
        if (!$last) return;
        if (!in_array($last['type'], [E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR], true)) return;
        $show('Abbruch', (string) $last['message'], (string) $last['file'], (int) $last['line']);
    });
})();
