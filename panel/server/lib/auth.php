<?php
/** Die Anmeldung fürs Panel. Ein Benutzer, ein Passwort-Hash in der Datenbank. */

function session_start_safe(): void {
    if (session_status() === PHP_SESSION_ACTIVE) return;
    // Eine Sitzungskennung, die PHP nicht selbst vergeben hat, wird verworfen.
    // Ohne das könnte jemand dem Besitzer vorher eine bekannte Kennung unterschieben
    // und die Sitzung nach dessen Anmeldung übernehmen.
    ini_set('session.use_strict_mode', '1');
    session_set_cookie_params([
        'httponly' => true,
        'samesite' => 'Lax',
        'secure'   => request_is_https(),
    ]);
    session_start();
}

/**
 * Läuft dieser Aufruf über HTTPS?
 *
 * Hinter einem Reverse-Proxy — und das ist bei fast jedem Hoster so — endet die
 * Verschlüsselung davor, und $_SERVER['HTTPS'] ist leer, obwohl der Besucher
 * längst auf einer https-Adresse steht. Ohne die zweite Zeile bekäme das
 * Sitzungscookie dort nie das secure-Flag.
 */
function request_is_https(): bool {
    if (!empty($_SERVER['HTTPS']) && strtolower((string) $_SERVER['HTTPS']) !== 'off') return true;
    if (($_SERVER['SERVER_PORT'] ?? '') === '443') return true;
    $forwarded = strtolower((string) ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? ''));
    return str_contains($forwarded, 'https');
}

function admin_password_hash(): ?string {
    // Einfache Anführungszeichen überall: unter ANSI_QUOTES wäre "admin_hash"
    // ein Spaltenname und die Abfrage schlüge fehl.
    $row = db()->query("SELECT value FROM settings WHERE name = 'admin_hash'")->fetch();
    return $row['value'] ?? null;
}

function set_admin_password(string $plain): void {
    $hash = password_hash($plain, PASSWORD_DEFAULT);
    db()->prepare("REPLACE INTO settings (name, value) VALUES ('admin_hash', ?)")->execute([$hash]);
}

function is_logged_in(): bool {
    session_start_safe();
    return !empty($_SESSION['admin']);
}

function require_admin(): void {
    if (!is_logged_in()) {
        header('Location: login.php');
        exit;
    }
}

/**
 * Bremst das Raten aus.
 *
 * Nicht mit einer harten Sperre nach fünf Versuchen: bei den meisten Hostern steht
 * ein Proxy davor, und dann ist $_SERVER['REMOTE_ADDR'] für **alle** Besucher
 * dieselbe Adresse. Fünf falsche Eingaben von irgendwem hätten den Besitzer aus
 * seinem eigenen Panel ausgesperrt, beliebig oft wiederholbar.
 *
 * Stattdessen wird jeder Versuch langsamer beantwortet als der davor. Nach zwanzig
 * Fehlversuchen in einer Viertelstunde ist dann doch Schluss — so weit kommt, wer
 * sein Passwort bloß vergessen hat, nicht, und wer durchprobiert, schafft in einer
 * Stunde eine Handvoll Versuche statt Tausender.
 */
function login_attempts(string $ip): int {
    $stmt = db()->prepare(
        'SELECT COUNT(*) AS n FROM login_attempts
         WHERE ip = ? AND at > DATE_SUB(NOW(), INTERVAL 15 MINUTE)'
    );
    $stmt->execute([$ip]);
    return (int) ($stmt->fetch()['n'] ?? 0);
}

function login_blocked(string $ip): bool {
    return login_attempts($ip) >= 20;
}

/** Wartet so lange, wie die bisherigen Fehlversuche es verdienen. Höchstens drei Sekunden. */
function login_delay(string $ip): void {
    $n = login_attempts($ip);
    if ($n > 0) usleep((int) min($n * 400_000, 3_000_000));
}

function note_failed_login(string $ip): void {
    db()->prepare('INSERT INTO login_attempts (ip, at) VALUES (?, NOW())')->execute([$ip]);
    // Gleich mit aufräumen. Die Tabelle wird sonst über Jahre zur Müllhalde,
    // und älter als einen Tag interessiert hier nichts mehr.
    db()->exec('DELETE FROM login_attempts WHERE at < DATE_SUB(NOW(), INTERVAL 1 DAY)');
}

function clear_logins(string $ip): void {
    db()->prepare('DELETE FROM login_attempts WHERE ip = ?')->execute([$ip]);
}
