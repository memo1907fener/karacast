# Updateprüfung

Karacast hat keinen Store hinter sich. Damit die App trotzdem sagen kann
„es gibt was Neues", fragt sie **eine einzige kleine Datei** im Netz ab —
`karacast.json`. Mehr passiert nicht: die App lädt nichts herunter und
installiert nichts von allein, sie zeigt nur an, dass eine neuere Version da ist,
und nennt die Adresse.

## Die Datei

```json
{
  "versionCode": 16,
  "versionName": "1.9.3",
  "url": "https://github.com/memo1907fener/karacast/releases/download/v1.9.3/KaracastIPTV-1.9.3.apk",
  "notes": "Was in dieser Version neu ist."
}
```

| Feld          | Bedeutung                                                                 |
|---------------|---------------------------------------------------------------------------|
| `versionCode` | Die Zahl aus `app/build.gradle.kts`. **Nur wenn sie größer ist** als die installierte, meldet sich die App. |
| `versionName` | Was dem Nutzer angezeigt wird, z. B. `1.9.3`.                              |
| `url`         | Wo das APK liegt.                                                          |
| `notes`       | Ein bis zwei Sätze, was neu ist. Darf leer sein.                           |

Diese Datei wird seit Version 1.9.3 **nicht mehr von Hand gepflegt**. Der
Veröffentlichungslauf auf GitHub schreibt sie selbst um — siehe unten.

## Einmal einrichten

### 1. Repository

GitHub Desktop → *File → New repository* → Name `karacast`. Sichtbarkeit:
**Public** — bei *Private* kann der Fernseher die Datei nicht lesen, weil er
sich nicht anmelden kann. Ordner hineinlegen und pushen. Der Signaturschlüssel
bleibt dabei außen vor: `keystore.properties` und `*.jks` stehen in der
`.gitignore`.

### 2. Adresse in der App eintragen

Auf dem Fernseher: *Einstellungen → Updates → Update-Adresse*:

```
https://raw.githubusercontent.com/memo1907fener/karacast/main/update/karacast.json
```

`raw.githubusercontent.com` ist wichtig — die normale `github.com`-Adresse
liefert eine Webseite, kein JSON, und die App kann damit nichts anfangen.

### 3. Den Signaturschlüssel bei GitHub hinterlegen

Damit GitHub das APK **mit demselben Schlüssel** signieren kann wie bisher.
Nur so lässt sich ein Update über eine bestehende Installation legen; ein
fremd signiertes APK verweigert Android.

Zuerst den Schlüssel in Text verwandeln. In der PowerShell, im Projektordner:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\safir-release.jks")) | Set-Clipboard
```

Der Inhalt liegt danach in der Zwischenablage. Dann auf GitHub:
*Settings → Secrets and variables → Actions → New repository secret*, und
vier Stück anlegen:

| Name                | Inhalt                                             |
|---------------------|----------------------------------------------------|
| `KEYSTORE_BASE64`   | das eben Kopierte (einfach einfügen)               |
| `KEYSTORE_PASSWORD` | `storePassword` aus `keystore.properties`          |
| `KEY_ALIAS`         | `safir`                                            |
| `KEY_PASSWORD`      | `keyPassword` aus `keystore.properties`            |

Diese vier Werte sieht danach niemand mehr, auch du nicht — GitHub zeigt sie
nie wieder an, sondern nur, dass es sie gibt. Sie gehören ausschließlich
dorthin: nicht in eine Datei im Repository, nicht in einen Chat.

## Bei jeder neuen Version

1. `versionCode` und `versionName` in `app/build.gradle.kts` hochzählen
   (`versionCode` **muss** größer werden, `versionName` ist der Text).
2. Pushen.
3. Auf GitHub: *Actions → Veröffentlichen → Run workflow*. Ins Feld „Was ist
   neu?" ein, zwei Sätze schreiben — genau die stehen später am Fernseher.

Der Lauf erledigt dann alles Weitere:

* baut das Release-APK und signiert es mit deinem Schlüssel,
* prüft, dass es **nicht** als `testOnly` markiert und nicht mit dem
  Debug-Schlüssel signiert ist (beides waren schon Fehlerquellen: so ein APK
  lässt sich von Hand nicht installieren),
* legt das Release `v1.9.3` an und hängt `KaracastIPTV-1.9.3.apk` daran,
* schreibt `update/karacast.json` auf `main` um.

Danach meldet jeder Fernseher beim nächsten *Jetzt prüfen*, dass etwas Neues da
ist. Das APK selbst muss weiterhin bestätigt werden — Android lässt eine App
sich nicht ohne Zustimmung selbst ersetzen.

Wer lieber mit Tags arbeitet: ein Tag `v1.9.3` pushen löst denselben Lauf aus.
Der Tag muss zur `versionName` passen, sonst bricht er ab.

### Wenn der Lauf rot wird

| Meldung | Was zu tun ist |
|---|---|
| „versionCode … ist nicht größer als der veröffentlichte" | Version in `app/build.gradle.kts` vergessen hochzuzählen. |
| „Das Secret KEYSTORE_BASE64 fehlt" | Schritt 3 oben nachholen. |
| „Tag …, aber app/build.gradle.kts sagt …" | Version und Tag stimmen nicht überein. |
| „Mit dem Debug-Schlüssel signiert" | Das Secret enthält nicht den richtigen Schlüssel, oder Passwort/Alias passen nicht. |

## Achtung: der Schlüssel

Alle Versionen müssen mit **demselben** Schlüssel signiert sein
(`keystore/safir-release.jks`, Alias `safir`). Geht diese Datei verloren, kann
nie wieder ein Update über eine bestehende Installation gelegt werden — jeder
müsste die App löschen und neu einrichten. Eine Kopie an einen zweiten Ort
legen, der nicht dieser Rechner ist.
