# Karacast Panel

Der Lizenzserver. Er stellt unterschriebene Tickets aus, mit denen sich Fernseher
dauerhaft freischalten — auch dann noch, wenn dieser Server irgendwann nicht mehr
läuft. Das ist Absicht: geprüft wird die Unterschrift, nicht die Erreichbarkeit.

## Was der Server können muss

* **PHP 8.1 oder neuer** — `match`, `never` als Rückgabetyp und `str_contains`
  werden benutzt; auf PHP 8.0 lädt das Panel nicht.
* **MySQL 5.7 / MariaDB 10.2 oder neuer** (wegen `utf8mb4` und InnoDB in der
  vorausgesetzten Form).
* **Die OpenSSL-Erweiterung**, mit einer brauchbaren `openssl.cnf`. Ohne sie lässt
  sich kein Schlüsselpaar erzeugen; install.php sagt es dir dann deutlich.
* **`mod_rewrite`** ist angenehm, aber nicht nötig — ohne ihn trägst du in der App
  `…/api/index.php` statt `…/api` ein, der Rest funktioniert unverändert.

## Einrichten — sechs Schritte

**1. Datenbank anlegen.** Im Hosting-Panel eine leere MySQL-Datenbank erstellen und
Name, Benutzer und Passwort notieren.

**2. Dateien hochladen.** Diesen ganzen Ordner per FTP in das Verzeichnis deiner
Domain legen, also so, dass `https://karacast.de/api/...` auf `api/` zeigt.

**3. config.php anlegen.** `config.example.php` zu `config.php` kopieren und die
Werte eintragen — vor allem die Datenbankdaten und `base_url` (deine Domain, ohne
Schrägstrich am Ende). Diese Datei sieht niemand außer dir; sie wird auch von der
mitgelieferten `.htaccess` blockiert, falls PHP einmal aussetzt.

**4. `https://karacast.de/install.php` aufrufen.** Dort vergibst du ein Passwort für
das Panel. Das Skript legt die Tabellen an und erzeugt das Schlüsselpaar.

**5. Den öffentlichen Schlüssel in die App übertragen.** install.php zeigt ihn nach
dem Einrichten an. Er kommt in
`app/src/main/java/com/safir/iptv/util/LicenseVerifier.kt` → `PUBLIC_KEY_BASE64`.
Danach die App neu bauen. Ohne diesen Schritt erkennt die App die Tickets nicht,
weil sie noch den Entwicklungsschlüssel trägt.

**6. install.php vom Server löschen.** Sie ist danach nur noch eine Angriffsfläche.

> **Wenn nach dem Hochladen gar nichts mehr geht** (Endlosschleife oder Fehler 500):
> Die mitgelieferte `.htaccess` leitet jeden Aufruf auf `https://` um. Hat deine
> Domain noch kein Zertifikat, klammere die vier `RewriteCond`/`RewriteRule`-Zeilen
> im HTTPS-Block unten in der Datei mit `#` aus — und hol sie wieder herein, sobald
> das Zertifikat läuft. Über `http://` geht dein Panel-Passwort im Klartext durchs
> Netz.

## Eigene Geräte freischalten

1. Am Fernseher unter *Einstellungen → Lizenz* als Lizenz-Adresse eintragen:
   `https://karacast.de/api` — dann *Jetzt prüfen*.
