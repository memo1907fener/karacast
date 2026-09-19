# Lizenzserver — Spezifikation

Die App bietet 14 Tage kostenlos an und muss danach einmalig freigeschaltet werden.
Dieses Dokument beschreibt genau, was der Server dafür können muss. Die App-Seite ist
fertig und wartet nur noch auf diese Endpunkte.

## Der Grundsatz

**Ein geprüftes Ticket braucht keinen Server mehr.** Der Server unterschreibt die
Lizenz einmal; die App prüft die Unterschrift von da an selbst — offline, für immer.
Fällt karacast.de aus, verliert kein zahlender Kunde sein Fernsehen.

Daraus folgt der zweite Grundsatz: **ein nicht erreichbarer Server nimmt nie etwas
weg.** Eine gescheiterte Anfrage lässt alles, wie es war.

## Schlüsselpaar erzeugen

Einmalig, auf dem Server, und der private Teil verlässt ihn nie:

```bash
openssl ecparam -name prime256v1 -genkey -noout -out karacast-private.pem
openssl ec -in karacast-private.pem -pubout -outform DER | base64 -w0
```

Die zweite Zeile gibt den öffentlichen Schlüssel aus. Der kommt in die App, in
`util/LicenseVerifier.kt` → `PUBLIC_KEY_BASE64`. Dort steht derzeit ein
**Entwicklungsschlüssel**; der gehört vor dem ersten Verkauf ersetzt.

Der private Schlüssel gehört mit `chmod 600` gesichert und in kein Git-Repository.
Geht er verloren, kann keine neue Lizenz mehr ausgestellt werden — bereits verkaufte
funktionieren aber weiter, weil sie nicht nachgeprüft werden müssen.

## Das Ticket

Drei Teile, durch Punkte getrennt:

```
KC1.<base64url(payload)>.<base64url(signatur)>
```

Der Payload ist JSON:

```json
{
  "v": 1,
  "ids": ["a1b2c3d4e5f6", "9f8e7d6c5b4a"],
  "iat": 1789642179,
  "plan": "lifetime",
  "ord": "pi_3QxyzABC"
}
```

| Feld   | Bedeutung |
|--------|-----------|
| `ids`  | **Alle** Gerätekennungen, die der Server zu diesem Kauf kennt. Die App akzeptiert das Ticket, wenn *eine* davon zu ihr passt — so überlebt die Lizenz einen Wechsel von WLAN auf LAN. |
| `iat`  | Zeitpunkt der Ausstellung, Sekunden seit 1970. |
| `plan` | Heute immer `lifetime`. Das Feld existiert, damit später Jahres- oder Mehrgerätelizenzen möglich sind, ohne die App zu ändern. |
| `ord`  | Deine Zahlungsreferenz, für den Supportfall. |

Signiert wird über die **ASCII-Bytes von `KC1.<base64url(payload)>`** — also über die
ersten beiden Teile so, wie sie dastehen. Base64url heißt: `+` → `-`, `/` → `_`, ohne
`=` am Ende.

In PHP:

```php
function issueTicket(array $ids, string $order, string $privateKeyPem): string {
    $payload = json_encode([
        'v'    => 1,
        'ids'  => array_values($ids),
        'iat'  => time(),
        'plan' => 'lifetime',
        'ord'  => $order,
    ], JSON_UNESCAPED_SLASHES);

    $b64 = fn(string $raw) => rtrim(strtr(base64_encode($raw), '+/', '-_'), '=');
    $signed = 'KC1.' . $b64($payload);

    $key = openssl_pkey_get_private($privateKeyPem);
    openssl_sign($signed, $signature, $key, OPENSSL_ALGO_SHA256);

    return $signed . '.' . $b64($signature);
}
```

`openssl_sign` liefert bei einem EC-Schlüssel eine DER-kodierte Signatur — genau das,
was Javas `SHA256withECDSA` erwartet. Es ist nichts umzuwandeln.

## Endpunkt 1 — `POST /api/license/hello`

Wird beim Start einmal am Tag gefragt und auf dem Aktivierungsbildschirm alle zehn
Sekunden.

**Anfrage**

```json
{
  "device": "A1B2C3D4E5F6",
  "ids": ["a1b2c3d4e5f6", "9f8e7d6c5b4a", "77aa88bb99cc"],
  "app": "karacast",
  "versionCode": 13,
  "versionName": "1.9.0",
  "model": "TCL Smart TV Pro",
  "language": "de"
}
```

`device` ist die Kennung, die auf dem Fernseher steht — abgeleitet aus der
belastbarsten verfügbaren Kennung, meist der MAC-Adresse des LAN-Anschlusses.
`ids` sind **alle** Kennungen dieses Geräts als Kurz-Hashes, stärkste zuerst:
LAN-MAC, WLAN-MAC, Android-ID, App-eigene Zufallskennung.

**Der Server muss:**

1. Ein Gerät suchen, bei dem **irgendeine** der `ids` bereits bekannt ist.
2. Gefunden: die neuen `ids` dem Gerät hinzufügen (so wächst der Fingerabdruck mit,
   wenn jemand das Kabel einsteckt).
