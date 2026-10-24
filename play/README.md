# Karacast im Google Play Store

Es gibt **zwei Apps**, beide aus demselben Code:

- **Karacast IPTV** (`app/`) — für Fernseher, TV-Boxen und Tablets.
- **Karacast Mobil** (`mobile/`) — für Handys und Tablets. Siehe Abschnitt 8.

Jede App gibt es in zwei Fassungen:

| | **direct** (APK) | **play** (Google Play) |
|---|---|---|
| Paketname | `com.safir.iptv` | `com.sternweb.karacast` |
| Updates | eigene, über GitHub | über den Play Store |
| Bezahlen | karacast.de (QR-Code) | Google Play Billing |
| Eigener Server | Lizenz + Panel | keiner |
| Bauen | `./gradlew assembleDirectRelease` | `./gradlew bundlePlayRelease` |

Das Bundle für Google liegt danach unter
`app/build/outputs/bundle/playRelease/app-play-release.aab`.

---

## 1. Upload-Schlüssel — sichern!

Die Play-Fassung wird mit einem **eigenen Upload-Schlüssel** signiert:

- `keystore/karacast-play-upload.jks`
- `keystore-play.properties` (Passwort)

Beide liegen **nur auf diesem Rechner** und sind von Git ausgeschlossen.
Kopiere sie auf einen USB-Stick oder in einen Passwort-Manager. Geht der
Schlüssel verloren, ist das kein Weltuntergang (Play Console → *App-Integrität*
→ *Upload-Schlüssel zurücksetzen*), kostet aber ein paar Tage.

Beim ersten Upload **„Play App Signing“ aktiviert lassen** (Standard): Google
verwaltet dann den eigentlichen App-Schlüssel.

---

## 2. App in der Play Console anlegen

1. *App erstellen* → Name **Karacast IPTV**, Standardsprache Deutsch, **App**,
   **Kostenlos** (gekauft wird in der App), Erklärungen bestätigen.
2. *Testen und veröffentlichen → Produktion → Erweiterte Einstellungen →
   Formfaktoren* → **Android TV** hinzufügen. Google prüft die TV-Fassung
   zusätzlich (Bedienung mit Fernbedienung, Banner, kein Touch-Zwang) — das
   erfüllt die App.
3. Bundle hochladen (zuerst in einen **internen Test**, siehe Punkt 5).

## 3. Produkte anlegen (Preise bestimmst du hier)

Die App liest Namen und Preise direkt von Google — im Code steht kein Preis.
Die **Produkt-IDs müssen exakt so heißen**:

**Einmalkauf** — *Monetarisieren → Produkte → In-App-Produkte*
- Produkt-ID: `karacast_lifetime`
- Name: z. B. „Karacast für immer“
- Preis: frei wählbar → *Aktivieren*

**Abo** — *Monetarisieren → Produkte → Abos*
- Produkt-ID: `karacast_abo`
- Darunter beliebige **Basis-Abos**, z. B. `monatlich` (1 Monat) und
  `jaehrlich` (1 Jahr), jeweils mit Preis → *Aktivieren*
- Die App zeigt automatisch jedes aktive Basis-Abo als eigenen Knopf
  („Monatsabo 2,99 € / Monat“, „Jahresabo 19,99 € / Jahr“).
- Optional ein Angebot mit kostenloser Testphase — die App erkennt das und
  schreibt „Mit kostenloser Testphase“ dazu.

Die 14 Tage Testphase der App gelten zusätzlich wie bisher.

Preise später ändern: einfach in der Console. Kein neues Update nötig.

## 4. Pflichtangaben (*App-Inhalte*)

- **Datenschutzerklärung**: `datenschutz.html` aus diesem Ordner öffentlich
  hochladen (z. B. `https://karacast.de/datenschutz`) und die Adresse eintragen.
  Vorher die Platzhalter `[…]` ausfüllen.
- **Datensicherheit**: siehe Abschnitt unten.
- **Werbung**: Nein.
- **App-Zugriff**: „Alle Funktionen ohne besonderen Zugriff verfügbar“ —
  *aber*: ohne eigenen IPTV-Zugang sieht der Prüfer nur den Login. Besser:
  „Zugriff eingeschränkt“ wählen und **Test-Zugangsdaten** eines legalen,
  frei verfügbaren Streams angeben (z. B. eine öffentliche M3U-Liste mit
  freien Sendern wie den offiziellen Streams öffentlich-rechtlicher Sender).
- **Einstufung**: Fragebogen ausfüllen; die App selbst enthält keine Inhalte.
- **Zielgruppe**: 18+ bzw. „nicht für Kinder“.
- **Nachrichten-App / Behörden-App / Finanzfunktionen**: jeweils Nein.

### Datensicherheit — die Antworten

- *Erhebt oder teilt die App Nutzerdaten?* → **Nein.**
  Begründung: Alle Einstellungen, Senderlisten und Zugangsdaten bleiben auf
  dem Gerät. Die App verbindet sich nur mit dem IPTV-Anbieter, den der Nutzer
  selbst einträgt. Zahlungen wickelt Google Play ab (zählt nicht als
  Erhebung durch die App).
