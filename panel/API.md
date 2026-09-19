# Panel-Schnittstelle

Die App kann sich später bei einem Panel melden und von dort ihren Zugang
bekommen. Damit muss niemand mehr Server, Benutzer und Passwort abtippen — der
Fernseher bekommt einen Code, und der Rest passiert von allein.

Dieses Dokument beschreibt, was das Panel können muss. In der App ist die Seite
*Einstellungen → Panel* bereits da; solange dort keine Adresse steht, passiert
**nichts**: keine Anfrage, keine Kennung, kein Datenverkehr.

## Ein einziger Aufruf

```
POST <panel-adresse>/device/hello
Content-Type: application/json
```

### Was der Fernseher schickt

```json
{
  "deviceId": "8f3c1a2b4d5e6f70",
  "code": "ABCD-1234",
  "app": "karacast",
  "versionCode": 12,
  "versionName": "1.8.1",
  "model": "TCL Smart TV Pro",
  "androidSdk": 34,
  "language": "de"
}
```

| Feld         | Bedeutung |
|--------------|-----------|
| `deviceId`   | 16 Zeichen, beim ersten Bedarf zufällig erzeugt und danach fest. Kein Gerätekennzeichen des Herstellers, keine Person dahinter — nur damit das Panel denselben Fernseher wiedererkennt. |
| `code`       | Der Code, den jemand im Panel für diesen Fernseher erzeugt und am TV eintippt. Leer, solange keiner eingetragen wurde. |
| `versionCode`| Damit das Panel weiß, ob dieser Fernseher ein Update braucht. |
| `model`      | Hersteller und Modell, für die Geräteliste im Panel. |
| `language`   | `de`, `tr`, `en` oder `ar` — für Meldungen in der richtigen Sprache. |

### Was das Panel antwortet

Alles ist optional. Ein Panel, das nur Zugänge verteilt, schickt nur `source`;
eines, das nur Versionen ankündigt, nur `update`. Felder, die die App nicht
kennt, überliest sie — so kann das Panel wachsen, ohne dass vorher auf jedem
Fernseher eine neue App installiert werden muss.

```json
{
  "status": "ok",
  "deviceName": "Wohnzimmer Oma",
  "source": {
    "type": "xtream",
    "name": "Familie Karatas",
    "url": "http://server.example:8080",
    "username": "…",
    "password": "…",
    "epgUrl": ""
  },
  "update": {
    "versionCode": 13,
    "versionName": "1.9.0",
    "url": "https://github.com/memo1907fener/karacast/releases/download/v1.9.0/KaracastIPTV-1.9.0.apk",
    "notes": "Was neu ist."
  },
  "message": {
    "id": "m7",
    "title": "Wartung",
    "text": "Heute Nacht von 2 bis 4 Uhr ist der Server kurz weg."
  }
}
```

| Feld         | Bedeutung |
|--------------|-----------|
| `deviceName` | Wie das Panel diesen Fernseher nennt. Erscheint in den Einstellungen. |
| `source`     | Der Zugang. `type` ist `xtream` oder `m3u`; bei `m3u` steht die Playlist-URL in `url` und `username`/`password` bleiben leer. |
| `update`     | Dieselben vier Felder wie in `update/karacast.json`. Ein Panel kann Updates also gezielt pro Gerät ausrollen statt für alle gleichzeitig. |
| `message`    | Eine Meldung. `id` sorgt dafür, dass sie nur einmal gezeigt wird. |

**Kennt das Panel den Code nicht**, antwortet es trotzdem mit `200` und lässt
`source` weg. Die App sagt dann „Das Panel hat keinen Zugang für diesen Code" —
das ist ehrlicher als ein Fehler, denn erreichbar war der Server ja.

## Was die App damit macht

- Der Zugang wird **angeboten, nicht angewendet**. In den Einstellungen erscheint
  ein Knopf „Zugang übernehmen". Ein Fernseher, der sich still auf einen anderen
  Anbieter umstellt, weil ein Server das gesagt hat, wäre eine böse Überraschung.
- Eine `update`-Angabe landet auf demselben Weg wie die aus `karacast.json`:
  angezeigt, heruntergeladen erst nach einem Ja, installiert von Android nach
  einer weiteren Rückfrage.
- Gescheitert wird sichtbar: „Das Panel war nicht erreichbar". Eine Familie, der
  automatische Einrichtung versprochen wurde, hat ein Recht darauf zu erfahren,
  wenn sie nicht stattgefunden hat.

## Bitte https

Über `http://` wandern Benutzername und Passwort im Klartext durchs Netz —
genau die Daten, für die es diese Schnittstelle überhaupt gibt. Die App sagt das
auf der Panel-Seite auch deutlich, solange die Adresse mit `http://` beginnt.
Für den Anfang zum Ausprobieren in Ordnung, für den Dauerbetrieb nicht.