3. Nicht gefunden: Gerät anlegen, `first_seen = jetzt`.

**Antwort**

```json
{
  "status": "trial",
  "trialStartedAt": 1789000000,
  "serverTime": 1789642179,
  "ticket": ""
}
```

| Feld | Bedeutung |
|------|-----------|
| `status` | `trial`, `active` oder `blocked`. Unbekannte Werte behandelt die App als `trial` — ein Programm, das sich wegen eines Wortes sperrt, das es nicht kennt, geht beim nächsten Server-Update kaputt. |
| `trialStartedAt` | Wann der Server dieses Gerät zum ersten Mal gesehen hat, in Sekunden. Die App nimmt immer das **frühere** von Server- und eigenem Datum — Neuinstallieren verlängert die Testphase also nicht. |
| `serverTime` | Deine Uhr. Die App geht nie hinter den zuletzt gesehenen Serverzeitpunkt zurück, damit Uhr-Zurückstellen nichts bringt. |
| `ticket` | Das unterschriebene Ticket, sobald bezahlt wurde. Sonst leer. |

## Endpunkt 2 — `POST /api/license/redeem`

Für alle, die den QR-Code nicht scannen konnten und einen Code per E-Mail bekommen haben.

```json
{ "code": "K7M2-9QX4", "device": "A1B2C3D4E5F6", "ids": ["a1b2c3d4e5f6", "…"] }
```

Antwort wie bei `hello`. Ist der Code gültig und noch nicht eingelöst: an dieses Gerät
binden, als eingelöst markieren, Ticket ausstellen. Sonst `status: "trial"` und kein
Ticket — die App sagt dann „Hat nicht geklappt. Stimmt der Code?".

## Endpunkt 3 — die Kaufseite

`GET /aktivieren?d=A1B2C3D4E5F6`

Die Adresse steckt im QR-Code auf dem Fernseher. Die Seite zeigt die Kennung zur
Kontrolle, den Preis, und führt zu Stripe oder PayPal.

**Wichtig für die Zahlungsanbieter:** Auf dieser Seite wird *Software* verkauft — ein
TV-Player. Keine Senderlisten, keine Logos, keine Anbieternamen. Stripe und PayPal
sperren Konten, wenn ihre Prüfer den Eindruck gewinnen, es gehe um Zugang zu fremden
Inhalten.

**Ebenfalls auf die Seite:** die Checkbox, mit der der Käufer ausdrücklich zustimmt,
dass die Leistung sofort erbracht wird und er damit sein 14-tägiges Widerrufsrecht
verliert. Ohne sie kann jeder nach dreizehn Tagen Nutzung das Geld zurückverlangen.

## Endpunkt 4 — der Zahlungs-Webhook

`POST /api/payment/webhook` — von Stripe beziehungsweise PayPal.

Unterschrift des Webhooks prüfen (bei Stripe `Stripe-Signature`), Gerät anhand der im
Checkout mitgegebenen Kennung suchen, Ticket ausstellen, `status = active` setzen.
Beim nächsten `hello` — also spätestens zehn Sekunden später — schaltet der Fernseher
von selbst frei.

## Die Datenbank

Drei Tabellen reichen:

```sql
CREATE TABLE devices (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(16) NOT NULL,
  first_seen  DATETIME    NOT NULL,
  last_seen   DATETIME    NOT NULL,
  model       VARCHAR(64),
  version     VARCHAR(16),
  status      ENUM('trial','active','blocked') NOT NULL DEFAULT 'trial',
  order_ref   VARCHAR(64),
  ticket      TEXT,
  UNIQUE KEY (code)
);

CREATE TABLE device_ids (
  device_id  INT         NOT NULL,
  id_hash    VARCHAR(32) NOT NULL,
  PRIMARY KEY (id_hash),
  KEY (device_id)
);

CREATE TABLE redeem_codes (
  code       VARCHAR(16) PRIMARY KEY,
  created_at DATETIME NOT NULL,
  used_at    DATETIME NULL,
  device_id  INT NULL
);
```

`device_ids` ist die Tabelle, auf die es ankommt: Ein Gerät hat mehrere Kennungen, und
gesucht wird über **jede** davon. Deshalb liegt der Primärschlüssel auf `id_hash`.

## Was das Panel an Oberfläche braucht

- Geräteliste mit Status, Modell, Version, zuletzt gesehen
- Gerät manuell freischalten — für Barzahlung im Laden
- Lizenz auf ein anderes Gerät übertragen, zwei Mal pro Jahr in Selbstbedienung.
  Ohne das wird jeder kaputte Fernseher zu einem Anruf bei dir, und der frisst die
  9,99 € mehrfach auf.
- Einlösecodes erzeugen
- Sperren, bei Rückbuchung

## Was die App macht, wenn nichts eingetragen ist

Nichts. Ohne Lizenz-Adresse gibt es keine Anfrage, und die Testphase läuft rein lokal.
Das ist der Zustand, in dem die App ausgeliefert wird, bis der Server steht.