- *Werden Daten bei der Übertragung verschlüsselt?* → Nein, nicht durchgängig
  (viele IPTV-Anbieter senden nur über `http://`). Ehrlich angeben.
- *Können Nutzer das Löschen beantragen?* → Nicht zutreffend (keine Daten
  beim Entwickler); Löschen = App-Daten löschen oder deinstallieren.

## 5. Testen, bevor es öffentlich wird

1. **Interner Test**: Bundle hochladen, dich selbst als Tester eintragen,
   über den Einladungslink installieren. Hier kannst du die Käufe ausprobieren.
2. *Einstellungen → Lizenztests* → deine Gmail-Adresse eintragen: dann
   kosten Testkäufe nichts, und Abos laufen im Minutentakt ab.
3. Falls dein Entwicklerkonto ein **privates Konto nach Nov. 2023** ist:
   Google verlangt einen **geschlossenen Test mit mindestens 12 Testern über
   14 Tage**, bevor „Produktion“ freigeschaltet wird. Bei einem
   Organisationskonto entfällt das.

## 6. Worauf Google bei IPTV-Apps achtet

IPTV-Player werden oft abgelehnt. So bleibt Karacast auf der sicheren Seite:

- In Texten und Bildern **keine Sendernamen, Senderlogos, Filmplakate oder
  Sportligen**. Screenshots mit neutralen Testsendern / eigenen Bildern.
- Keine Formulierungen wie „tausende Sender“, „gratis Fernsehen“, „alle
  Bundesliga-Spiele“.
- Deutlich sagen: **„Karacast enthält keine Inhalte. Du brauchst einen
  eigenen Zugang bei einem Anbieter.“** (steht in den Texten unten schon).
- Keine Links oder Empfehlungen zu IPTV-Anbietern — auch nicht auf
  karacast.de, solange die Seite in der Store-Beschreibung verlinkt ist.

## 7. Ein Update veröffentlichen

1. `versionCode` und `versionName` in `app/build.gradle.kts` hochzählen.
2. `./gradlew bundlePlayRelease`
3. In der Play Console neuen Release anlegen, `.aab` hochladen, Notiz
   schreiben, einreichen.

Die APK-Fassung wird wie bisher über den Tag (`v1.9.x`) auf GitHub
veröffentlicht — beide können dieselbe Versionsnummer tragen.

---

## 8. Karacast Mobil — die Handy-App

Eine **eigene App** in der Play Console, mit eigenem Eintrag und eigenen
Produkten. Ein Kauf in der Handy-App schaltet den Fernseher nicht frei und
umgekehrt.

| | TV-App | Handy-App |
|---|---|---|
| Paketname (Play) | `com.sternweb.karacast` | `com.sternweb.karacast.mobile` |
| Paketname (APK) | `com.safir.iptv` | `com.safir.iptv.mobile` |
| Geräte im Play Store | Fernseher + Tablets | Handys + Tablets |
| Update-Datei (APK) | `update/karacast.json` | `update/karacast-mobile.json` |
| Veröffentlichen (APK) | Tag `v1.2.3` | Tag `mobile-v1.2.3` |

**Wer was sieht:**
- Die TV-App meldet sich im Play Store nur für große Bildschirme an
  (`supports-screens` in `app/src/play/AndroidManifest.xml`). Handys sehen sie
  nicht.
- Die Handy-App meldet kein Android TV und verlangt einen Touchscreen —
  Fernseher sehen sie nicht.
- Feiner schneiden: Play Console → *Release → Gerätekatalog*, dort einzelne
  Geräte ausschließen.
- Die APKs lassen sich überall installieren. Darum prüft jede App beim Start
  das Gerät und schickt auf dem falschen zur richtigen App (TV-App auf einem
  Handy → „Karacast Mobil holen“).

**In der Play Console:**
1. *App erstellen* → Name **Karacast Mobil**, Deutsch, App, Kostenlos.
   Formfaktoren: nur Telefon/Tablet (kein Android TV hinzufügen).
2. Produkte — **IDs exakt so**, wie bei der TV-App:
   - In-App-Produkt `karacast_lifetime` → **29,99 €** → Aktivieren
   - Abo `karacast_abo` → Basis-Abo `monatlich`, 1 Monat, **1,99 €** → Aktivieren
   Die App zeigt die Preise so, wie Google sie liefert; im Code steht keiner.
3. Bundle bauen: GitHub → *Actions* → **Play-Bundles bauen** → *Run workflow*
   → „handy“. Das `.aab` unter *Artifacts* herunterladen und in einen
   **internen Test** hochladen. (Einmalig die vier `PLAY_…`-Secrets anlegen,
   siehe Kopf von `.github/workflows/play-bundles.yml`.)
4. Datenschutz, Datensicherheit, Einstufung: dieselben Antworten wie bei der
   TV-App (Abschnitt 4). Screenshots im Hochformat vom Handy.
5. Privates Entwicklerkonto: auch für diese App gilt der geschlossene Test mit
   12 Testern über 14 Tage vor „Produktion“.

Die 14 Tage Testphase in der App gelten auch hier; danach erscheint die Seite
„Karacast freischalten“ mit Abo und Einmalkauf.
