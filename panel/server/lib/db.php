<?php
/** Die Datenbankverbindung. Eine pro Aufruf, danach schließt PHP sie selbst. */

function config(): array {
    static $config = null;
    if ($config === null) {
        $path = __DIR__ . '/../config.php';
        if (!is_file($path)) {
            http_response_code(500);
            exit('config.php fehlt. Kopiere config.example.php und trage deine Daten ein.');
        }
        $config = require $path;
        // Und PHP dieselbe Uhrzeit wie die Datenbank lesen lassen: die Spalten
        // kommen als nackte Zeichenkette zurück, und strtotime() legt sonst die
        // Zeitzone des Servers darüber.
        date_default_timezone_set('UTC');
    }
    return $config;
}

function db(): PDO {
    static $pdo = null;
    if ($pdo !== null) return $pdo;

    $c = config()['db'];
    $dsn = "mysql:host={$c['host']};dbname={$c['name']};charset=utf8mb4";
    try {
        $pdo = new PDO($dsn, $c['user'], $c['pass'], [
            // Ausnahmen statt stiller Fehler: ein Lizenzserver, der Probleme
            // verschweigt, verkauft irgendwann Lizenzen, die es nicht gibt.
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            // Echte Prepared Statements, nicht die emulierte Variante.
            PDO::ATTR_EMULATE_PREPARES   => false,
        ]);
        // Beide Uhren auf UTC festnageln. NOW() in der Datenbank und time() in
        // PHP müssen dasselbe meinen: aus first_seen wird der Beginn der
        // Testphase, und eine Stunde Unterschied ist eine Stunde Lizenz.
        $pdo->exec("SET time_zone = '+00:00'");
    } catch (PDOException $e) {
        http_response_code(500);
        exit('Datenbank nicht erreichbar.');
    }
    return $pdo;
}
