<?php
/**
 * Drei Sprachen, ohne Bibliothek.
 *
 * Die Wahl fällt einmal pro Aufruf und in dieser Reihenfolge: was in der Adresse
 * steht (?lang=tr), sonst was der Besucher beim letzten Mal gewählt hat, sonst was
 * sein Browser mitschickt, sonst Deutsch. Nur die erste dieser Quellen ist eine
 * Entscheidung des Besuchers — deshalb schreibt nur sie das Cookie.
 */

const LANGS = ['de' => 'Deutsch', 'en' => 'English', 'tr' => 'Türkçe'];

function current_lang(): string {
    static $lang = null;
    if ($lang !== null) return $lang;

    $wanted = is_string($_GET['lang'] ?? null) ? strtolower($_GET['lang']) : '';
    if (isset(LANGS[$wanted])) {
        // Ein Jahr. Funktionscookie, kein Zählwerk: es steht nichts darin außer
        // zwei Buchstaben, und ohne es sähe die Seite nur in der falschen Sprache
        // aus. Dafür braucht es kein Einwilligungsbanner.
        setcookie('lang', $wanted, [
            'expires'  => time() + 31536000,
            'path'     => '/',
            'samesite' => 'Lax',
            'secure'   => !empty($_SERVER['HTTPS']),
            'httponly' => false,
        ]);
        return $lang = $wanted;
    }

    $cookie = is_string($_COOKIE['lang'] ?? null) ? strtolower($_COOKIE['lang']) : '';
    if (isset(LANGS[$cookie])) return $lang = $cookie;

    return $lang = browser_lang();
}

/** Die erste Sprache aus dem Accept-Language-Kopf, die es hier überhaupt gibt. */
function browser_lang(): string {
    $header = strtolower((string) ($_SERVER['HTTP_ACCEPT_LANGUAGE'] ?? ''));
    foreach (explode(',', $header) as $part) {
        $code = substr(trim(explode(';', $part)[0]), 0, 2);
        if (isset(LANGS[$code])) return $code;
    }
    return 'de';
}

/**
 * Ein Textbaustein.
 *
 * Fehlt er in der gewählten Sprache, kommt der deutsche — eine halb übersetzte
 * Seite ist unangenehm, eine Seite mit leeren Löchern ist kaputt.
 */
function t(string $key, string|int ...$args): string {
    static $strings = [];
    $lang = current_lang();

    if (!isset($strings[$lang])) {
        $strings[$lang] = require __DIR__ . '/../lang/' . $lang . '.php';
    }
    if (!isset($strings['de'])) {
        $strings['de'] = require __DIR__ . '/../lang/de.php';
    }

    $text = $strings[$lang][$key] ?? $strings['de'][$key] ?? $key;
    return $args ? vsprintf($text, $args) : $text;
}

/** Dieselbe Seite, andere Sprache — mit allem, was sonst in der Adresse steht. */
function lang_url(string $lang): string {
    $query = $_GET;
    $query['lang'] = $lang;
    $path = strtok((string) ($_SERVER['REQUEST_URI'] ?? '/'), '?');
    return $path . '?' . http_build_query($query);
}