2. Der Fernseher erscheint sofort im Panel unter *Geräte*.
3. Öffnen, Vermerk eintragen (z. B. „eigenes Gerät"), **Dauerhaft freischalten**.
4. Am Fernseher noch einmal *Jetzt prüfen*. Fertig — ab jetzt für immer, auch ohne
   Internet.

Läuft `mod_rewrite` auf deinem Hosting nicht, trage stattdessen
`https://karacast.de/api/index.php` ein. Funktioniert genauso.

### Wenn du später einen neuen Fernseher bekommst

Im Panel beim alten Gerät *Auf ein anderes Gerät übertragen*. Das neue muss sich
vorher einmal gemeldet haben. Der alte Fernseher wird dabei **gesperrt** — sonst
stünde dieselbe Lizenz zweimal in der Welt. Sein Ticket im Speicher gilt zwar noch,
bis er das nächste Mal nachfragt; eine Unterschrift lässt sich offline nicht
zurücknehmen. Für ein Gerät, das du behalten willst, ist der Übertragen-Knopf also
der falsche: schalte es einfach separat frei.

## „Neue Kennungen warten"

Steht das bei einem freigeschalteten Gerät, hat sich jemand mit dem Code dieses
Fernsehers gemeldet und dabei eine Kennung mitgebracht, die das Panel nicht kennt.

Das wird **nicht** automatisch übernommen, und der Grund ist wichtig: Der
Gerätecode ist kein Geheimnis. Er steht auf dem Fernsehschirm, im QR-Code, auf der
Aktivierungsseite und in jeder Support-Mail. Würde eine einzige passende Kennung
genügen, um weitere anzuhängen, könnte jeder, der einen fremden Code abliest, seine
eigene Kennung an eine bezahlte Lizenz hängen und sich beim nächsten Ticket
mitnehmen lassen — unbemerkt, weil beim Bestohlenen alles weiterläuft. Ein echter
Fernseher weist sich immer mit **mehreren** Kennungen aus; die zweite steht
nirgends geschrieben.

Übernimm eine wartende Kennung also nur, wenn du weißt, warum sie dazugekommen ist:
App-Daten gelöscht, Gerät neu aufgesetzt, von WLAN auf Kabel gewechselt. Sonst
verwerfen. In der Testphase gilt die Regel nicht — dort gibt es nichts zu holen.

## Bezahlung mit Stripe einrichten

Vier Handgriffe. Solange `secret_key` in `config.php` leer ist, gibt es keinen
Bezahlknopf und die Seiten zeigen weiter den Kontaktweg — du kannst also gefahrlos
zuerst hochladen und danach in Ruhe einrichten.

**1. Testschlüssel holen.** Im Stripe-Dashboard oben den **Testmodus** einschalten,
dann *Entwickler → API-Schlüssel → Geheimer Schlüssel* (`sk_test_…`). In `config.php`
unter `stripe.secret_key` eintragen.

**2. Webhook anlegen.** *Entwickler → Webhooks → Endpunkt hinzufügen*, Adresse:

```
https://karacast.de/webhook.php
```

Diese drei Ereignisse abonnieren — mehr nicht:

```
checkout.session.completed
charge.refunded
charge.dispute.created
```

Stripe zeigt danach ein *Signing secret* (`whsec_…`). Das kommt in `config.php` unter
`stripe.webhook_secret`. **Ohne dieses Geheimnis nimmt webhook.php gar nichts an** —
und das ist Absicht: es ist das Einzige, was die Adresse davor schützt, dass jemand
„bezahlt" hineinschreibt.

**3. Testkauf.** Ein Gerät in der Testphase, auf `aktivieren.php?d=…`, Haken setzen,
kaufen. Stripes Testkarte: `4242 4242 4242 4242`, beliebiges künftiges Ablaufdatum,
beliebige Prüfziffer. Danach muss das Gerät im Panel auf *Freigeschaltet* stehen und
der Fernseher es binnen zehn Sekunden übernehmen.

**4. Scharf schalten.** Testmodus aus, denselben Weg noch einmal: `sk_live_…` und ein
neuer Webhook-Endpunkt mit eigenem `whsec_…`. Die Live-Werte sind andere als die
Testwerte — das ist der häufigste Fehler dabei.

### Was wo passiert

| Datei | Rolle |
|---|---|
| `checkout.php` | legt die Bezahlsitzung an und leitet zu Stripe weiter |
| `webhook.php` | Stripes Meldung „bezahlt" — der verlässliche Weg |
| `danke.php` | fragt nach der Rückkehr selbst bei Stripe nach — der schnelle Weg |
| `lib/stripe.php` | die drei Aufrufe und die Unterschriftsprüfung |

Freigeschaltet wird **nie** auf Zuruf des Browsers, sondern nur, wenn Stripe selbst
„paid" sagt und der Betrag stimmt. Webhook und Dankeseite laufen beide in dieselbe
Funktion; wer zuerst kommt, schaltet frei, der zweite findet es bereits erledigt.
Eine Erstattung oder Rückbuchung sperrt das Gerät automatisch.

Kartendaten berühren deinen Server nie: das Bezahlen findet vollständig auf Stripes
Seiten statt. Deshalb brauchst du für dieses Panel keine PCI-Zertifizierung.

### Umsatzsteuer

Bei digitalen Produkten an Verbraucher in der EU fällt die Steuer im Land des Käufers
an. Unter der Kleinunternehmerregelung ist das zunächst kein Thema; darüber hinaus
läuft es über das OSS-Verfahren in FinanzOnline. Stripe kann die Steuer auch selbst
berechnen (*Stripe Tax*, kostenpflichtig) — dann baue ich das ein. Sprich vorher mit
deiner Steuerberatung; das hier ist keine Steuerberatung.

## Updates einspielen — bitte so und nicht anders

**Nicht** die ZIP im Dateimanager des Hosters entpacken, und **nicht** per FTP
abgleichen lassen. Beides geht schief, und beides ist hier schon schiefgegangen:

* Der Dateimanager *mischt* keine Ordner. Findet er `admin/` bereits vor, legt er
  `admin.198` daneben — beim nächsten Mal `admin.4099`. Nach fünf Updates liegen
  fünfzehn veraltete, über das Internet erreichbare Kopien im Webverzeichnis.
* Ein FTP-Programm im Abgleichmodus löscht, was in der ZIP fehlt. In der ZIP fehlt
  mit Absicht `keys/private.pem` — und ohne den lässt sich keine Lizenz mehr
  ausstellen.

Der sichere Weg, drei Schritte:

1. Die ZIP **unentpackt** ins Hauptverzeichnis hochladen (dorthin, wo `index.php` liegt).
2. `https://karacast.de/einspielen.php` aufrufen und auf *Einspielen* drücken.
3. Auf derselben Seite unten: *Kopien löschen*, falls dort Ordner wie `admin.198`
   aufgelistet sind.

`config.php` und der Ordner `keys/` werden dabei nie angefasst — auch dann nicht,
wenn sie in der ZIP vorkämen.

## Eine ältere Fassung nachrüsten

Läuft auf dem Server schon ein Panel und du spielst nur die neuen Dateien darüber:
einmal `https://karacast.de/upgrade.php` aufrufen (nach der Anmeldung), dann die
Datei löschen. Sie legt ausschließlich an, was fehlt — Geräte, Lizenzen und der
Schlüssel bleiben unberührt. `config.php` und `keys/private.pem` werden beim
Hochladen nicht überschrieben, die sind in dieser ZIP gar nicht enthalten.

## Wenn du den öffentlichen Schlüssel noch einmal brauchst

Er steht im Panel oben auf der Geräteliste unter *Öffentlicher Schlüssel für die
App*. Du brauchst ihn jedes Mal, wenn du die App neu baust.

## Was drin ist

| Ort | Wozu |
|---|---|
| `index.php` | Die Startseite — was Karacast ist, was es kostet, wie man es bekommt |
| `status.php` | „Ist mein Gerät freigeschaltet?" — Selbstauskunft über die Gerätekennung |
| `aktivieren.php` | Die Seite, auf der der QR-Code vom Fernseher landet |
| `impressum.php`, `agb.php`, `datenschutz.php` | Die Pflichtseiten. **Entwürfe**, siehe unten. |
| `api/` | Die Schnittstelle für die Fernseher: `license/hello` und `license/redeem` |
| `admin/` | Geräteliste, Freischalten, Sperren, Übertragen, Einlösecodes |
| `lang/` | Die Texte der öffentlichen Seiten in Deutsch, Englisch, Türkisch |
| `lib/` | Datenbank, Ticket-Unterschrift, Anmeldung, Sprachen, Seitengerüst |
| `keys/` | Der private Schlüssel. Wird beim Einrichten erzeugt. |

## Die öffentlichen Seiten

Dreisprachig: Deutsch, Englisch, Türkisch. Die Sprache kommt aus der Adresse
(`?lang=tr`), sonst aus dem Cookie der letzten Wahl, sonst aus dem Browser, sonst
Deutsch. Fehlt ein Text in einer Sprache, erscheint der deutsche — eine Seite mit
Lücken wäre schlimmer. Texte ändern: in `lang/de.php`, dann in den anderen beiden.

Auf allen drei Verkaufsseiten wird ausdrücklich **Software** verkauft. Keine Sender,
keine Anbieternamen, keine Logos, keine Aussage über Inhalte. Bitte dabei bleiben,
auch wenn es verlockend ist: Stripe und PayPal prüfen genau das.

### Impressum, AGB, Datenschutz

Sind ausgefüllt — mit deinen Daten aus der App —, aber sie sind **Entwürfe**. Der
Datenschutztext beschreibt exakt das, was Panel und App heute tun. Zwei Dinge musst
du selbst erledigen:

1. Im Impressum die Zeilen in eckigen Klammern ausfüllen oder löschen (UID, Firmenbuch,
   Gewerbebehörde).
2. Alle drei vor dem Verkaufsstart von einer Anwältin oder einem Anwalt prüfen lassen.
   Das kostet einmal ein paar hundert Euro und ist billiger als die erste Abmahnung.

Und sobald Stripe dazukommt oder Playlists auf dem Server liegen, muss der
Datenschutztext mitwachsen — sonst stimmt er nicht mehr.

## Der private Schlüssel

`keys/private.pem` ist das Wertvollste hier. Wer ihn hat, kann Lizenzen für fremde
Geräte ausstellen. Er wird beim Einrichten erzeugt, bekommt `chmod 600` und liegt
hinter einer `.htaccess`, die den Zugriff verweigert.

**Prüf das einmal von Hand**: `https://karacast.de/keys/private.pem` im Browser
aufrufen. Es muss ein Fehler kommen. Kommt Text, liegt der Schlüssel offen — dann
die Datei sofort über das Webverzeichnis hinaus verschieben und den neuen Pfad in
`config.php` unter `key_path` eintragen.

Geht der Schlüssel verloren, lassen sich **keine neuen** Lizenzen mehr ausstellen.
Bereits verkaufte laufen weiter, weil sie nicht nachgeprüft werden müssen. Trotzdem:
eine Kopie an einen zweiten Ort, der nicht dieser Server ist.

## Was noch fehlt

Stripe und PayPal. `aktivieren.php` zeigt vorerst die Kennung, den Preis und den
Kontaktweg; das Freischalten passiert von Hand im Panel oder über einen Einlösecode.
Sobald die Zahlungsanbieter dazukommen, wird aus dem Kontaktblock ein Bezahlknopf —
alles darum herum bleibt, wie es ist.

Für den Verkauf gehört dann außerdem auf die Seite: Impressum, AGB, und die
Checkbox, mit der der Käufer ausdrücklich zustimmt, dass die Leistung sofort
erbracht wird und er damit sein vierzehntägiges Widerrufsrecht verliert.

## Eine Bitte in eigener Sache

Auf `aktivieren.php` wird **Software** verkauft — ein Abspielprogramm. Keine Sender,
keine Anbieternamen, keine Logos. Nicht aus Zimperlichkeit: Stripe und PayPal sperren
Konten, wenn ihre Prüfer den Eindruck gewinnen, es gehe um Zugang zu fremden
Inhalten. Diese Trennung entscheidet darüber, ob dein Zahlungskonto in drei Monaten
noch offen ist.
