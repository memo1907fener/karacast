# Safir IPTV — Android TV / Google TV Player

Ein nativer IPTV-Player für Google TV, Android TV und TV-Boxen. Kotlin, Jetpack
Compose for TV, Media3/ExoPlayer. Die App bringt **keine** Inhalte mit — sie
spielt die Playlist ab, die du selbst einträgst.

## Was drin ist (v1.0 — MVP)

- **Xtream Codes** Login (Server + Benutzer + Passwort) über `player_api.php`
- **M3U / M3U8** Playlists per URL, inkl. `tvg-id`, `tvg-logo`, `group-title`
- **XMLTV EPG**: eigene URL oder automatisch die `xmltv.php` des Xtream-Panels,
  gzip wird erkannt, gefiltert auf die Kanäle deiner Playlist
- Kanalbrowser mit Kategorien-Sidebar, Vorschaupanel und Jetzt/Gleich-Anzeige
- Player mit D-Pad-Steuerung: ▲▼ zappen, ◀ Kanalliste, OK Info-Leiste,
  Menü-Taste setzt Favorit
- Favoriten, „Zuletzt gesehen", Kanalsuche
- TV-Programm-Ansicht pro Kanal
- Einstellungen: Playlist/EPG neu laden, HLS statt MPEG-TS, Puffergröße,
  User-Agent (VLC / FFmpeg / OkHttp), Verbindung trennen

Noch **nicht** drin (bewusst, als nächste Ausbaustufe): VOD-/Serien-Katalog,
Mehrbenutzer-Profile, Kindersicherung, Catch-up/Archiv, Aufnahme.

## Bauen

### Mit Android Studio (empfohlen)

1. Android Studio öffnen → **Open** → diesen Ordner wählen
2. Gradle-Sync abwarten (lädt beim ersten Mal ~1 GB Abhängigkeiten)
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**

Die APK liegt danach hier:

```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

### Auf der Kommandozeile

```bash
./gradlew assembleDebug      # zum Testen
./gradlew assembleRelease    # signiert, für den Dauereinsatz
```

Voraussetzung: JDK 17 und ein Android SDK mit API 35
(`ANDROID_HOME` gesetzt oder `local.properties` mit `sdk.dir=...`).

### Über GitHub Actions

`.github/workflows/build-apk.yml` baut bei jedem Push Debug- und Release-APK und
hängt sie als Artefakt an den Workflow-Lauf. Nützlich, wenn du kein Android
Studio installieren willst.

## Signierung

`keystore.properties` und `keystore/safir-release.jks` liegen bei, damit
`assembleRelease` sofort durchläuft. **Ersetze diesen Schlüssel, bevor du die App
an andere weitergibst** — wer die Datei hat, kann Updates unter deiner App-ID
veröffentlichen:

```bash
keytool -genkeypair -v -keystore keystore/mein-key.jks -alias meinalias \
  -keyalg RSA -keysize 2048 -validity 10000
```

Danach die vier Werte in `keystore.properties` anpassen. Fehlt die Datei, wird
der Release-Build mit dem Debug-Key signiert — er läuft dann zwar, taugt aber
nicht für den Play Store.

## Auf die Box bringen

```bash
adb connect 192.168.1.50:5555        # IP der Box, ADB-Debugging vorher aktivieren
adb install -r app/build/outputs/apk/release/app-release.apk
```

Alternativ die APK auf einen USB-Stick kopieren und mit einem Dateimanager auf
der Box installieren, oder über „Downloader" per URL laden.

Die App meldet sich mit `LEANBACK_LAUNCHER` an und erscheint damit auf dem
Google-TV-Homescreen; `@drawable/app_banner` (320×180) ist das Kachelbild.

## Aufbau des Codes

```
data/
  local/        Room: Quelle, Kategorien, Kanäle, Favoriten, EPG
  remote/
    xtream/     player_api.php-Client, tolerant gegenüber Panel-Eigenheiten
    m3u/        Streaming-Parser für erweiterte M3U-Playlists
    epg/        XMLTV-Pull-Parser, gefiltert und speicherschonend
  repository/   PlaylistRepository (Sync) und EpgRepository (Guide)
ui/
  login/        Einrichtung Xtream / M3U
  channels/     Kanalbrowser mit Sidebar und Vorschau
  player/       ExoPlayer, Info-Leiste, Zapping, Kanalliste-Overlay
  guide/        TV-Programm
  settings/     Wartung und Wiedergabe-Optionen
```

Kein DI-Framework: `di/AppContainer` verdrahtet alles von Hand, die ViewModels
kommen aus einer einzigen `viewModelFactory`.

## Wenn ein Kanal nicht läuft

In dieser Reihenfolge durchgehen — das deckt fast alles ab:

1. **Einstellungen → HLS statt MPEG-TS**, danach Playlist neu laden. Manche
   Panels liefern über `.ts` nur einen Stummel, über `.m3u8` den vollen Stream.
2. **User-Agent wechseln.** Viele Anbieter filtern danach; VLC ist die sicherste
   Wahl, manche Panels wollen `Lavf/...`.
3. **Puffer erhöhen** (12–16 s), wenn das Bild nach Sekunden einfriert.
4. „Zu viele Verbindungen" heißt fast immer: ein anderes Gerät läuft noch mit
   demselben Konto.

## Rechtliches

Die App ist ein reiner Player. Du brauchst ein eigenes Abo bei einem Anbieter
und bist dafür verantwortlich, dass die Inhalte, die du abspielst, legal sind.
Für den Play Store: Apps, die IPTV-Playlists abspielen, müssen ohne
vorkonfigurierte Kanäle ausgeliefert werden — genau so ist diese hier gebaut.
