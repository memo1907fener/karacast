package com.safir.iptv.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The languages the flag row on the dashboard offers.
 *
 * A flag is not a language, strictly — but on a television, four coloured
 * rectangles are recognised across the room in a way four words never are, and a
 * device that renders no flag emoji falls back to the two letters of the country
 * code, which is still exactly the right hint.
 */
enum class AppLanguage(val tag: String, val flag: String, val label: String) {
    DE("de", "🇩🇪", "Deutsch"),
    TR("tr", "🇹🇷", "Türkçe"),
    EN("en", "🇬🇧", "English"),
    AR("ar", "🇸🇦", "العربية");

    val isRtl: Boolean get() = this == AR

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: systemDefault()

        /** Follows the box's own language when the user has never chosen one. */
        private fun systemDefault(): AppLanguage =
            entries.firstOrNull { it.tag == Locale.getDefault().language } ?: DE
    }
}

/**
 * Every word the app says, in one place.
 *
 * German sits in the parameter defaults, so it is written once; the other three
 * languages are the same object with every field overridden. That keeps a missing
 * translation impossible to ship by accident — the compiler asks for each one —
 * and lets the ViewModels read the same strings as the screens without needing a
 * Context, which is what Android's own resource system would have demanded.
 */
data class Strings(

    // ------------------------------------------------------------- everywhere
    val back: String = "Zurück",
    val home: String = "Start",
    val search: String = "Suche",
    val guide: String = "Programm",
    val settings: String = "Einstellungen",
    val channels: String = "Kanäle",
    val off: String = "Aus",
    val on: String = "An",
    val none: String = "keine",
    val live: String = "LIVE",

    // -------------------------------------------------------------- dashboard
    val liveTv: String = "Live TV",
    val liveTvCaption: String = "Alle Kanäle und Gruppen",
    val series: String = "Serien",
    val movies: String = "Filme",
    val comingSoon: String = "Bald verfügbar",
    val seriesLater: String = "Serien werden in einer späteren Version geladen.",
    val moviesLater: String = "Filme werden in einer späteren Version geladen.",

    // --------------------------------------------------------- channel lists
    val favorites: String = "Favoriten",
    val recentlyWatched: String = "Zuletzt gesehen",
    val allChannels: String = "Alle Kanäle",
    val searchChannel: String = "Kanal suchen",
    val typeName: String = "Name eintippen …",
    val noChannels: String = "Keine Kanäle",
    val emptyList: String = "Diese Liste ist leer. Mit Zurück kommst du zur Übersicht.",
    val noChannelSelected: String = "Kein Kanal ausgewählt",
    val nextUp: String = "Danach",
    val favorite: String = "Favorit",
    val jumpNow: String = "OK springt sofort",
    val switchNow: String = "OK schaltet sofort um",

    // ---------------------------------------------------------------- player
    val playerHint: String = "▲▼ Kanal · OK Liste · ◀▶ Info",
    val playbackFailed: String = "Wiedergabe fehlgeschlagen",
    val retry: String = "Erneut versuchen",
    val recording: String = "Aufzeichnung",
    val channelNotFound: String = "Kanal nicht gefunden.",
    val noArchive: String = "Für diese Quelle gibt es kein Archiv.",
    val errNetwork: String = "Keine Verbindung zum Stream. Netzwerk oder Server prüfen.",
    val errRejected: String =
        "Der Server hat den Stream abgelehnt. Oft heißt das: zu viele gleichzeitige Verbindungen.",
    val errNotFound: String = "Dieser Kanal ist beim Anbieter nicht verfügbar.",
    val errFormat: String =
        "Das Stream-Format wird nicht erkannt. In den Einstellungen HLS statt TS versuchen.",
    val errDecoder: String = "Der Decoder dieses Geräts kommt mit dem Stream nicht zurecht.",

    // -------------------------------------------------------- player options
    val options: String = "Optionen",
    val audioTrack: String = "Tonspur",
    val subtitles: String = "Untertitel",
    val aspect: String = "Bildformat",
    val sleepTimer: String = "Sleep-Timer",
    val backCloses: String = "Zurück schließt",
    val backOneLevel: String = "Zurück geht eine Ebene hoch",
    val nothingToChoose: String = "Für diesen Kanal gibt es hier nichts zu wählen.",
    val addFavorite: String = "Zu Favoriten hinzufügen",
    val removeFavorite: String = "Aus Favoriten entfernen",
    val aspectFit: String = "Anpassen",
    val aspectFill: String = "Ausfüllen",
    val aspectZoom: String = "Zoom",
    val aspectFitHint: String = "Ganzes Bild, ggf. Balken",
    val aspectFillHint: String = "Bild verzerrt auf den Rahmen",
    val aspectZoomHint: String = "Balken weg, Rand beschnitten",
    val stereo: String = "Stereo",
    val mono: String = "Mono",

    // ------------------------------------------------------------------ guide
    val tvGuide: String = "TV-Programm",
    val guideHint: String =
        "OK spielt den Kanal · vergangene Sendung mit Archiv startet die Aufzeichnung",
    val now: String = "Jetzt",
    val noGuideData: String = "keine Programmdaten",
    val loadingGuide: String = "Programm wird geladen …",
    val oneMoment: String = "Einen Moment.",
    val loadPlaylistFirst: String = "Lade zuerst die Playlist.",
    val archive: String = "Archiv",

    // ----------------------------------------------------------- with a value
    /** "%d Kanäle" */
    val nChannels: (Int) -> String = { "$it Kanäle" },
    /** "noch 8 Std 16 Min" */
    val stillRunning: (String) -> String = { "noch $it" },
    /** "Nr. 250 gibt es nicht" */
    val numberMissing: (Int) -> String = { "Nr. $it gibt es nicht" },
    /** "45 Minuten" */
    val nMinutes: (Int) -> String = { "$it Minuten" },
    /** "45 Min" */
    val nMinutesShort: (Int) -> String = { "$it Min" },
    /** "Spur 2" */
    val trackNumber: (Int) -> String = { "Spur $it" },
    val startingUp: String = "Karacast IPTV wird gestartet …",

    // --------------------------------------------------------------- settings
    val sourceSection: String = "Quelle",
    val name: String = "Name",
    val type: String = "Typ",
    val m3uPlaylist: String = "M3U-Playlist",
    val server: String = "Server",
    val lastLoaded: String = "Zuletzt geladen",
    val epgSection: String = "Programmzeitschrift",
    val available: String = "vorhanden",
    val noData: String = "keine Daten",
    val reloadPlaylist: String = "Playlist neu laden",
    val refreshEpg: String = "EPG aktualisieren",
    val editCredentials: String = "Zugangsdaten ändern",
    val playbackSection: String = "Wiedergabe",
    val useHls: String = "HLS statt MPEG-TS verwenden",
    val useHlsHint: String = "Hilft, wenn Kanäle nach ein paar Sekunden stehen bleiben. " +
        "Danach die Playlist neu laden.",
    val buffer: String = "Puffer",
    val bufferHint: String = "Mehr Puffer = stabiler, aber langsamerer Kanalwechsel.",
    val userAgent: String = "User-Agent",
    val displaySection: String = "Anzeige",
    val clockWhilePlaying: String = "Uhr während der Wiedergabe",
    val clockWhilePlayingHint: String = "Digitale Uhrzeit oben rechts, dauerhaft sichtbar.",
    val resumeLast: String = "Zuletzt gesehenen Kanal beim Start öffnen",
    val resumeLastHint: String = "Die App springt beim Öffnen direkt in die Wiedergabe. " +
        "Mit Zurück kommst du zur Kanalliste.",
    val previewWhileBrowsing: String = "Vorschau beim Blättern",
    val previewWhileBrowsingHint: String = "Der Kanal unter dem Fokus läuft rechts im Panel, " +
        "kurz verzögert, damit schnelles Blättern keine Verbindung aufbaut.",
    val previewMutedTitle: String = "Vorschau stumm",
    val previewMutedHint: String = "Bild ohne Ton beim Stöbern.",
    val sortAlphabetically: String = "Kanäle alphabetisch sortieren",
    val sortAlphabeticallyHint: String = "Statt nach Kanalnummer. Hilft in langen Listen wie " +
        "„Alle Kanäle“. In eigenen Gruppen gilt weiterhin deine selbst festgelegte Reihenfolge.",
    val startOnBoot: String = "Beim Einschalten des Geräts starten",
    val startOnBootHint: String = "Die Box bootet, Karacast ist sofort da. Für einen " +
        "Fernseher, der nur dafür gedacht ist.",
    val ownGroups: String = "Eigene Gruppen",
    val ownGroupsHint: String = "Fasse mehrere Anbieter-Kategorien zu einer eigenen Liste " +
        "zusammen — z.B. alles Sportliche in einer Gruppe. Gruppen stehen in der " +
        "Seitenleiste ganz oben.",
    val createdCount: String = "Angelegt",
    val manageGroups: String = "Gruppen verwalten",
    val whatLiveOpens: String = "Was „Live TV“ öffnet",
    val whatLiveOpensHint: String = "Wähle eine Liste, und Live TV geht sofort in die Kanäle — " +
        "ohne die Übersicht mit Gruppen, Favoriten und Zuletzt gesehen dazwischen, wie bei " +
        "einem Sat-Receiver. Zurück führt dann direkt aufs Dashboard.",
    val showOverview: String = "Übersicht zeigen",
    val overviewDefault: String = "Standard — erst das Menü, dann die Kanäle",
    val categoryOrder: String = "Reihenfolge der Kategorien",
    val noCategoriesLoaded: String = "Noch keine Kategorien geladen.",
    val categoryOrderHint: String =
        "Bestimmt, in welcher Folge die Kategorien in der Seitenleiste stehen.",
    val accountSection: String = "Konto",
    val accountHint: String = "Trennt die App vom Anbieter und löscht Kanäle, Favoriten und " +
        "EPG-Daten von diesem Gerät.",
    val disconnect: String = "Verbindung trennen",
    val updates: String = "Updates",
    val updatesHint: String = "Die App kommt nicht aus einem Store, also fragt sie selbst nach. " +
        "Hinterlege die Adresse einer kleinen Datei, die Versionsnummer und Download-Link " +
        "enthält — ohne Adresse wird nichts abgerufen.",
    val updateAddress: String = "Update-Adresse",
    val installedVersion: String = "Installierte Version",
    val checkNow: String = "Jetzt prüfen",
    val aboutSection: String = "Über die App",
    val aboutSectionHint: String = "Wer hinter Karacast IPTV steckt, wie man dich erreicht " +
        "und das Kleingedruckte.",
    val aboutButton: String = "Über Karacast IPTV",
    val disclaimer: String = "Karacast IPTV — die App liefert keine Inhalte, sondern spielt " +
        "ausschließlich deine eigene Playlist ab.",

    // ------------------------------------------------- what the settings reply
    val liveShowsOverviewAgain: String = "Live TV zeigt wieder die Übersicht.",
    val liveJumpsDirect: String =
        "Live TV springt ab dem nächsten Start direkt in diese Liste.",
    val noUpdateAddress: String = "Es ist keine Update-Adresse hinterlegt.",
    val alreadyLatest: String = "Du hast bereits die neueste Version.",
    val reloadingPlaylist: String = "Playlist wird neu geladen …",
    val loadingEpg: String = "Programmzeitschrift wird geladen …",
    val noEpgUrl: String = "Für diese Quelle ist keine EPG-URL hinterlegt.",
    val noProgramsDelivered: String = "Der Anbieter hat keine passenden Sendungen geliefert.",
    val noSourceConfigured: String = "Es ist keine Quelle eingerichtet.",
    val hlsAfterReload: String = "Wirkt nach dem nächsten Playlist-Neuladen.",

    // ----------------------------------------------------------------- groups
    val noGroupYet: String = "Noch keine Gruppe angelegt.",
    val newGroup: String = "Neue Gruppe",
    val editChannels: String = "Kanäle bearbeiten",
    val editChannelsHint: String =
        "Einzelne Kanäle aus der Gruppe nehmen und ihre Reihenfolge festlegen.",
    val noGroupSelected: String = "Keine Gruppe ausgewählt",
    val noGroupSelectedHint: String =
        "Lege links eine Gruppe an und hake hier die Kategorien an, die hineingehören.",
    val groupName: String = "Name der Gruppe",
    val categoriesInGroup: String = "Kategorien in dieser Gruppe",
    val delete: String = "Löschen",
    val noCategories: String = "Keine Kategorien",
    val noCategoriesHint: String = "Lade zuerst die Playlist, dann erscheinen hier die " +
        "Kategorien deines Anbieters.",
    val group: String = "Gruppe",
    val groupEditHint: String = "OK blendet aus oder wieder ein · ▲▼ verschiebt",
    val showAllChannels: String = "Alle einblenden",
    val resetOrder: String = "Reihenfolge zurücksetzen",
    val done: String = "Fertig",
    val noChannelsInGroup: String = "Keine Kanäle in dieser Gruppe",
    val noChannelsInGroupHint: String = "Hake zuerst im Gruppen-Editor die Kategorien an, " +
        "die in diese Gruppe gehören.",
    val hiddenLabel: String = "ausgeblendet",

    // ------------------------------------------------------------------ about
    val developer: String = "Entwickler",
    val contact: String = "Kontakt",
    val website: String = "Website",
    val legal: String = "Rechtliches",
    val aboutHint: String = "Hier kommt später dein Text hin.",
    val developerHint: String = "Name, Firma, Rolle.",
    val contactHint: String = "E-Mail, Telefon, WhatsApp.",
    val websiteHint: String = "Adresse deiner Seite oder deines Shops.",
    val legalHint: String = "Impressum, Haftung, Datenschutz.",
    val aboutDisclaimer: String = "Karacast IPTV liefert keine Inhalte. Die App spielt " +
        "ausschließlich die Playlist ab, die du selbst einträgst.",

    // ------------------------------------------------------------------ login
    val connectProvider: String = "Anbieter verbinden",
    val connectProviderHint: String = "Trage die Zugangsdaten deines IPTV-Anbieters ein.",
    val serverUrl: String = "Server-URL",
    val username: String = "Benutzername",
    val password: String = "Passwort",
    val m3uUrlLabel: String = "M3U-Playlist-URL",
    val epgUrlLabel: String = "XMLTV-EPG-URL (optional)",
    val connectAndLoad: String = "Verbinden und laden",
    val loginNote: String = "Hinweis: Diese App enthält keine Kanäle. Du brauchst ein " +
        "eigenes Abo bei einem Anbieter deiner Wahl.",
    val longPlaylistHint: String = "Das kann bei großen Playlists eine Minute dauern.",
    val brandHeadline: String = "Live-TV auf dem großen Bildschirm",
    val brandText: String = "Xtream-Codes-Konten und M3U-Playlists, mit Programmzeitschrift, " +
        "Favoriten und Fernbedienungs-Steuerung.",
    val myProvider: String = "Mein Anbieter",
    val connecting: String = "Verbinde …",
    val playlistUrl: String = "Playlist-URL",
    val errHostUnreachable: String =
        "Server nicht erreichbar — prüfe die Adresse und die Internetverbindung.",
    val errTimeout: String = "Zeitüberschreitung. Der Server antwortet nicht.",
    val errAuth: String = "Zugriff verweigert — Benutzername oder Passwort stimmen nicht.",
    val errNotFound404: String = "Adresse nicht gefunden (404). Stimmt die Server-URL?",
    val errSsl: String = "SSL-Fehler. Versuche http:// statt https://.",

    // ------------------------------------------------------- more with a value
    val nCategories: (Int) -> String = { "$it Kategorien" },
    val nHidden: (Int) -> String = { "$it ausgeblendet" },
    val nSelected: (Int) -> String = { "$it ausgewählt" },
    val versionLabel: (String) -> String = { "Version $it" },
    val versionAvailable: (String) -> String = { "Version $it ist verfügbar" },
    val nChannelsUpdated: (Int) -> String = { "$it Kanäle aktualisiert." },
    val nProgramsLoaded: (Int) -> String = { "$it Sendungen geladen." },
    val activeOfTotal: (Int, Int) -> String = { active, total ->
        "$active von $total Kanälen aktiv"
    },
    val missingFields: (String) -> String = {
        "Noch leer: $it. Feld mit OK öffnen, eintippen, mit Zurück schließen."
    },

    // ------------------------------------------------- what loading looks like
    val syncSigningIn: String = "Melde an …",
    val syncCategories: String = "Lade Kategorien …",
    val syncChannels: String = "Lade Kanäle …",
    val syncPlaylist: String = "Lade Playlist …",
    val syncGuide: String = "Lade Programmzeitschrift …",
    val errNoPlayableChannels: String = "Die Playlist enthält keine abspielbaren Kanäle.",
    val syncSaving: (Int) -> String = { "Speichere $it Kanäle" },
    val syncEntries: (Int) -> String = { "Verarbeite $it Einträge …" },
    val syncPrograms: (Int) -> String = { "$it Sendungen geladen …" },
    val errAccountInactive: (String) -> String = { "Das Konto ist nicht aktiv (Status: $it)." },

    /**
     * Grouped, not flat. A JVM constructor takes at most 255 arguments and this
     * class is most of the way there; a group costs one slot no matter how many
     * words it holds, so everything new goes into one.
     */
    val recovery: RecoveryStrings = RecoveryStrings(),
    val vod: VodStrings = VodStrings(),
    val tracks: TrackStrings = TrackStrings(),
    val colors: ColorStrings = ColorStrings(),
    val crash: CrashStrings = CrashStrings(),
    val parental: ParentalStrings = ParentalStrings(),
    val hub: HubStrings = HubStrings(),
    val info: AboutStrings = AboutStrings(),
    val update: UpdateStrings = UpdateStrings(),
    val panel: PanelStrings = PanelStrings(),
    val license: LicenseStrings = LicenseStrings()
)

/**
 * The trial, and what happens when it ends.
 *
 * Written to be read by somebody who did not expect a bill. It says what it costs,
 * that it is once and not monthly, and what to press — in that order, because that
 * is the order the questions arrive in.
 */
data class LicenseStrings(
    val section: String = "Lizenz",
    val tileHint: String = "Testphase, Aktivierung, Gerätecode",
    val expiredTitle: String = "Die Testphase ist vorbei",
    val blockedTitle: String = "Diese Lizenz ist gesperrt",
    val expiredBody: String = "Vierzehn Tage kostenlos sind um. Wenn Karacast dir " +
        "gefallen hat, schalte es einmalig frei — kein Abo, keine monatlichen Kosten, " +
        "und die Freischaltung gilt für dieses Gerät, solange es steht.",
    val blockedBody: String = "Für dieses Gerät wurde die Lizenz zurückgezogen. Wenn du " +
        "denkst, das ist ein Irrtum, melde dich mit dem Gerätecode unten.",
    val price: String = "Einmalig 9,99 €",
    val howTo: String = "Mit dem Handy den Code abscannen — die Seite kennt dein Gerät " +
        "dann schon. Sobald bezahlt ist, schaltet sich der Fernseher von selbst frei.",
    val deviceCode: String = "Gerätecode",
    val waiting: String = "Warte auf die Zahlung …",
    val checkNow: String = "Jetzt prüfen",
    val redeemTitle: String = "Oder Code eingeben",
    val redeemHint: String = "Acht Zeichen, falls du sie per E-Mail bekommen hast.",
    val redeem: String = "Einlösen",
    val activated: String = "Freigeschaltet — danke!",
    val failed: String = "Hat nicht geklappt. Stimmt der Code?",
    val noServer: String = "Es ist keine Lizenz-Adresse hinterlegt.",
    val status: String = "Status",
    val statusTrial: String = "Testversion",
    val statusActive: String = "Freigeschaltet",
    val statusExpired: String = "Abgelaufen",
    val statusBlocked: String = "Gesperrt",
    val serverAddress: String = "Lizenz-Adresse",
    val testingTitle: String = "Zum Ausprobieren",
    val testingHint: String = "Nur in der Entwicklerversion. Im fertigen APK gibt es " +
        "diese Knöpfe nicht.",
    val resetTrial: String = "Testphase zurücksetzen",
    val expireTrial: String = "Testphase beenden",
    val simulateActive: String = "Freischaltung simulieren",
    val daysLeft: (Int) -> String = { "Testversion — noch $it Tage" },
    val lastDay: String = "Testversion — letzter Tag",
    val activatedOn: (String) -> String = { "Freigeschaltet am $it" }
)

/**
 * Updating an app nobody got from a store.
 *
 * The app downloads the file and then hands it to Android, which asks the viewer
 * itself whether to install it. That second question is not something this app can
 * skip and should not want to: a television that installs software without anyone
 * agreeing is a television nobody should own.
 */
data class UpdateStrings(
    val available: String = "Neue Version verfügbar",
    val installNow: String = "Jetzt aktualisieren",
    val later: String = "Später",
    val skipVersion: String = "Diese Version überspringen",
    val downloading: String = "Wird heruntergeladen …",
    val readyToInstall: String = "Fertig — die Installation wird geöffnet",
    val installHint: String = "Android fragt gleich selbst, ob installiert werden darf. " +
        "Beim ersten Mal muss dieser App außerdem erlaubt werden, Apps zu installieren — " +
        "der Fernseher führt dich hin.",
    val failed: String = "Der Download hat nicht geklappt",
    val autoCheck: String = "Beim Start nach Updates suchen",
    val autoCheckHint: String = "Einmal täglich, wenn die App geöffnet wird. Gefunden wird " +
        "nur nachgefragt — installiert wird nie etwas von allein.",
    val percent: (Int) -> String = { "$it %" },
    val versionIsOut: (String) -> String = { "Version $it ist da" }
)

/**
 * The panel this app will one day be handed its account by.
 *
 * Nothing here happens until an address is entered: no request, no identifier sent,
 * nothing. That is deliberate — an app that phones somewhere before anybody asked
 * it to is not one you hand to your family.
 */
data class PanelStrings(
    val title: String = "Panel",
    val tileHint: String = "Zugang automatisch beziehen",
    val explain: String = "Später vergibt das Panel die Zugänge: Der Fernseher meldet sich " +
        "mit seinem Gerätecode, bekommt Server, Benutzer und Passwort zugeschickt und richtet " +
        "sich selbst ein. Niemand muss mehr etwas abtippen.\n\n" +
        "Solange hier keine Adresse steht, passiert nichts — die App verbindet sich mit " +
        "niemandem und verschickt nichts.",
    val address: String = "Panel-Adresse",
    val code: String = "Gerätecode",
    val codeHint: String = "Der Code, den das Panel für diesen Fernseher anzeigt.",
    val connect: String = "Jetzt verbinden",
    val notConfigured: String = "Kein Panel eingerichtet",
    val deviceId: String = "Geräte-Kennung",
    val deviceIdHint: String = "Zufällig vergeben, damit das Panel diesen Fernseher " +
        "wiedererkennt. Steckt keine Person dahinter.",
    val lastSync: String = "Zuletzt verbunden",
    val applySource: String = "Zugang übernehmen",
    val nothingReceived: String = "Das Panel hat keinen Zugang für diesen Code.",
    val insecure: String = "Diese Adresse ist unverschlüsselt (http). Benutzername und " +
        "Passwort wären im Netz mitlesbar — für den Dauerbetrieb bitte auf https umstellen.",
    val failed: String = "Das Panel war nicht erreichbar",
    val connected: (String) -> String = { "Verbunden — $it" },
    val sourceReceived: (String) -> String = { "Zugang „$it“ übernommen" }
)

/**
 * The presentation page.
 *
 * Deliberately translated rather than left as one fixed block of text: the name
 * and the e-mail address of whoever runs the app are the same in every language,
 * but what the app *is* and what it does not promise are exactly the things a
 * grandmother in Ankara has as much right to read in her own language as anyone.
 */
data class AboutStrings(
    val tagline: String = "Fernsehen für die ganze Familie — einschalten und schauen.",
    val body: String = "Karacast IPTV spielt deinen eigenen IPTV-Zugang auf dem Fernseher ab: " +
        "Live-Sender mit Programmführer, dazu Filme und Serien, sofern dein Anbieter " +
        "einen Katalog mitliefert.\n\n" +
        "Gebaut für die Fernbedienung und für Leute, die vorher kein Handbuch lesen " +
        "wollen: große Schrift, klare Kacheln, die Farbtasten dort, wo ein Receiver sie " +
        "auch hat. Bleibt ein Sender hängen, repariert die App ihn von allein, statt " +
        "eine Fehlermeldung hinzustellen.\n\n" +
        "Die Oberfläche gibt es auf Deutsch, Türkisch, Englisch und Arabisch.",
    val legal: String = "Karacast IPTV ist ein Abspielprogramm. Die App enthält, vermittelt " +
        "und verkauft weder Sender noch Filme oder Serien — sie zeigt ausschließlich das " +
        "an, was der Zugang liefert, den du selbst einträgst.\n\n" +
        "Wofür dieser Zugang verwendet wird und ob er dort, wo du lebst, zulässig ist, " +
        "entscheidest und verantwortest du. Für Inhalte, Verfügbarkeit und Rechtmäßigkeit " +
        "der eingetragenen Quelle wird keine Haftung übernommen.\n\n" +
        "Die App sammelt keine Nutzungsdaten und sendet nichts an Dritte. Zugangsdaten, " +
        "Favoriten und Einstellungen bleiben auf diesem Gerät.",
    val licensesTitle: String = "Verwendete Software",
    val licenses: String = "Karacast IPTV steht auf freier Software: AndroidX Media3 / " +
        "ExoPlayer, Jetpack Compose und Compose for TV, Room, OkHttp und Coil — alle unter " +
        "der Apache-Lizenz 2.0 — sowie FFmpeg, eingebunden über NextLib, unter der " +
        "LGPL 2.1 oder später. Die Lizenztexte und der Quellcode der FFmpeg-Bibliotheken " +
        "sind auf Anfrage erhältlich."
)

/**
 * The child lock. Worded for the person setting it up, not for a technician:
 * every line says what will actually be different on the television afterwards.
 */
data class ParentalStrings(
    val title: String = "Kindersicherung",
    val tileHint: String = "PIN, gesperrte Kategorien, Stichw\u00f6rter",
    val explain: String = "Gesperrte Kategorien verschwinden \u00fcberall \u2014 bei Live TV, " +
        "bei den Filmen und bei den Serien. Sie werden nicht abgefragt, sie sind einfach " +
        "nicht mehr da. Wer die PIN kennt, kann sie hier jederzeit wieder freigeben.",
    val enable: String = "Kindersicherung einschalten",
    val enableHint: String = "Aus hei\u00dft: alles ist sichtbar. Die PIN und die Liste " +
        "bleiben gespeichert und gelten wieder, sobald du sie einschaltest.",
    val pinTitle: String = "PIN",
    val pinHint: String = "Vier Ziffern. Ohne sie kommt niemand mehr in diesen Bereich.",
    val setPin: String = "PIN festlegen",
    val changePin: String = "PIN \u00e4ndern",
    val enterPin: String = "PIN eingeben",
    val newPin: String = "Neue PIN",
    val repeatPin: String = "PIN wiederholen",
    val save: String = "Speichern",
    val wrongPin: String = "Falsche PIN",
    val pinMismatch: String = "Die beiden Eingaben sind nicht gleich",
    val pinTooShort: String = "Die PIN muss aus vier Ziffern bestehen",
    val pinSaved: String = "PIN gespeichert",
    val noPinYet: String = "Noch keine PIN \u2014 jeder kommt hier herein",
    val pinSet: String = "PIN ist gesetzt",
    val keywordsTitle: String = "Stichw\u00f6rter",
    val keywordsHint: String = "Jede Kategorie, deren Name eines dieser W\u00f6rter " +
        "enth\u00e4lt, wird ausgeblendet \u2014 auch neue, die der Anbieter erst noch anlegt.",
    val addKeyword: String = "Hinzuf\u00fcgen",
    val newKeyword: String = "Neues Stichwort",
    val categoriesTitle: String = "Einzelne Kategorien",
    val categoriesHint: String = "Zus\u00e4tzlich zu den Stichw\u00f6rtern: hier sperrst du " +
        "Kategorien der Kanalliste von Hand.",
    val noCategories: String = "Keine Kategorien geladen",
    val locked: String = "Gesperrt",
    val free: String = "Frei",
    val nBlocked: (Int) -> String = { "$it gesperrt" }
)

/**
 * The settings hub: one tile per area, so nothing has to be scrolled past to
 * reach anything else. The short line under each name says what is inside, which
 * on a television matters more than the name itself.
 */
data class HubStrings(
    val hint: String = "OK \u00f6ffnet einen Bereich \u00b7 Zur\u00fcck kommt hierher",
    val source: String = "Quelle",
    val sourceHint: String = "Zugang, Playlist neu laden, EPG",
    val playback: String = "Wiedergabe",
    val playbackHint: String = "Puffer, Stream-Format, Selbstreparatur",
    val display: String = "Anzeige",
    val displayHint: String = "Uhr, Vorschau, Sortierung, Start",
    val groups: String = "Eigene Gruppen",
    val groupsHint: String = "Listen bauen und was Live TV \u00f6ffnet",
    val order: String = "Reihenfolge",
    val orderHint: String = "Kategorien sortieren",
    val updates: String = "Updates",
    val updatesHint: String = "Nach neuen Versionen suchen",
    val account: String = "Konto",
    val accountHint: String = "Trennen und neu einrichten",
    val about: String = "\u00dcber die App",
    val aboutHint: String = "Version und Hinweise",
    val problems: String = "Letzter Fehler",
    val problemsHint: String = "Was zuletzt schiefging, im Klartext"
)

/** What the app says about the last time it fell over. */
data class CrashStrings(
    val title: String = "Letzter Fehler",
    val explain: String = "Die App hat sich nach einem Fehler selbst neu gestartet. " +
        "Hier steht, was es war — nenn mir diese Zeile, wenn es wieder passiert.",
    val whenLabel: String = "Wann",
    val what: String = "Was",
    val forget: String = "Eintrag löschen"
)

/** The coloured-key bar: what each colour does, spelled out on screen. */
data class ColorStrings(
    val hint: String = "Farbtaste drücken — oder mit ◀▶ auswählen und OK",
    val previousChannel: String = "Vorheriger Kanal",
    val fromStart: String = "Von vorn",
    val audio: String = "Tonspur",
    val subtitles: String = "Untertitel",
    val picture: String = "Bildformat",
    val noPreviousChannel: String = "noch keiner",
    /** "Gilt nur für ARD HD" — said on the picture-format page, which is per channel. */
    val onlyFor: (String) -> String = { "Gilt nur für $it" }
)

/** Which dub to hear and which subtitles to read, chosen once in settings. */
data class TrackStrings(
    val section: String = "Ton und Untertitel",
    val audioTitle: String = "Bevorzugte Tonspur",
    val audioHint: String = "Bringt ein Film oder ein Kanal mehrere Sprachen mit, wählt " +
        "die App automatisch diese hier. Gibt es sie nicht, bleibt es bei der Spur, mit " +
        "der der Stream beginnt.",
    val subtitleTitle: String = "Untertitel",
    val subtitleHint: String = "Untertitel in dieser Sprache einschalten, sofern der Stream " +
        "welche mitliefert. „Aus“ heißt: keine Untertitel.",
    val automatic: String = "Automatisch"
)

/** The self-healing player: what it says while it puts a dead stream back together. */
data class RecoveryStrings(
    val reconnecting: String = "Verbindung wird wiederhergestellt …",
    val tryingOtherFormat: String = "Anderes Stream-Format wird versucht …",
    val stillTrying: String = "Der Sender antwortet nicht. Ich versuche es weiter — " +
        "die Wiedergabe startet von selbst, sobald er wieder da ist.",
    val autoRecover: String = "Hängende Streams selbst reparieren",
    val autoRecoverHint: String = "Bleibt das Bild stehen oder bricht die Verbindung ab, " +
        "verbindet sich die App von allein neu und wechselt notfalls das Stream-Format. " +
        "Ohne diese Option kommt stattdessen die Fehlermeldung mit „Erneut versuchen“.",
    val attempt: (Int) -> String = { "Versuch $it" }
)

/** Films and series: the catalogue, the detail page, the film player. */
data class VodStrings(
    val continueWatching: String = "Weiterschauen",
    val continueHint: String = "OK spielt an der Stelle weiter, an der du aufgehört hast",
    val clearList: String = "Liste leeren",
    val seriesCaption: String = "Staffeln und Folgen",
    val moviesCaption: String = "Der Filmkatalog deines Anbieters",
    val loading: String = "Wird geladen …",
    val noMovies: String = "Keine Filme",
    val noSeries: String = "Keine Serien",
    val noEpisodes: String = "Keine Folgen",
    val emptyCategory: String = "Diese Kategorie ist leer.",
    val onlyXtream: String = "Filme und Serien gibt es nur bei Xtream-Zugängen — " +
        "eine M3U-Playlist liefert keinen Katalog mit.",
    val play: String = "Abspielen",
    val playFromStart: String = "Von vorn abspielen",
    val plot: String = "Handlung",
    val cast: String = "Besetzung",
    val director: String = "Regie",
    val genre: String = "Genre",
    val released: String = "Erschienen",
    val duration: String = "Länge",
    val rating: String = "Bewertung",
    val seasonsTitle: String = "Staffeln",
    val episodesTitle: String = "Folgen",
    val gridHint: String = "OK öffnet · ◀ zurück zu den Kategorien",
    val episodeHint: String = "OK spielt die Folge",
    val playerHint: String = "OK Leiste · ◀▶ 30 Sek · Zurück beendet",
    val season: (Int) -> String = { "Staffel $it" },
    val episode: (Int) -> String = { "Folge $it" },
    val nSeasons: (Int) -> String = { "$it Staffeln" },
    val nEpisodes: (Int) -> String = { "$it Folgen" },
    val nTitles: (Int) -> String = { "$it Titel" },
    val continueAt: (String) -> String = { "Weiter bei $it" },
    val search: String = "Suche",
    val searchHint: String = "Ab zwei Buchstaben wird gesucht",
    val searchPrompt: String = "Wonach suchst du?",
    val searching: String = "Wird gesucht \u2026",
    val noResults: String = "Nichts gefunden",
    val noResultsHint: String = "Versuch eine andere Schreibweise oder weniger Buchstaben.",
    val favorites: String = "Favoriten",
    val noFavorites: String = "Noch keine Favoriten",
    val favoritesHint: String = "\u00d6ffne einen Titel und w\u00e4hl \u201eMerken\u201c \u2014 " +
        "dann wartet er hier auf dich.",
    val addFavorite: String = "Merken",
    val removeFavorite: String = "Gemerkt \u2713",
    val nextEpisode: String = "N\u00e4chste Folge",
    val lastEpisode: String = "Letzte Folge dieser Staffel",
    val autoNext: String = "Folgen automatisch weiterspielen",
    val autoNextHint: String = "Am Ende einer Folge startet die n\u00e4chste von allein \u2014 " +
        "solange die Staffel noch eine hat.",
    val nResults: (Int) -> String = { "$it Treffer" }
)

private val Turkish = Strings(
    back = "Geri",
    home = "Başlangıç",
    search = "Ara",
    guide = "Yayın akışı",
    settings = "Ayarlar",
    channels = "Kanal",
    off = "Kapalı",
    on = "Açık",
    none = "yok",
    live = "CANLI",

    liveTv = "Canlı TV",
    liveTvCaption = "Tüm kanallar ve gruplar",
    series = "Diziler",
    movies = "Filmler",
    comingSoon = "Yakında",
    seriesLater = "Diziler daha sonraki bir sürümde gelecek.",
    moviesLater = "Filmler daha sonraki bir sürümde gelecek.",

    favorites = "Favoriler",
    recentlyWatched = "Son izlenenler",
    allChannels = "Tüm kanallar",
    searchChannel = "Kanal ara",
    typeName = "Adını yaz …",
    noChannels = "Kanal yok",
    emptyList = "Bu liste boş. Geri tuşuyla genel görünüme dönersin.",
    noChannelSelected = "Kanal seçilmedi",
    nextUp = "Sonra",
    favorite = "Favori",
    jumpNow = "OK hemen atlar",
    switchNow = "OK hemen geçer",

    playerHint = "▲▼ Kanal · OK Liste · ◀▶ Bilgi",
    playbackFailed = "Oynatma başarısız",
    retry = "Tekrar dene",
    recording = "Kayıt",
    channelNotFound = "Kanal bulunamadı.",
    noArchive = "Bu kaynak için arşiv yok.",
    errNetwork = "Yayına bağlanılamadı. Ağı veya sunucuyu kontrol et.",
    errRejected = "Sunucu yayını reddetti. Genelde aynı anda çok fazla bağlantı demektir.",
    errNotFound = "Bu kanal sağlayıcıda mevcut değil.",
    errFormat = "Yayın biçimi tanınmadı. Ayarlarda TS yerine HLS dene.",
    errDecoder = "Bu cihazın çözücüsü bu yayınla baş edemiyor.",

    options = "Seçenekler",
    audioTrack = "Ses parçası",
    subtitles = "Altyazı",
    aspect = "Görüntü biçimi",
    sleepTimer = "Uyku zamanlayıcı",
    backCloses = "Geri kapatır",
    backOneLevel = "Geri bir seviye yukarı çıkar",
    nothingToChoose = "Bu kanalda seçilecek bir şey yok.",
    addFavorite = "Favorilere ekle",
    removeFavorite = "Favorilerden çıkar",
    aspectFit = "Sığdır",
    aspectFill = "Doldur",
    aspectZoom = "Yakınlaştır",
    aspectFitHint = "Tüm görüntü, gerekirse siyah bantlarla",
    aspectFillHint = "Görüntü çerçeveye göre gerilir",
    aspectZoomHint = "Bantlar gider, kenarlar kırpılır",
    stereo = "Stereo",
    mono = "Mono",

    tvGuide = "Yayın akışı",
    guideHint = "OK kanalı açar · arşivi olan geçmiş yayın kayıttan başlar",
    now = "Şimdi",
    noGuideData = "yayın bilgisi yok",
    loadingGuide = "Yayın akışı yükleniyor …",
    oneMoment = "Bir saniye.",
    loadPlaylistFirst = "Önce oynatma listesini yükle.",
    archive = "Arşiv",

    nChannels = { "$it kanal" },
    stillRunning = { "$it kaldı" },
    numberMissing = { "$it numarası yok" },
    nMinutes = { "$it dakika" },
    nMinutesShort = { "$it dk" },
    trackNumber = { "Parça $it" },
    startingUp = "Karacast IPTV başlatılıyor …",

    sourceSection = "Kaynak",
    name = "Ad",
    type = "Tür",
    m3uPlaylist = "M3U oynatma listesi",
    server = "Sunucu",
    lastLoaded = "Son yükleme",
    epgSection = "Yayın rehberi",
    available = "mevcut",
    noData = "veri yok",
    reloadPlaylist = "Listeyi yeniden yükle",
    refreshEpg = "EPG'yi güncelle",
    editCredentials = "Giriş bilgilerini değiştir",
    playbackSection = "Oynatma",
    useHls = "MPEG-TS yerine HLS kullan",
    useHlsHint = "Kanallar birkaç saniye sonra donuyorsa yardımcı olur. " +
        "Sonrasında listeyi yeniden yükle.",
    buffer = "Önbellek",
    bufferHint = "Daha çok önbellek = daha kararlı ama daha yavaş kanal geçişi.",
    userAgent = "User-Agent",
    displaySection = "Görünüm",
    clockWhilePlaying = "Oynatma sırasında saat",
    clockWhilePlayingHint = "Sağ üstte dijital saat, sürekli görünür.",
    resumeLast = "Açılışta son izlenen kanalı aç",
    resumeLastHint = "Uygulama açılır açılmaz oynatmaya girer. " +
        "Geri tuşuyla kanal listesine dönersin.",
    previewWhileBrowsing = "Gezinirken önizleme",
    previewWhileBrowsingHint = "Seçili kanal sağdaki panelde çalışır; hızlı gezinmede " +
        "bağlantı kurulmasın diye kısa bir gecikmeyle.",
    previewMutedTitle = "Önizleme sessiz",
    previewMutedHint = "Gezinirken sesiz görüntü.",
    sortAlphabetically = "Kanalları alfabetik sırala",
    sortAlphabeticallyHint = "Kanal numarası yerine. „Tüm kanallar“ gibi uzun listelerde " +
        "işe yarar. Kendi gruplarında senin belirlediğin sıra geçerli kalır.",
    startOnBoot = "Cihaz açılınca başlat",
    startOnBootHint = "Kutu açılır, Karacast hemen oradadır. Sadece bunun için ayrılmış " +
        "bir televizyon için.",
    ownGroups = "Kendi grupların",
    ownGroupsHint = "Birden çok sağlayıcı kategorisini tek bir listede topla — örneğin " +
        "tüm spor kanallarını bir grupta. Gruplar kenar çubuğunun en üstünde yer alır.",
    createdCount = "Oluşturulan",
    manageGroups = "Grupları yönet",
    whatLiveOpens = "„Canlı TV“ neyi açar",
    whatLiveOpensHint = "Bir liste seç; Canlı TV doğrudan kanallara girsin — gruplar, " +
        "favoriler ve son izlenenler ekranı araya girmeden, uydu alıcısı gibi. " +
        "Geri tuşu o zaman doğrudan ana ekrana götürür.",
    showOverview = "Genel görünümü göster",
    overviewDefault = "Varsayılan — önce menü, sonra kanallar",
    categoryOrder = "Kategori sırası",
    noCategoriesLoaded = "Henüz kategori yüklenmedi.",
    categoryOrderHint = "Kategorilerin kenar çubuğunda hangi sırayla duracağını belirler.",
    accountSection = "Hesap",
    accountHint = "Uygulamanın sağlayıcıyla bağlantısını keser; kanalları, favorileri ve " +
        "EPG verilerini bu cihazdan siler.",
    disconnect = "Bağlantıyı kes",
    updates = "Güncellemeler",
    updatesHint = "Uygulama bir mağazadan gelmiyor, bu yüzden kendisi soruyor. Sürüm " +
        "numarası ve indirme bağlantısı içeren küçük bir dosyanın adresini gir — " +
        "adres yoksa hiçbir şey sorgulanmaz.",
    updateAddress = "Güncelleme adresi",
    installedVersion = "Kurulu sürüm",
    checkNow = "Şimdi kontrol et",
    aboutSection = "Uygulama hakkında",
    aboutSectionHint = "Karacast IPTV'nin arkasında kim var, sana nasıl ulaşılır ve " +
        "küçük yazılar.",
    aboutButton = "Karacast IPTV hakkında",
    disclaimer = "Karacast IPTV — uygulama içerik sağlamaz, yalnızca senin kendi " +
        "oynatma listeni çalar.",

    liveShowsOverviewAgain = "Canlı TV yine genel görünümü gösteriyor.",
    liveJumpsDirect = "Canlı TV bir sonraki açılıştan itibaren doğrudan bu listeye girer.",
    noUpdateAddress = "Kayıtlı bir güncelleme adresi yok.",
    alreadyLatest = "Zaten en yeni sürümü kullanıyorsun.",
    reloadingPlaylist = "Oynatma listesi yeniden yükleniyor …",
    loadingEpg = "Yayın rehberi yükleniyor …",
    noEpgUrl = "Bu kaynak için kayıtlı bir EPG adresi yok.",
    noProgramsDelivered = "Sağlayıcı uygun bir yayın bilgisi göndermedi.",
    noSourceConfigured = "Kurulu bir kaynak yok.",
    hlsAfterReload = "Listeyi bir sonraki yeniden yüklemede etkili olur.",

    noGroupYet = "Henüz grup oluşturulmadı.",
    newGroup = "Yeni grup",
    editChannels = "Kanalları düzenle",
    editChannelsHint = "Gruptan tek tek kanal çıkar ve sıralamayı belirle.",
    noGroupSelected = "Grup seçilmedi",
    noGroupSelectedHint = "Soldan bir grup oluştur ve buradan içine girecek kategorileri işaretle.",
    groupName = "Grubun adı",
    categoriesInGroup = "Bu gruptaki kategoriler",
    delete = "Sil",
    noCategories = "Kategori yok",
    noCategoriesHint = "Önce oynatma listesini yükle; sağlayıcının kategorileri burada görünür.",
    group = "Grup",
    groupEditHint = "OK gizler veya geri gösterir · ▲▼ taşır",
    showAllChannels = "Hepsini göster",
    resetOrder = "Sıralamayı sıfırla",
    done = "Bitti",
    noChannelsInGroup = "Bu grupta kanal yok",
    noChannelsInGroupHint = "Önce grup düzenleyicide bu gruba girecek kategorileri işaretle.",
    hiddenLabel = "gizli",

    developer = "Geliştirici",
    contact = "İletişim",
    website = "Web sitesi",
    legal = "Yasal",
    aboutHint = "Metnin daha sonra buraya gelecek.",
    developerHint = "Ad, firma, görev.",
    contactHint = "E-posta, telefon, WhatsApp.",
    websiteHint = "Sayfanın veya mağazanın adresi.",
    legalHint = "Künye, sorumluluk, gizlilik.",
    aboutDisclaimer = "Karacast IPTV içerik sağlamaz. Uygulama yalnızca senin girdiğin " +
        "oynatma listesini çalar.",

    connectProvider = "Sağlayıcıya bağlan",
    connectProviderHint = "IPTV sağlayıcının giriş bilgilerini gir.",
    serverUrl = "Sunucu adresi",
    username = "Kullanıcı adı",
    password = "Şifre",
    m3uUrlLabel = "M3U liste adresi",
    epgUrlLabel = "XMLTV EPG adresi (isteğe bağlı)",
    connectAndLoad = "Bağlan ve yükle",
    loginNote = "Not: Bu uygulama kanal içermez. Kendi seçtiğin bir sağlayıcıda " +
        "kendi aboneliğine ihtiyacın var.",
    longPlaylistHint = "Büyük listelerde bu bir dakika sürebilir.",
    brandHeadline = "Büyük ekranda canlı TV",
    brandText = "Xtream Codes hesapları ve M3U listeleri; yayın rehberi, favoriler ve " +
        "kumanda kontrolü ile.",
    myProvider = "Sağlayıcım",
    connecting = "Bağlanıyor …",
    playlistUrl = "Liste adresi",
    errHostUnreachable = "Sunucuya ulaşılamıyor — adresi ve internet bağlantısını kontrol et.",
    errTimeout = "Zaman aşımı. Sunucu yanıt vermiyor.",
    errAuth = "Erişim reddedildi — kullanıcı adı veya şifre yanlış.",
    errNotFound404 = "Adres bulunamadı (404). Sunucu adresi doğru mu?",
    errSsl = "SSL hatası. https:// yerine http:// dene.",

    nCategories = { "$it kategori" },
    nHidden = { "$it gizli" },
    nSelected = { "$it seçili" },
    versionLabel = { "Sürüm $it" },
    versionAvailable = { "Sürüm $it mevcut" },
    nChannelsUpdated = { "$it kanal güncellendi." },
    nProgramsLoaded = { "$it yayın yüklendi." },
    activeOfTotal = { active, total -> "$total kanaldan $active tanesi etkin" },
    missingFields = {
        "Hâlâ boş: $it. Alanı OK ile aç, yaz, Geri ile kapat."
    },

    syncSigningIn = "Giriş yapılıyor …",
    syncCategories = "Kategoriler yükleniyor …",
    syncChannels = "Kanallar yükleniyor …",
    syncPlaylist = "Oynatma listesi yükleniyor …",
    syncGuide = "Yayın rehberi yükleniyor …",
    errNoPlayableChannels = "Oynatma listesinde çalınabilir kanal yok.",
    syncSaving = { "$it kanal kaydediliyor" },
    syncEntries = { "$it kayıt işleniyor …" },
    syncPrograms = { "$it yayın yüklendi …" },
    errAccountInactive = { "Hesap etkin değil (durum: $it)." },

    recovery = RecoveryStrings(
        reconnecting = "Bağlantı yeniden kuruluyor …",
        tryingOtherFormat = "Başka bir yayın biçimi deneniyor …",
        stillTrying = "Kanal yanıt vermiyor. Denemeye devam ediyorum — " +
            "kanal geri geldiğinde yayın kendiliğinden başlar.",
        autoRecover = "Donan yayınları kendi kendine onar",
        autoRecoverHint = "Görüntü donarsa ya da bağlantı koparsa uygulama kendiliğinden " +
            "yeniden bağlanır, gerekirse yayın biçimini değiştirir. Bu seçenek kapalıyken " +
            "onun yerine „Tekrar dene“ yazan hata mesajı gelir.",
        attempt = { "Deneme $it" }
    ),
    crash = CrashStrings(
        title = "Son hata",
        explain = "Uygulama bir hatadan sonra kendini yeniden başlattı. Ne olduğu " +
            "burada yazıyor — tekrar olursa bana bu satırı söyle.",
        whenLabel = "Ne zaman",
        what = "Ne",
        forget = "Kaydı sil"
    ),
    colors = ColorStrings(
        hint = "Renkli tuşa bas — ya da ◀▶ ile seç ve OK",
        previousChannel = "Önceki kanal",
        fromStart = "Baştan",
        audio = "Ses parçası",
        subtitles = "Altyazı",
        picture = "Görüntü biçimi",
        noPreviousChannel = "henüz yok",
        onlyFor = { "Yalnızca $it için geçerli" }
    ),
    tracks = TrackStrings(
        section = "Ses ve altyazı",
        audioTitle = "Tercih edilen ses parçası",
        audioHint = "Bir film ya da kanal birden çok dil taşıyorsa uygulama otomatik " +
            "olarak bunu seçer. Yoksa yayının başladığı parça kalır.",
        subtitleTitle = "Altyazı",
        subtitleHint = "Yayın altyazı içeriyorsa bu dildeki altyazıyı açar. " +
            "„Kapalı“ demek: altyazı yok.",
        automatic = "Otomatik"
    ),
    vod = VodStrings(
        continueWatching = "İzlemeye devam et",
        continueHint = "OK, bıraktığın yerden devam eder",
        clearList = "Listeyi temizle",
        seriesCaption = "Sezonlar ve bölümler",
        moviesCaption = "Sağlayıcının film kataloğu",
        loading = "Yükleniyor …",
        noMovies = "Film yok",
        noSeries = "Dizi yok",
        noEpisodes = "Bölüm yok",
        emptyCategory = "Bu kategori boş.",
        onlyXtream = "Filmler ve diziler yalnızca Xtream hesaplarında var — " +
            "M3U listesi katalog getirmez.",
        play = "Oynat",
        playFromStart = "Baştan oynat",
        plot = "Konu",
        cast = "Oyuncular",
        director = "Yönetmen",
        genre = "Tür",
        released = "Yayın yılı",
        duration = "Süre",
        rating = "Puan",
        seasonsTitle = "Sezonlar",
        episodesTitle = "Bölümler",
        gridHint = "OK açar · ◀ kategorilere döner",
        episodeHint = "OK bölümü oynatır",
        playerHint = "OK çubuk · ◀▶ 30 sn · Geri bitirir",
        season = { "$it. Sezon" },
        episode = { "$it. Bölüm" },
        nSeasons = { "$it sezon" },
        nEpisodes = { "$it bölüm" },
        nTitles = { "$it başlık" },
        continueAt = { "$it konumundan devam et" },
        search = "Arama",
        searchHint = "\u0130ki harften itibaren arar",
        searchPrompt = "Ne ar\u0131yorsun?",
        searching = "Aran\u0131yor \u2026",
        noResults = "Bir \u015fey bulunamad\u0131",
        noResultsHint = "Ba\u015fka bir yaz\u0131m ya da daha az harf dene.",
        favorites = "Favoriler",
        noFavorites = "Hen\u00fcz favori yok",
        favoritesHint = "Bir ba\u015fl\u0131\u011f\u0131 a\u00e7 ve \u201eKaydet\u201c se\u00e7 \u2014 " +
            "sonra burada seni bekler.",
        addFavorite = "Kaydet",
        removeFavorite = "Kaydedildi \u2713",
        nextEpisode = "Sonraki b\u00f6l\u00fcm",
        lastEpisode = "Bu sezonun son b\u00f6l\u00fcm\u00fc",
        autoNext = "B\u00f6l\u00fcmleri otomatik oynat",
        autoNextHint = "Bir b\u00f6l\u00fcm bitince sonraki kendili\u011finden ba\u015flar \u2014 " +
            "bu sezonda ba\u015fka b\u00f6l\u00fcm kald\u0131\u011f\u0131 s\u00fcrece.",
        nResults = { "$it sonu\u00e7" }
    ),
    parental = ParentalStrings(
        title = "\u00c7ocuk kilidi",
        tileHint = "PIN, kilitli kategoriler, anahtar kelimeler",
        explain = "Kilitli kategoriler her yerden kaybolur \u2014 canl\u0131 yay\u0131nda, " +
            "filmlerde ve dizilerde. Sorulmaz, sadece art\u0131k orada de\u011fildir. " +
            "PIN\u2019i bilen istedi\u011fi zaman burada yeniden a\u00e7abilir.",
        enable = "\u00c7ocuk kilidini a\u00e7",
        enableHint = "Kapal\u0131yken her \u015fey g\u00f6r\u00fcn\u00fcr. PIN ve liste " +
            "kay\u0131tl\u0131 kal\u0131r ve tekrar a\u00e7t\u0131\u011f\u0131nda ge\u00e7erli olur.",
        pinTitle = "PIN",
        pinHint = "D\u00f6rt rakam. Onsuz kimse bu b\u00f6l\u00fcme giremez.",
        setPin = "PIN belirle",
        changePin = "PIN de\u011fi\u015ftir",
        enterPin = "PIN gir",
        newPin = "Yeni PIN",
        repeatPin = "PIN\u2019i tekrarla",
        save = "Kaydet",
        wrongPin = "Yanl\u0131\u015f PIN",
        pinMismatch = "\u0130ki giri\u015f ayn\u0131 de\u011fil",
        pinTooShort = "PIN d\u00f6rt rakam olmal\u0131",
        pinSaved = "PIN kaydedildi",
        noPinYet = "Hen\u00fcz PIN yok \u2014 herkes girebilir",
        pinSet = "PIN belirlendi",
        keywordsTitle = "Anahtar kelimeler",
        keywordsHint = "Ad\u0131nda bu kelimelerden biri ge\u00e7en her kategori gizlenir \u2014 " +
            "sa\u011flay\u0131c\u0131n\u0131n sonradan ekleyecekleri de.",
        addKeyword = "Ekle",
        newKeyword = "Yeni kelime",
        categoriesTitle = "Tek tek kategoriler",
        categoriesHint = "Anahtar kelimelere ek olarak: kanal listesi kategorilerini " +
            "burada elle kilitlersin.",
        noCategories = "Y\u00fcklenmi\u015f kategori yok",
        locked = "Kilitli",
        free = "A\u00e7\u0131k",
        nBlocked = { "$it kilitli" }
    ),
    hub = HubStrings(
        hint = "OK bir b\u00f6l\u00fcm\u00fc a\u00e7ar \u00b7 Geri buraya d\u00f6ner",
        source = "Kaynak",
        sourceHint = "Hesap, listeyi yenile, EPG",
        playback = "Oynatma",
        playbackHint = "Tampon, yay\u0131n bi\u00e7imi, kendi kendine onarma",
        display = "G\u00f6r\u00fcn\u00fcm",
        displayHint = "Saat, \u00f6nizleme, s\u0131ralama, ba\u015flang\u0131\u00e7",
        groups = "Kendi gruplar\u0131n",
        groupsHint = "Liste kur ve canl\u0131 yay\u0131n\u0131n neyi a\u00e7aca\u011f\u0131",
        order = "S\u0131ra",
        orderHint = "Kategorileri s\u0131rala",
        updates = "G\u00fcncellemeler",
        updatesHint = "Yeni s\u00fcr\u00fcm ara",
        account = "Hesap",
        accountHint = "Ba\u011flant\u0131y\u0131 kes ve yeniden kur",
        about = "Uygulama hakk\u0131nda",
        aboutHint = "S\u00fcr\u00fcm ve notlar",
        problems = "Son hata",
        problemsHint = "En son ne ters gitti, a\u00e7\u0131k\u00e7a"
    ),
    info = AboutStrings(
        tagline = "Tüm aile için televizyon — aç ve izle.",
        body = "Karacast IPTV kendi IPTV hesabını televizyonda oynatır: program " +
            "rehberiyle canlı kanallar, sağlayıcın katalog veriyorsa filmler ve " +
            "diziler.\n\n" +
            "Kumanda için yapıldı ve önce kılavuz okumak istemeyen insanlar için: büyük " +
            "yazı, net kutucuklar, renkli tuşlar bir uydu alıcısındaki yerinde. Bir yayın " +
            "takılırsa uygulama hata mesajı göstermek yerine onu kendi kendine onarır.\n\n" +
            "Arayüz Almanca, Türkçe, İngilizce ve Arapça olarak var.",
        legal = "Karacast IPTV bir oynatıcıdır. Uygulama ne kanal ne film ne de dizi " +
            "içerir, satar ya da aracılık eder — yalnızca senin girdiğin hesabın " +
            "verdiğini gösterir.\n\n" +
            "Bu hesabın ne için kullanıldığı ve yaşadığın yerde yasal olup olmadığı senin " +
            "kararın ve sorumluluğundur. Girilen kaynağın içeriği, erişilebilirliği ve " +
            "hukuka uygunluğu için sorumluluk kabul edilmez.\n\n" +
            "Uygulama kullanım verisi toplamaz ve hiçbir şeyi üçüncü kişilere göndermez. " +
            "Hesap bilgileri, favoriler ve ayarlar bu cihazda kalır.",
        licensesTitle = "Kullanılan yazılım",
        licenses = "Karacast IPTV özgür yazılımın üzerinde duruyor: AndroidX Media3 / " +
            "ExoPlayer, Jetpack Compose ve Compose for TV, Room, OkHttp ve Coil — hepsi " +
            "Apache Lisansı 2.0 — ayrıca NextLib üzerinden eklenen FFmpeg, LGPL 2.1 veya " +
            "sonrası. Lisans metinleri ve FFmpeg kütüphanelerinin kaynak kodu talep " +
            "üzerine verilir."
    ),
    update = UpdateStrings(
        available = "Yeni sürüm var",
        installNow = "Şimdi güncelle",
        later = "Sonra",
        skipVersion = "Bu sürümü atla",
        downloading = "İndiriliyor …",
        readyToInstall = "Bitti — kurulum açılıyor",
        installHint = "Android birazdan kurulum için kendisi soracak. İlk seferde bu " +
            "uygulamaya ayrıca uygulama kurma izni verilmeli — televizyon seni oraya " +
            "götürür.",
        failed = "İndirme başarısız oldu",
        autoCheck = "Açılışta güncelleme ara",
        autoCheckHint = "Uygulama açıldığında günde bir kez. Bulunursa yalnızca sorulur — " +
            "hiçbir şey kendiliğinden kurulmaz.",
        percent = { "$it %" },
        versionIsOut = { "$it sürümü çıktı" }
    ),
    panel = PanelStrings(
        title = "Panel",
        tileHint = "Hesabı otomatik al",
        explain = "İleride hesapları panel dağıtacak: televizyon cihaz koduyla bildirir, " +
            "sunucu, kullanıcı ve şifre kendisine gönderilir ve kendini kurar. Kimsenin bir " +
            "şey yazmasına gerek kalmaz.\n\n" +
            "Burada adres yazmadığı sürece hiçbir şey olmaz — uygulama kimseye bağlanmaz ve " +
            "hiçbir şey göndermez.",
        address = "Panel adresi",
        code = "Cihaz kodu",
        codeHint = "Panelin bu televizyon için gösterdiği kod.",
        connect = "Şimdi bağlan",
        notConfigured = "Panel kurulmadı",
        deviceId = "Cihaz kimliği",
        deviceIdHint = "Panelin bu televizyonu tanıması için rastgele verildi. Arkasında " +
            "bir kişi yok.",
        lastSync = "Son bağlantı",
        applySource = "Hesabı al",
        nothingReceived = "Panelde bu koda ait hesap yok.",
        insecure = "Bu adres şifresiz (http). Kullanıcı adı ve şifre ağda okunabilir olur — " +
            "sürekli kullanım için lütfen https yapın.",
        failed = "Panele ulaşılamadı",
        connected = { "Bağlandı — $it" },
        sourceReceived = { "„$it“ hesabı alındı" }
    ),
    license = LicenseStrings(
        section = "Lisans",
        tileHint = "Deneme süresi, etkinleştirme, cihaz kodu",
        expiredTitle = "Deneme süresi bitti",
        blockedTitle = "Bu lisans kilitli",
        expiredBody = "On dört ücretsiz gün doldu. Karacast hoşuna gittiyse bir defaya " +
            "mahsus aç — abonelik yok, aylık ücret yok, ve açılış bu cihaz durduğu " +
            "sürece geçerli.",
        blockedBody = "Bu cihazın lisansı geri alındı. Bunun bir yanlışlık olduğunu " +
            "düşünüyorsan aşağıdaki cihaz koduyla bize ulaş.",
        price = "Tek seferlik 9,99 €",
        howTo = "Kodu telefonla okut — sayfa cihazını zaten tanıyor. Ödeme yapılır " +
            "yapılmaz televizyon kendini açar.",
        deviceCode = "Cihaz kodu",
        waiting = "Ödeme bekleniyor …",
        checkNow = "Şimdi kontrol et",
        redeemTitle = "Ya da kodu gir",
        redeemHint = "E-posta ile aldıysan sekiz karakter.",
        redeem = "Kullan",
        activated = "Açıldı — teşekkürler!",
        failed = "Olmadı. Kod doğru mu?",
        noServer = "Kayıtlı bir lisans adresi yok.",
        status = "Durum",
        statusTrial = "Deneme sürümü",
        statusActive = "Açık",
        statusExpired = "Süresi doldu",
        statusBlocked = "Kilitli",
        serverAddress = "Lisans adresi",
        testingTitle = "Denemek için",
        testingHint = "Yalnızca geliştirici sürümünde. Bitmiş APK'da bu düğmeler yok.",
        resetTrial = "Deneme süresini sıfırla",
        expireTrial = "Deneme süresini bitir",
        simulateActive = "Açılışı simüle et",
        daysLeft = { "Deneme sürümü — $it gün kaldı" },
        lastDay = "Deneme sürümü — son gün",
        activatedOn = { "$it tarihinde açıldı" }
    )
)

private val English = Strings(
    back = "Back",
    home = "Home",
    search = "Search",
    guide = "Guide",
    settings = "Settings",
    channels = "Channels",
    off = "Off",
    on = "On",
    none = "none",
    live = "LIVE",

    liveTv = "Live TV",
    liveTvCaption = "All channels and groups",
    series = "Series",
    movies = "Movies",
    comingSoon = "Coming soon",
    seriesLater = "Series will arrive in a later version.",
    moviesLater = "Movies will arrive in a later version.",

    favorites = "Favourites",
    recentlyWatched = "Recently watched",
    allChannels = "All channels",
    searchChannel = "Search channel",
    typeName = "Type a name …",
    noChannels = "No channels",
    emptyList = "This list is empty. Back takes you to the overview.",
    noChannelSelected = "No channel selected",
    nextUp = "Next",
    favorite = "Favourite",
    jumpNow = "OK jumps now",
    switchNow = "OK switches now",

    playerHint = "▲▼ Channel · OK List · ◀▶ Info",
    playbackFailed = "Playback failed",
    retry = "Try again",
    recording = "Recording",
    channelNotFound = "Channel not found.",
    noArchive = "This source has no archive.",
    errNetwork = "Cannot reach the stream. Check the network or the server.",
    errRejected = "The server refused the stream. Usually that means too many connections at once.",
    errNotFound = "This channel is not available from the provider.",
    errFormat = "The stream format was not recognised. Try HLS instead of TS in settings.",
    errDecoder = "This device's decoder cannot handle the stream.",

    options = "Options",
    audioTrack = "Audio track",
    subtitles = "Subtitles",
    aspect = "Picture format",
    sleepTimer = "Sleep timer",
    backCloses = "Back closes",
    backOneLevel = "Back goes up one level",
    nothingToChoose = "Nothing to choose here for this channel.",
    addFavorite = "Add to favourites",
    removeFavorite = "Remove from favourites",
    aspectFit = "Fit",
    aspectFill = "Fill",
    aspectZoom = "Zoom",
    aspectFitHint = "Whole picture, bars if needed",
    aspectFillHint = "Picture stretched to the frame",
    aspectZoomHint = "Bars gone, edges cropped",
    stereo = "Stereo",
    mono = "Mono",

    tvGuide = "TV guide",
    guideHint = "OK plays the channel · a past programme with archive starts the recording",
    now = "Now",
    noGuideData = "no guide data",
    loadingGuide = "Loading the guide …",
    oneMoment = "One moment.",
    loadPlaylistFirst = "Load the playlist first.",
    archive = "Archive",

    nChannels = { "$it channels" },
    stillRunning = { "$it left" },
    numberMissing = { "No. $it does not exist" },
    nMinutes = { "$it minutes" },
    nMinutesShort = { "$it min" },
    trackNumber = { "Track $it" },
    startingUp = "Starting Karacast IPTV …",

    sourceSection = "Source",
    name = "Name",
    type = "Type",
    m3uPlaylist = "M3U playlist",
    server = "Server",
    lastLoaded = "Last loaded",
    epgSection = "TV guide",
    available = "available",
    noData = "no data",
    reloadPlaylist = "Reload playlist",
    refreshEpg = "Refresh EPG",
    editCredentials = "Change credentials",
    playbackSection = "Playback",
    useHls = "Use HLS instead of MPEG-TS",
    useHlsHint = "Helps when channels stall after a few seconds. " +
        "Reload the playlist afterwards.",
    buffer = "Buffer",
    bufferHint = "More buffer = steadier, but slower channel changes.",
    userAgent = "User agent",
    displaySection = "Display",
    clockWhilePlaying = "Clock during playback",
    clockWhilePlayingHint = "Digital clock at the top right, always visible.",
    resumeLast = "Open the last watched channel at start",
    resumeLastHint = "The app goes straight into playback when it opens. " +
        "Back takes you to the channel list.",
    previewWhileBrowsing = "Preview while browsing",
    previewWhileBrowsingHint = "The focused channel plays in the panel on the right, " +
        "after a short delay so fast browsing opens no connection.",
    previewMutedTitle = "Preview muted",
    previewMutedHint = "Picture without sound while browsing.",
    sortAlphabetically = "Sort channels alphabetically",
    sortAlphabeticallyHint = "Instead of by channel number. Helps in long lists such as " +
        "“All channels”. Your own groups keep the order you set.",
    startOnBoot = "Start when the device switches on",
    startOnBootHint = "The box boots, Karacast is right there. For a television " +
        "meant for nothing else.",
    ownGroups = "Your own groups",
    ownGroupsHint = "Bundle several provider categories into one list of your own — " +
        "everything sporting in one group, say. Groups sit at the very top of the sidebar.",
    createdCount = "Created",
    manageGroups = "Manage groups",
    whatLiveOpens = "What “Live TV” opens",
    whatLiveOpensHint = "Pick a list and Live TV goes straight to the channels — without " +
        "the overview of groups, favourites and recently watched in between, like a " +
        "satellite receiver. Back then leads straight to the dashboard.",
    showOverview = "Show the overview",
    overviewDefault = "Default — the menu first, then the channels",
    categoryOrder = "Order of the categories",
    noCategoriesLoaded = "No categories loaded yet.",
    categoryOrderHint = "Decides the order the categories appear in the sidebar.",
    accountSection = "Account",
    accountHint = "Disconnects the app from the provider and deletes channels, favourites " +
        "and guide data from this device.",
    disconnect = "Disconnect",
    updates = "Updates",
    updatesHint = "The app does not come from a store, so it asks for itself. Enter the " +
        "address of a small file holding the version number and a download link — " +
        "without an address nothing is fetched.",
    updateAddress = "Update address",
    installedVersion = "Installed version",
    checkNow = "Check now",
    aboutSection = "About the app",
    aboutSectionHint = "Who is behind Karacast IPTV, how to get in touch, and the small print.",
    aboutButton = "About Karacast IPTV",
    disclaimer = "Karacast IPTV — the app supplies no content, it only plays the playlist " +
        "you enter yourself.",

    liveShowsOverviewAgain = "Live TV shows the overview again.",
    liveJumpsDirect = "From the next start, Live TV goes straight into this list.",
    noUpdateAddress = "No update address has been entered.",
    alreadyLatest = "You already have the newest version.",
    reloadingPlaylist = "Reloading the playlist …",
    loadingEpg = "Loading the TV guide …",
    noEpgUrl = "No EPG address is stored for this source.",
    noProgramsDelivered = "The provider delivered no matching programmes.",
    noSourceConfigured = "No source has been set up.",
    hlsAfterReload = "Takes effect after the next playlist reload.",

    noGroupYet = "No group created yet.",
    newGroup = "New group",
    editChannels = "Edit channels",
    editChannelsHint = "Take single channels out of the group and set their order.",
    noGroupSelected = "No group selected",
    noGroupSelectedHint = "Create a group on the left, then tick the categories that " +
        "belong in it here.",
    groupName = "Name of the group",
    categoriesInGroup = "Categories in this group",
    delete = "Delete",
    noCategories = "No categories",
    noCategoriesHint = "Load the playlist first, then your provider's categories appear here.",
    group = "Group",
    groupEditHint = "OK hides or shows again · ▲▼ moves",
    showAllChannels = "Show all",
    resetOrder = "Reset the order",
    done = "Done",
    noChannelsInGroup = "No channels in this group",
    noChannelsInGroupHint = "Tick the categories that belong in this group in the group " +
        "editor first.",
    hiddenLabel = "hidden",

    developer = "Developer",
    contact = "Contact",
    website = "Website",
    legal = "Legal",
    aboutHint = "Your text goes here later.",
    developerHint = "Name, company, role.",
    contactHint = "E-mail, phone, WhatsApp.",
    websiteHint = "The address of your site or your shop.",
    legalHint = "Imprint, liability, privacy.",
    aboutDisclaimer = "Karacast IPTV supplies no content. The app only plays the playlist " +
        "you enter yourself.",

    connectProvider = "Connect a provider",
    connectProviderHint = "Enter the credentials of your IPTV provider.",
    serverUrl = "Server URL",
    username = "User name",
    password = "Password",
    m3uUrlLabel = "M3U playlist URL",
    epgUrlLabel = "XMLTV EPG URL (optional)",
    connectAndLoad = "Connect and load",
    loginNote = "Note: this app contains no channels. You need your own subscription " +
        "with a provider of your choice.",
    longPlaylistHint = "With large playlists this can take a minute.",
    brandHeadline = "Live TV on the big screen",
    brandText = "Xtream Codes accounts and M3U playlists, with a TV guide, favourites " +
        "and remote control.",
    myProvider = "My provider",
    connecting = "Connecting …",
    playlistUrl = "Playlist URL",
    errHostUnreachable = "Server unreachable — check the address and the internet connection.",
    errTimeout = "Timed out. The server is not answering.",
    errAuth = "Access denied — the user name or password is wrong.",
    errNotFound404 = "Address not found (404). Is the server URL right?",
    errSsl = "SSL error. Try http:// instead of https://.",

    nCategories = { "$it categories" },
    nHidden = { "$it hidden" },
    nSelected = { "$it selected" },
    versionLabel = { "Version $it" },
    versionAvailable = { "Version $it is available" },
    nChannelsUpdated = { "$it channels updated." },
    nProgramsLoaded = { "$it programmes loaded." },
    activeOfTotal = { active, total -> "$active of $total channels active" },
    missingFields = {
        "Still empty: $it. Open the field with OK, type, close with Back."
    },

    syncSigningIn = "Signing in …",
    syncCategories = "Loading categories …",
    syncChannels = "Loading channels …",
    syncPlaylist = "Loading the playlist …",
    syncGuide = "Loading the TV guide …",
    errNoPlayableChannels = "The playlist holds no playable channels.",
    syncSaving = { "Saving $it channels" },
    syncEntries = { "Processing $it entries …" },
    syncPrograms = { "$it programmes loaded …" },
    errAccountInactive = { "The account is not active (status: $it)." },

    recovery = RecoveryStrings(
        reconnecting = "Reconnecting …",
        tryingOtherFormat = "Trying the other stream format …",
        stillTrying = "The channel is not answering. I'll keep trying — playback starts " +
            "again by itself as soon as it comes back.",
        autoRecover = "Repair stalled streams automatically",
        autoRecoverHint = "When the picture freezes or the connection drops, the app " +
            "reconnects on its own and switches the stream format if it has to. With this " +
            "off you get the error message with “Try again” instead.",
        attempt = { "Attempt $it" }
    ),
    crash = CrashStrings(
        title = "Last error",
        explain = "The app restarted itself after a failure. What it was is written " +
            "here — read me this line if it happens again.",
        whenLabel = "When",
        what = "What",
        forget = "Clear the entry"
    ),
    colors = ColorStrings(
        hint = "Press a coloured key — or pick with ◀▶ and OK",
        previousChannel = "Previous channel",
        fromStart = "From the start",
        audio = "Audio track",
        subtitles = "Subtitles",
        picture = "Picture format",
        noPreviousChannel = "none yet",
        onlyFor = { "Applies to $it only" }
    ),
    tracks = TrackStrings(
        section = "Audio and subtitles",
        audioTitle = "Preferred audio track",
        audioHint = "When a film or a channel carries several languages, the app picks " +
            "this one automatically. When it does not, the track the stream opens with stays.",
        subtitleTitle = "Subtitles",
        subtitleHint = "Turn on subtitles in this language, where the stream carries them. " +
            "“Off” means no subtitles.",
        automatic = "Automatic"
    ),
    vod = VodStrings(
        continueWatching = "Keep watching",
        continueHint = "OK carries on where you stopped",
        clearList = "Clear the list",
        seriesCaption = "Seasons and episodes",
        moviesCaption = "Your provider's film catalogue",
        loading = "Loading …",
        noMovies = "No movies",
        noSeries = "No series",
        noEpisodes = "No episodes",
        emptyCategory = "This category is empty.",
        onlyXtream = "Movies and series only come with Xtream accounts — an M3U playlist " +
            "brings no catalogue with it.",
        play = "Play",
        playFromStart = "Play from the start",
        plot = "Plot",
        cast = "Cast",
        director = "Director",
        genre = "Genre",
        released = "Released",
        duration = "Length",
        rating = "Rating",
        seasonsTitle = "Seasons",
        episodesTitle = "Episodes",
        gridHint = "OK opens · ◀ back to the categories",
        episodeHint = "OK plays the episode",
        playerHint = "OK bar · ◀▶ 30 s · Back ends",
        season = { "Season $it" },
        episode = { "Episode $it" },
        nSeasons = { "$it seasons" },
        nEpisodes = { "$it episodes" },
        nTitles = { "$it titles" },
        continueAt = { "Continue at $it" },
        search = "Search",
        searchHint = "Searching starts at two letters",
        searchPrompt = "What are you looking for?",
        searching = "Searching \u2026",
        noResults = "Nothing found",
        noResultsHint = "Try a different spelling, or fewer letters.",
        favorites = "Favourites",
        noFavorites = "No favourites yet",
        favoritesHint = "Open a title and choose \u201cSave\u201d \u2014 it will be waiting here.",
        addFavorite = "Save",
        removeFavorite = "Saved \u2713",
        nextEpisode = "Next episode",
        lastEpisode = "Last episode of this season",
        autoNext = "Play episodes automatically",
        autoNextHint = "When an episode ends the next one starts by itself \u2014 " +
            "for as long as the season has another.",
        nResults = { "$it results" }
    ),
    parental = ParentalStrings(
        title = "Child lock",
        tileHint = "PIN, locked categories, keywords",
        explain = "Locked categories disappear everywhere \u2014 in live TV, in the films " +
            "and in the series. Nothing is asked for; they are simply not there. " +
            "Whoever knows the PIN can let them back in here at any time.",
        enable = "Turn the child lock on",
        enableHint = "Off means everything is visible. The PIN and the list stay saved " +
            "and apply again the moment you switch it back on.",
        pinTitle = "PIN",
        pinHint = "Four digits. Without them nobody gets back into this section.",
        setPin = "Set a PIN",
        changePin = "Change PIN",
        enterPin = "Enter PIN",
        newPin = "New PIN",
        repeatPin = "Repeat PIN",
        save = "Save",
        wrongPin = "Wrong PIN",
        pinMismatch = "The two entries are not the same",
        pinTooShort = "The PIN has to be four digits",
        pinSaved = "PIN saved",
        noPinYet = "No PIN yet \u2014 anyone can walk in here",
        pinSet = "A PIN is set",
        keywordsTitle = "Keywords",
        keywordsHint = "Any category whose name contains one of these words is hidden \u2014 " +
            "including ones the provider has yet to create.",
        addKeyword = "Add",
        newKeyword = "New keyword",
        categoriesTitle = "Individual categories",
        categoriesHint = "On top of the keywords: lock channel-list categories by hand here.",
        noCategories = "No categories loaded",
        locked = "Locked",
        free = "Open",
        nBlocked = { "$it locked" }
    ),
    hub = HubStrings(
        hint = "OK opens a section \u00b7 Back returns here",
        source = "Source",
        sourceHint = "Account, reload playlist, EPG",
        playback = "Playback",
        playbackHint = "Buffer, stream format, self-repair",
        display = "Display",
        displayHint = "Clock, preview, sorting, start-up",
        groups = "Your groups",
        groupsHint = "Build lists and choose what Live TV opens",
        order = "Order",
        orderHint = "Sort the categories",
        updates = "Updates",
        updatesHint = "Look for a newer version",
        account = "Account",
        accountHint = "Disconnect and set up again",
        about = "About the app",
        aboutHint = "Version and notes",
        problems = "Last error",
        problemsHint = "What went wrong last time, in plain words"
    ),
    info = AboutStrings(
        tagline = "Television for the whole family — switch on and watch.",
        body = "Karacast IPTV plays your own IPTV account on the television: live " +
            "channels with a programme guide, and films and series where your provider " +
            "supplies a catalogue.\n\n" +
            "Built for the remote, and for people who would rather not read a manual " +
            "first: large type, clear tiles, the coloured keys where a receiver has " +
            "always put them. When a channel stalls the app repairs it by itself instead " +
            "of putting up an error.\n\n" +
            "The interface is available in German, Turkish, English and Arabic.",
        legal = "Karacast IPTV is a player. The app contains, brokers and sells no " +
            "channels, no films and no series — it shows only what is delivered by the " +
            "account you enter yourself.\n\n" +
            "What that account is used for, and whether it is permitted where you live, " +
            "is your decision and your responsibility. No liability is accepted for the " +
            "content, the availability or the lawfulness of the source you enter.\n\n" +
            "The app collects no usage data and sends nothing to third parties. " +
            "Credentials, favourites and settings stay on this device.",
        licensesTitle = "Software used",
        licenses = "Karacast IPTV stands on free software: AndroidX Media3 / ExoPlayer, " +
            "Jetpack Compose and Compose for TV, Room, OkHttp and Coil — all under the " +
            "Apache License 2.0 — and FFmpeg, included by way of NextLib, under the " +
            "LGPL 2.1 or later. The licence texts and the source of the FFmpeg libraries " +
            "are available on request."
    ),
    update = UpdateStrings(
        available = "A newer version is available",
        installNow = "Update now",
        later = "Later",
        skipVersion = "Skip this version",
        downloading = "Downloading …",
        readyToInstall = "Done — the installer is opening",
        installHint = "Android will ask for itself whether to install. The first time, this " +
            "app also has to be allowed to install apps — the television will take you " +
            "there.",
        failed = "The download did not work",
        autoCheck = "Look for updates at start-up",
        autoCheckHint = "Once a day, when the app is opened. Anything found is only offered " +
            "— nothing is ever installed on its own.",
        percent = { "$it %" },
        versionIsOut = { "Version $it is out" }
    ),
    panel = PanelStrings(
        title = "Panel",
        tileHint = "Receive the account automatically",
        explain = "Later the panel hands out the accounts: the television reports in with " +
            "its device code, is sent the server, user name and password, and sets itself " +
            "up. Nobody has to type anything any more.\n\n" +
            "As long as no address is entered here, nothing happens — the app connects to " +
            "nobody and sends nothing.",
        address = "Panel address",
        code = "Device code",
        codeHint = "The code the panel shows for this television.",
        connect = "Connect now",
        notConfigured = "No panel set up",
        deviceId = "Device identifier",
        deviceIdHint = "Assigned at random so the panel recognises this television again. " +
            "There is no person behind it.",
        lastSync = "Last connected",
        applySource = "Take the account",
        nothingReceived = "The panel has no account for this code.",
        insecure = "This address is unencrypted (http). The user name and password would be " +
            "readable on the network — please move to https for everyday use.",
        failed = "The panel could not be reached",
        connected = { "Connected — $it" },
        sourceReceived = { "Account “$it” taken over" }
    ),
    license = LicenseStrings(
        section = "Licence",
        tileHint = "Trial, activation, device code",
        expiredTitle = "The trial is over",
        blockedTitle = "This licence is locked",
        expiredBody = "Fourteen free days are up. If you liked Karacast, unlock it once " +
            "— no subscription, no monthly cost, and the unlock holds for this device " +
            "for as long as it stands there.",
        blockedBody = "The licence for this device was withdrawn. If you think that is a " +
            "mistake, get in touch with the device code below.",
        price = "9.99 € once",
        howTo = "Scan the code with your phone — the page already knows your device. " +
            "The moment it is paid, the television unlocks itself.",
        deviceCode = "Device code",
        waiting = "Waiting for the payment …",
        checkNow = "Check now",
        redeemTitle = "Or enter a code",
        redeemHint = "Eight characters, if you were sent them by e-mail.",
        redeem = "Redeem",
        activated = "Unlocked — thank you!",
        failed = "That did not work. Is the code right?",
        noServer = "No licence address is stored.",
        status = "Status",
        statusTrial = "Trial",
        statusActive = "Unlocked",
        statusExpired = "Expired",
        statusBlocked = "Locked",
        serverAddress = "Licence address",
        testingTitle = "For trying it out",
        testingHint = "Only in the developer build. The finished APK has no such buttons.",
        resetTrial = "Reset the trial",
        expireTrial = "End the trial",
        simulateActive = "Simulate an unlock",
        daysLeft = { "Trial — $it days left" },
        lastDay = "Trial — last day",
        activatedOn = { "Unlocked on $it" }
    )
)

private val Arabic = Strings(
    back = "رجوع",
    home = "الرئيسية",
    search = "بحث",
    guide = "دليل البرامج",
    settings = "الإعدادات",
    channels = "القنوات",
    off = "إيقاف",
    on = "تشغيل",
    none = "لا يوجد",
    live = "مباشر",

    liveTv = "البث المباشر",
    liveTvCaption = "كل القنوات والمجموعات",
    series = "المسلسلات",
    movies = "الأفلام",
    comingSoon = "قريباً",
    seriesLater = "ستتوفر المسلسلات في إصدار لاحق.",
    moviesLater = "ستتوفر الأفلام في إصدار لاحق.",

    favorites = "المفضلة",
    recentlyWatched = "شوهدت مؤخراً",
    allChannels = "كل القنوات",
    searchChannel = "ابحث عن قناة",
    typeName = "اكتب الاسم …",
    noChannels = "لا توجد قنوات",
    emptyList = "هذه القائمة فارغة. زر الرجوع يعيدك إلى النظرة العامة.",
    noChannelSelected = "لم يتم اختيار قناة",
    nextUp = "بعد ذلك",
    favorite = "مفضلة",
    jumpNow = "OK ينتقل فوراً",
    switchNow = "OK يبدّل فوراً",

    playerHint = "▲▼ القناة · OK القائمة · ◀▶ المعلومات",
    playbackFailed = "فشل التشغيل",
    retry = "حاول مرة أخرى",
    recording = "تسجيل",
    channelNotFound = "لم يتم العثور على القناة.",
    noArchive = "لا يوجد أرشيف لهذا المصدر.",
    errNetwork = "تعذّر الوصول إلى البث. تحقق من الشبكة أو الخادم.",
    errRejected = "رفض الخادم البث. غالباً بسبب عدد كبير من الاتصالات في وقت واحد.",
    errNotFound = "هذه القناة غير متوفرة لدى المزوّد.",
    errFormat = "لم يتم التعرف على صيغة البث. جرّب HLS بدل TS في الإعدادات.",
    errDecoder = "مفكك الترميز في هذا الجهاز لا يتعامل مع هذا البث.",

    options = "الخيارات",
    audioTrack = "المسار الصوتي",
    subtitles = "الترجمة",
    aspect = "نسبة الصورة",
    sleepTimer = "مؤقت النوم",
    backCloses = "زر الرجوع يغلق",
    backOneLevel = "زر الرجوع يصعد مستوى واحداً",
    nothingToChoose = "لا يوجد ما يمكن اختياره لهذه القناة.",
    addFavorite = "أضف إلى المفضلة",
    removeFavorite = "أزل من المفضلة",
    aspectFit = "ملاءمة",
    aspectFill = "ملء",
    aspectZoom = "تكبير",
    aspectFitHint = "الصورة كاملة، مع أشرطة عند اللزوم",
    aspectFillHint = "الصورة ممتدة إلى الإطار",
    aspectZoomHint = "بلا أشرطة، مع اقتصاص الحواف",
    stereo = "ستيريو",
    mono = "أحادي",

    tvGuide = "دليل البرامج",
    guideHint = "OK يشغّل القناة · برنامج سابق مع أرشيف يبدأ التسجيل",
    now = "الآن",
    noGuideData = "لا توجد بيانات برامج",
    loadingGuide = "جارٍ تحميل دليل البرامج …",
    oneMoment = "لحظة من فضلك.",
    loadPlaylistFirst = "حمّل قائمة التشغيل أولاً.",
    archive = "أرشيف",

    nChannels = { "$it قناة" },
    stillRunning = { "يتبقى $it" },
    numberMissing = { "الرقم $it غير موجود" },
    nMinutes = { "$it دقيقة" },
    nMinutesShort = { "$it د" },
    trackNumber = { "المسار $it" },
    startingUp = "جارٍ تشغيل Karacast IPTV …",

    sourceSection = "المصدر",
    name = "الاسم",
    type = "النوع",
    m3uPlaylist = "قائمة تشغيل M3U",
    server = "الخادم",
    lastLoaded = "آخر تحميل",
    epgSection = "دليل البرامج",
    available = "متوفر",
    noData = "لا توجد بيانات",
    reloadPlaylist = "إعادة تحميل القائمة",
    refreshEpg = "تحديث دليل البرامج",
    editCredentials = "تغيير بيانات الدخول",
    playbackSection = "التشغيل",
    useHls = "استخدام HLS بدل MPEG-TS",
    useHlsHint = "يفيد عندما تتوقف القنوات بعد ثوانٍ قليلة. أعد تحميل القائمة بعد ذلك.",
    buffer = "المخزن المؤقت",
    bufferHint = "مخزن أكبر = ثبات أعلى، لكن تبديل القنوات أبطأ.",
    userAgent = "User-Agent",
    displaySection = "العرض",
    clockWhilePlaying = "الساعة أثناء التشغيل",
    clockWhilePlayingHint = "ساعة رقمية أعلى اليمين، ظاهرة دائماً.",
    resumeLast = "فتح آخر قناة تمت مشاهدتها عند البدء",
    resumeLastHint = "يبدأ التطبيق بالتشغيل مباشرة عند فتحه. زر الرجوع يعيدك إلى قائمة القنوات.",
    previewWhileBrowsing = "معاينة أثناء التصفح",
    previewWhileBrowsingHint = "تعمل القناة المحددة في اللوحة على اليمين بعد تأخير قصير، " +
        "حتى لا ينشئ التصفح السريع اتصالاً.",
    previewMutedTitle = "معاينة صامتة",
    previewMutedHint = "صورة بلا صوت أثناء التصفح.",
    sortAlphabetically = "ترتيب القنوات أبجدياً",
    sortAlphabeticallyHint = "بدل الترتيب حسب الرقم. يفيد في القوائم الطويلة مثل " +
        "«كل القنوات». في مجموعاتك الخاصة يبقى ترتيبك أنت.",
    startOnBoot = "التشغيل عند تشغيل الجهاز",
    startOnBootHint = "يقلع الجهاز فيظهر Karacast فوراً. لتلفاز مخصص لهذا الغرض وحده.",
    ownGroups = "مجموعاتك الخاصة",
    ownGroupsHint = "اجمع عدة فئات من المزوّد في قائمة واحدة خاصة بك — كل ما يخص الرياضة " +
        "في مجموعة مثلاً. تظهر المجموعات في أعلى الشريط الجانبي.",
    createdCount = "المُنشأة",
    manageGroups = "إدارة المجموعات",
    whatLiveOpens = "ما الذي يفتحه «البث المباشر»",
    whatLiveOpensHint = "اختر قائمة، فيدخل البث المباشر إلى القنوات فوراً — بلا شاشة " +
        "المجموعات والمفضلة والمشاهَد مؤخراً بينهما، مثل جهاز استقبال الأقمار. " +
        "عندها يعود زر الرجوع إلى الشاشة الرئيسية مباشرة.",
    showOverview = "عرض النظرة العامة",
    overviewDefault = "الافتراضي — القائمة أولاً ثم القنوات",
    categoryOrder = "ترتيب الفئات",
    noCategoriesLoaded = "لم يتم تحميل أي فئة بعد.",
    categoryOrderHint = "يحدد ترتيب ظهور الفئات في الشريط الجانبي.",
    accountSection = "الحساب",
    accountHint = "يفصل التطبيق عن المزوّد ويحذف القنوات والمفضلة وبيانات دليل البرامج من هذا الجهاز.",
    disconnect = "قطع الاتصال",
    updates = "التحديثات",
    updatesHint = "لا يأتي التطبيق من متجر، لذلك يسأل بنفسه. أدخل عنوان ملف صغير يحتوي " +
        "رقم الإصدار ورابط التنزيل — بدون عنوان لا يتم جلب شيء.",
    updateAddress = "عنوان التحديث",
    installedVersion = "الإصدار المثبت",
    checkNow = "تحقق الآن",
    aboutSection = "عن التطبيق",
    aboutSectionHint = "من يقف خلف Karacast IPTV، وكيف يمكن التواصل معك، والتفاصيل القانونية.",
    aboutButton = "عن Karacast IPTV",
    disclaimer = "Karacast IPTV — لا يوفّر التطبيق أي محتوى، بل يشغّل قائمة التشغيل الخاصة بك فقط.",

    liveShowsOverviewAgain = "يعرض البث المباشر النظرة العامة مرة أخرى.",
    liveJumpsDirect = "من التشغيل القادم سيدخل البث المباشر إلى هذه القائمة مباشرة.",
    noUpdateAddress = "لم يتم إدخال عنوان تحديث.",
    alreadyLatest = "لديك بالفعل أحدث إصدار.",
    reloadingPlaylist = "جارٍ إعادة تحميل قائمة التشغيل …",
    loadingEpg = "جارٍ تحميل دليل البرامج …",
    noEpgUrl = "لا يوجد عنوان دليل برامج محفوظ لهذا المصدر.",
    noProgramsDelivered = "لم يرسل المزوّد أي برامج مطابقة.",
    noSourceConfigured = "لم يتم إعداد أي مصدر.",
    hlsAfterReload = "يسري بعد إعادة تحميل القائمة القادمة.",

    noGroupYet = "لم يتم إنشاء أي مجموعة بعد.",
    newGroup = "مجموعة جديدة",
    editChannels = "تحرير القنوات",
    editChannelsHint = "أخرج قنوات مفردة من المجموعة وحدد ترتيبها.",
    noGroupSelected = "لم يتم اختيار مجموعة",
    noGroupSelectedHint = "أنشئ مجموعة على اليسار ثم علّم هنا الفئات التي تنتمي إليها.",
    groupName = "اسم المجموعة",
    categoriesInGroup = "الفئات في هذه المجموعة",
    delete = "حذف",
    noCategories = "لا توجد فئات",
    noCategoriesHint = "حمّل قائمة التشغيل أولاً، فتظهر هنا فئات المزوّد.",
    group = "مجموعة",
    groupEditHint = "OK يخفي أو يعيد الإظهار · ▲▼ ينقل",
    showAllChannels = "إظهار الكل",
    resetOrder = "إعادة ضبط الترتيب",
    done = "تم",
    noChannelsInGroup = "لا توجد قنوات في هذه المجموعة",
    noChannelsInGroupHint = "علّم أولاً في محرر المجموعات الفئات التي تنتمي إلى هذه المجموعة.",
    hiddenLabel = "مخفية",

    developer = "المطوّر",
    contact = "التواصل",
    website = "الموقع",
    legal = "القانونية",
    aboutHint = "سيوضع نصك هنا لاحقاً.",
    developerHint = "الاسم، الشركة، الدور.",
    contactHint = "البريد الإلكتروني، الهاتف، واتساب.",
    websiteHint = "عنوان موقعك أو متجرك.",
    legalHint = "بيانات الناشر، المسؤولية، الخصوصية.",
    aboutDisclaimer = "لا يوفّر Karacast IPTV أي محتوى. يشغّل التطبيق فقط قائمة التشغيل " +
        "التي تدخلها بنفسك.",

    connectProvider = "الاتصال بالمزوّد",
    connectProviderHint = "أدخل بيانات الدخول الخاصة بمزوّد IPTV.",
    serverUrl = "عنوان الخادم",
    username = "اسم المستخدم",
    password = "كلمة المرور",
    m3uUrlLabel = "عنوان قائمة M3U",
    epgUrlLabel = "عنوان XMLTV للدليل (اختياري)",
    connectAndLoad = "اتصل وحمّل",
    loginNote = "ملاحظة: لا يحتوي هذا التطبيق على قنوات. تحتاج إلى اشتراك خاص بك لدى " +
        "مزوّد تختاره.",
    longPlaylistHint = "مع القوائم الكبيرة قد يستغرق هذا دقيقة.",
    brandHeadline = "بث مباشر على الشاشة الكبيرة",
    brandText = "حسابات Xtream Codes وقوائم M3U، مع دليل البرامج والمفضلة والتحكم " +
        "عبر جهاز التحكم.",
    myProvider = "مزوّدي",
    connecting = "جارٍ الاتصال …",
    playlistUrl = "عنوان القائمة",
    errHostUnreachable = "تعذّر الوصول إلى الخادم — تحقق من العنوان ومن اتصال الإنترنت.",
    errTimeout = "انتهت المهلة. الخادم لا يستجيب.",
    errAuth = "تم رفض الوصول — اسم المستخدم أو كلمة المرور غير صحيحة.",
    errNotFound404 = "لم يتم العثور على العنوان (404). هل عنوان الخادم صحيح؟",
    errSsl = "خطأ SSL. جرّب http:// بدل https://.",

    nCategories = { "$it فئة" },
    nHidden = { "$it مخفية" },
    nSelected = { "$it محددة" },
    versionLabel = { "الإصدار $it" },
    versionAvailable = { "الإصدار $it متوفر" },
    nChannelsUpdated = { "تم تحديث $it قناة." },
    nProgramsLoaded = { "تم تحميل $it برنامج." },
    activeOfTotal = { active, total -> "$active من $total قناة نشطة" },
    missingFields = {
        "ما زال فارغاً: $it. افتح الحقل بزر OK، اكتب، ثم أغلقه بزر الرجوع."
    },

    syncSigningIn = "جارٍ تسجيل الدخول …",
    syncCategories = "جارٍ تحميل الفئات …",
    syncChannels = "جارٍ تحميل القنوات …",
    syncPlaylist = "جارٍ تحميل قائمة التشغيل …",
    syncGuide = "جارٍ تحميل دليل البرامج …",
    errNoPlayableChannels = "لا تحتوي قائمة التشغيل على قنوات قابلة للتشغيل.",
    syncSaving = { "جارٍ حفظ $it قناة" },
    syncEntries = { "جارٍ معالجة $it إدخال …" },
    syncPrograms = { "تم تحميل $it برنامج …" },
    errAccountInactive = { "الحساب غير نشط (الحالة: $it)." },

    recovery = RecoveryStrings(
        reconnecting = "جارٍ إعادة الاتصال …",
        tryingOtherFormat = "جارٍ تجربة صيغة بث أخرى …",
        stillTrying = "القناة لا تستجيب. سأستمر في المحاولة — سيبدأ التشغيل من تلقاء نفسه " +
            "بمجرد عودتها.",
        autoRecover = "إصلاح البث المتوقف تلقائياً",
        autoRecoverHint = "إذا تجمدت الصورة أو انقطع الاتصال، يعيد التطبيق الاتصال من تلقاء " +
            "نفسه ويغيّر صيغة البث عند اللزوم. وبدون هذا الخيار تظهر رسالة الخطأ مع " +
            "«حاول مرة أخرى» بدلاً من ذلك.",
        attempt = { "المحاولة $it" }
    ),
    crash = CrashStrings(
        title = "آخر خطأ",
        explain = "أعاد التطبيق تشغيل نفسه بعد خطأ. ما حدث مكتوب هنا — أخبرني بهذا " +
            "السطر إذا تكرر الأمر.",
        whenLabel = "متى",
        what = "ماذا",
        forget = "حذف السجل"
    ),
    colors = ColorStrings(
        hint = "اضغط زراً ملوّناً — أو اختر بـ ◀▶ ثم OK",
        previousChannel = "القناة السابقة",
        fromStart = "من البداية",
        audio = "المسار الصوتي",
        subtitles = "الترجمة",
        picture = "نسبة الصورة",
        noPreviousChannel = "لا توجد بعد",
        onlyFor = { "ينطبق على $it فقط" }
    ),
    tracks = TrackStrings(
        section = "الصوت والترجمة",
        audioTitle = "المسار الصوتي المفضّل",
        audioHint = "إذا حمل الفيلم أو القناة عدة لغات، يختار التطبيق هذه تلقائياً. " +
            "وإن لم تتوفر، يبقى المسار الذي يبدأ به البث.",
        subtitleTitle = "الترجمة",
        subtitleHint = "تشغيل الترجمة بهذه اللغة إن كان البث يوفّرها. «إيقاف» تعني بلا ترجمة.",
        automatic = "تلقائي"
    ),
    vod = VodStrings(
        continueWatching = "متابعة المشاهدة",
        continueHint = "زر OK يكمل من حيث توقفت",
        clearList = "إفراغ القائمة",
        seriesCaption = "المواسم والحلقات",
        moviesCaption = "كتالوج أفلام مزوّدك",
        loading = "جارٍ التحميل …",
        noMovies = "لا توجد أفلام",
        noSeries = "لا توجد مسلسلات",
        noEpisodes = "لا توجد حلقات",
        emptyCategory = "هذه الفئة فارغة.",
        onlyXtream = "الأفلام والمسلسلات متاحة فقط مع حسابات Xtream — قائمة M3U لا تجلب " +
            "أي كتالوج.",
        play = "تشغيل",
        playFromStart = "التشغيل من البداية",
        plot = "القصة",
        cast = "الممثلون",
        director = "الإخراج",
        genre = "النوع",
        released = "سنة الإصدار",
        duration = "المدة",
        rating = "التقييم",
        seasonsTitle = "المواسم",
        episodesTitle = "الحلقات",
        gridHint = "OK يفتح · ◀ للعودة إلى الفئات",
        episodeHint = "OK يشغّل الحلقة",
        playerHint = "OK الشريط · ◀▶ ٣٠ ثانية · الرجوع ينهي",
        season = { "الموسم $it" },
        episode = { "الحلقة $it" },
        nSeasons = { "$it موسم" },
        nEpisodes = { "$it حلقة" },
        nTitles = { "$it عنوان" },
        continueAt = { "\u0627\u0644\u0645\u062a\u0627\u0628\u0639\u0629 \u0639\u0646\u062f $it" },
        search = "\u0628\u062d\u062b",
        searchHint = "\u064a\u0628\u062f\u0623 \u0627\u0644\u0628\u062d\u062b \u0645\u0646 \u062d\u0631\u0641\u064a\u0646",
        searchPrompt = "\u0639\u0645\u0651\u0627 \u062a\u0628\u062d\u062b\u061f",
        searching = "\u062c\u0627\u0631\u064d \u0627\u0644\u0628\u062d\u062b \u2026",
        noResults = "\u0644\u0627 \u062a\u0648\u062c\u062f \u0646\u062a\u0627\u0626\u062c",
        noResultsHint = "\u062c\u0631\u0651\u0628 \u0643\u062a\u0627\u0628\u0629 \u0623\u062e\u0631\u0649 \u0623\u0648 \u062d\u0631\u0648\u0641\u0627\u064b \u0623\u0642\u0644\u0651.",
        favorites = "\u0627\u0644\u0645\u0641\u0636\u0651\u0644\u0629",
        noFavorites = "\u0644\u0627 \u062a\u0648\u062c\u062f \u0645\u0641\u0636\u0651\u0644\u0629 \u0628\u0639\u062f",
        favoritesHint = "\u0627\u0641\u062a\u062d \u0639\u0646\u0648\u0627\u0646\u0627\u064b \u0648\u0627\u062e\u062a\u0631 \u00ab\u062d\u0641\u0638\u00bb \u2014 " +
            "\u0641\u064a\u0646\u062a\u0638\u0631\u0643 \u0647\u0646\u0627.",
        addFavorite = "\u062d\u0641\u0638",
        removeFavorite = "\u0645\u062d\u0641\u0648\u0638 \u2713",
        nextEpisode = "\u0627\u0644\u062d\u0644\u0642\u0629 \u0627\u0644\u062a\u0627\u0644\u064a\u0629",
        lastEpisode = "\u0622\u062e\u0631 \u062d\u0644\u0642\u0629 \u0641\u064a \u0647\u0630\u0627 \u0627\u0644\u0645\u0648\u0633\u0645",
        autoNext = "\u062a\u0634\u063a\u064a\u0644 \u0627\u0644\u062d\u0644\u0642\u0627\u062a \u062a\u0644\u0642\u0627\u0626\u064a\u0627\u064b",
        autoNextHint = "\u0639\u0646\u062f \u0627\u0646\u062a\u0647\u0627\u0621 \u062d\u0644\u0642\u0629 \u062a\u0628\u062f\u0623 \u0627\u0644\u062a\u0627\u0644\u064a\u0629 \u0648\u062d\u062f\u0647\u0627 \u2014 " +
            "\u0645\u0627 \u062f\u0627\u0645\u062a \u0641\u064a \u0627\u0644\u0645\u0648\u0633\u0645 \u062d\u0644\u0642\u0629 \u0623\u062e\u0631\u0649.",
        nResults = { "$it \u0646\u062a\u064a\u062c\u0629" }
    ),
    parental = ParentalStrings(
        title = "\u0642\u0641\u0644 \u0627\u0644\u0623\u0637\u0641\u0627\u0644",
        tileHint = "\u0631\u0645\u0632 \u0627\u0644\u0645\u0631\u0648\u0631\u060c \u0627\u0644\u0641\u0626\u0627\u062a \u0627\u0644\u0645\u0642\u0641\u0644\u0629\u060c \u0627\u0644\u0643\u0644\u0645\u0627\u062a",
        explain = "\u0627\u0644\u0641\u0626\u0627\u062a \u0627\u0644\u0645\u0642\u0641\u0644\u0629 \u062a\u062e\u062a\u0641\u064a \u0641\u064a \u0643\u0644 \u0645\u0643\u0627\u0646 \u2014 " +
            "\u0641\u064a \u0627\u0644\u0628\u062b \u0627\u0644\u0645\u0628\u0627\u0634\u0631 \u0648\u0641\u064a \u0627\u0644\u0623\u0641\u0644\u0627\u0645 \u0648\u0627\u0644\u0645\u0633\u0644\u0633\u0644\u0627\u062a. " +
            "\u0644\u0627 \u064a\u064f\u0637\u0644\u0628 \u0634\u064a\u0621\u060c \u0628\u0644 \u0644\u0645 \u062a\u0639\u062f \u0645\u0648\u062c\u0648\u062f\u0629.",
        enable = "\u062a\u0634\u063a\u064a\u0644 \u0642\u0641\u0644 \u0627\u0644\u0623\u0637\u0641\u0627\u0644",
        enableHint = "\u0639\u0646\u062f \u0627\u0644\u0625\u064a\u0642\u0627\u0641 \u064a\u0638\u0647\u0631 \u0643\u0644 \u0634\u064a\u0621. " +
            "\u064a\u0628\u0642\u0649 \u0627\u0644\u0631\u0645\u0632 \u0648\u0627\u0644\u0642\u0627\u0626\u0645\u0629 \u0645\u062d\u0641\u0648\u0638\u064a\u0646.",
        pinTitle = "\u0631\u0645\u0632 \u0627\u0644\u0645\u0631\u0648\u0631",
        pinHint = "\u0623\u0631\u0628\u0639\u0629 \u0623\u0631\u0642\u0627\u0645. \u0628\u062f\u0648\u0646\u0647 \u0644\u0627 \u0623\u062d\u062f \u064a\u062f\u062e\u0644 \u0647\u0646\u0627.",
        setPin = "\u062a\u0639\u064a\u064a\u0646 \u0631\u0645\u0632",
        changePin = "\u062a\u063a\u064a\u064a\u0631 \u0627\u0644\u0631\u0645\u0632",
        enterPin = "\u0623\u062f\u062e\u0644 \u0627\u0644\u0631\u0645\u0632",
        newPin = "\u0631\u0645\u0632 \u062c\u062f\u064a\u062f",
        repeatPin = "\u0623\u0639\u062f \u0627\u0644\u0631\u0645\u0632",
        save = "\u062d\u0641\u0638",
        wrongPin = "\u0631\u0645\u0632 \u062e\u0627\u0637\u0626",
        pinMismatch = "\u0627\u0644\u0625\u062f\u062e\u0627\u0644\u0627\u0646 \u063a\u064a\u0631 \u0645\u062a\u0637\u0627\u0628\u0642\u064a\u0646",
        pinTooShort = "\u064a\u062c\u0628 \u0623\u0646 \u064a\u0643\u0648\u0646 \u0627\u0644\u0631\u0645\u0632 \u0623\u0631\u0628\u0639\u0629 \u0623\u0631\u0642\u0627\u0645",
        pinSaved = "\u062a\u0645 \u062d\u0641\u0638 \u0627\u0644\u0631\u0645\u0632",
        noPinYet = "\u0644\u0627 \u064a\u0648\u062c\u062f \u0631\u0645\u0632 \u0628\u0639\u062f",
        pinSet = "\u0627\u0644\u0631\u0645\u0632 \u0645\u0636\u0628\u0648\u0637",
        keywordsTitle = "\u0627\u0644\u0643\u0644\u0645\u0627\u062a \u0627\u0644\u062f\u0627\u0644\u0651\u0629",
        keywordsHint = "\u062a\u064f\u062e\u0641\u0649 \u0643\u0644 \u0641\u0626\u0629 \u064a\u0631\u062f \u0641\u064a \u0627\u0633\u0645\u0647\u0627 \u0625\u062d\u062f\u0649 \u0647\u0630\u0647 \u0627\u0644\u0643\u0644\u0645\u0627\u062a.",
        addKeyword = "\u0625\u0636\u0627\u0641\u0629",
        newKeyword = "\u0643\u0644\u0645\u0629 \u062c\u062f\u064a\u062f\u0629",
        categoriesTitle = "\u0641\u0626\u0627\u062a \u0645\u0641\u0631\u062f\u0629",
        categoriesHint = "\u0625\u0644\u0649 \u062c\u0627\u0646\u0628 \u0627\u0644\u0643\u0644\u0645\u0627\u062a: \u0627\u0642\u0641\u0644 \u0641\u0626\u0627\u062a \u0642\u0627\u0626\u0645\u0629 \u0627\u0644\u0642\u0646\u0648\u0627\u062a \u064a\u062f\u0648\u064a\u0627\u064b.",
        noCategories = "\u0644\u0627 \u062a\u0648\u062c\u062f \u0641\u0626\u0627\u062a",
        locked = "\u0645\u0642\u0641\u0644\u0629",
        free = "\u0645\u0641\u062a\u0648\u062d\u0629",
        nBlocked = { "$it \u0645\u0642\u0641\u0644\u0629" }
    ),
    hub = HubStrings(
        hint = "\u0645\u0648\u0627\u0641\u0642 \u064a\u0641\u062a\u062d \u0642\u0633\u0645\u0627\u064b \u00b7 \u0631\u062c\u0648\u0639 \u064a\u0639\u0648\u062f \u0625\u0644\u0649 \u0647\u0646\u0627",
        source = "\u0627\u0644\u0645\u0635\u062f\u0631",
        sourceHint = "\u0627\u0644\u062d\u0633\u0627\u0628\u060c \u0625\u0639\u0627\u062f\u0629 \u0627\u0644\u062a\u062d\u0645\u064a\u0644\u060c \u0627\u0644\u062f\u0644\u064a\u0644",
        playback = "\u0627\u0644\u062a\u0634\u063a\u064a\u0644",
        playbackHint = "\u0627\u0644\u062a\u062e\u0632\u064a\u0646 \u0627\u0644\u0645\u0624\u0642\u062a \u0648\u0635\u064a\u063a\u0629 \u0627\u0644\u0628\u062b",
        display = "\u0627\u0644\u0639\u0631\u0636",
        displayHint = "\u0627\u0644\u0633\u0627\u0639\u0629 \u0648\u0627\u0644\u0645\u0639\u0627\u064a\u0646\u0629 \u0648\u0627\u0644\u062a\u0631\u062a\u064a\u0628",
        groups = "\u0645\u062c\u0645\u0648\u0639\u0627\u062a\u0643",
        groupsHint = "\u0642\u0648\u0627\u0626\u0645\u0643 \u0648\u0645\u0627 \u064a\u0641\u062a\u062d\u0647 \u0627\u0644\u0628\u062b \u0627\u0644\u0645\u0628\u0627\u0634\u0631",
        order = "\u0627\u0644\u062a\u0631\u062a\u064a\u0628",
        orderHint = "\u062a\u0631\u062a\u064a\u0628 \u0627\u0644\u0641\u0626\u0627\u062a",
        updates = "\u0627\u0644\u062a\u062d\u062f\u064a\u062b\u0627\u062a",
        updatesHint = "\u0627\u0644\u0628\u062d\u062b \u0639\u0646 \u0625\u0635\u062f\u0627\u0631 \u0623\u062d\u062f\u062b",
        account = "\u0627\u0644\u062d\u0633\u0627\u0628",
        accountHint = "\u0642\u0637\u0639 \u0627\u0644\u0627\u062a\u0635\u0627\u0644 \u0648\u0625\u0639\u0627\u062f\u0629 \u0627\u0644\u0625\u0639\u062f\u0627\u062f",
        about = "\u0639\u0646 \u0627\u0644\u062a\u0637\u0628\u064a\u0642",
        aboutHint = "\u0627\u0644\u0625\u0635\u062f\u0627\u0631 \u0648\u0627\u0644\u0645\u0644\u0627\u062d\u0638\u0627\u062a",
        problems = "\u0622\u062e\u0631 \u062e\u0637\u0623",
        problemsHint = "\u0645\u0627 \u0627\u0644\u0630\u064a \u0623\u062e\u0641\u0642 \u0622\u062e\u0631 \u0645\u0631\u0629"
    ),
    info = AboutStrings(
        tagline = "تلفاز لكل العائلة — شغّله وشاهد.",
        body = "يشغّل Karacast IPTV اشتراكك الخاص على التلفاز: قنوات مباشرة مع دليل " +
            "البرامج، وأفلام ومسلسلات إن كان مزوّدك يوفّر كتالوجاً.\n\n" +
            "مصنوع لجهاز التحكّم، ولمن لا يريد قراءة دليل أولاً: خط كبير، مربّعات واضحة، " +
            "والأزرار الملوّنة حيث اعتاد المستقبِل أن يضعها. وإذا تعثّرت قناة أصلحها " +
            "التطبيق من تلقاء نفسه بدل عرض رسالة خطأ.\n\n" +
            "الواجهة متوفّرة بالألمانية والتركية والإنجليزية والعربية.",
        legal = "Karacast IPTV مشغّل فحسب. لا يحتوي التطبيق على قنوات أو أفلام أو " +
            "مسلسلات ولا يبيعها ولا يتوسّط فيها — بل يعرض ما يقدّمه الاشتراك الذي " +
            "تُدخله أنت لا غير.\n\n" +
            "أمّا فيمَ يُستعمل هذا الاشتراك وهل هو مسموح حيث تقيم، فذلك قرارك " +
            "ومسؤوليتك. ولا تُقبل أي مسؤولية عن محتوى المصدر المُدخَل أو توفّره أو " +
            "مشروعيته.\n\n" +
            "لا يجمع التطبيق بيانات استعمال ولا يرسل شيئاً إلى أطراف أخرى. تبقى بيانات " +
            "الدخول والمفضّلة والإعدادات على هذا الجهاز.",
        licensesTitle = "البرمجيات المستعملة",
        licenses = "يقوم Karacast IPTV على برمجيات حرّة: AndroidX Media3 / ExoPlayer " +
            "وJetpack Compose وCompose for TV وRoom وOkHttp وCoil — جميعها برخصة " +
            "Apache 2.0 — إضافة إلى FFmpeg عبر NextLib برخصة LGPL 2.1 أو ما بعدها. " +
            "نصوص الرخص والشيفرة المصدرية لمكتبات FFmpeg متاحة عند الطلب."
    ),
    update = UpdateStrings(
        available = "يتوفّر إصدار أحدث",
        installNow = "حدِّث الآن",
        later = "لاحقاً",
        skipVersion = "تخطَّ هذا الإصدار",
        downloading = "جارٍ التنزيل …",
        readyToInstall = "تمّ — يُفتح المثبِّت",
        installHint = "سيسأل أندرويد بنفسه عن التثبيت. وفي المرّة الأولى يجب السماح لهذا " +
            "التطبيق بتثبيت التطبيقات — والتلفاز يأخذك إلى هناك.",
        failed = "لم ينجح التنزيل",
        autoCheck = "البحث عن تحديثات عند البدء",
        autoCheckHint = "مرّة في اليوم عند فتح التطبيق. وما يُعثر عليه يُعرض فقط — ولا " +
            "يُثبَّت شيء من تلقاء نفسه.",
        percent = { "$it %" },
        versionIsOut = { "صدر الإصدار $it" }
    ),
    panel = PanelStrings(
        title = "اللوحة",
        tileHint = "استلام الاشتراك تلقائياً",
        explain = "لاحقاً ستوزّع اللوحة الاشتراكات: يبلّغ التلفاز برمز جهازه، فيُرسَل إليه " +
            "الخادم واسم المستخدم وكلمة المرور فيُعِدّ نفسه. ولا يعود أحد مضطراً لكتابة " +
            "شيء.\n\n" +
            "وما دام لا عنوان هنا فلا يحدث شيء — لا يتّصل التطبيق بأحد ولا يرسل شيئاً.",
        address = "عنوان اللوحة",
        code = "رمز الجهاز",
        codeHint = "الرمز الذي تعرضه اللوحة لهذا التلفاز.",
        connect = "اتّصل الآن",
        notConfigured = "لم تُعدّ أي لوحة",
        deviceId = "معرّف الجهاز",
        deviceIdHint = "يُمنح عشوائياً لتتعرّف اللوحة على هذا التلفاز مجدّداً. لا شخص وراءه.",
        lastSync = "آخر اتّصال",
        applySource = "استلام الاشتراك",
        nothingReceived = "لا يوجد في اللوحة اشتراك لهذا الرمز.",
        insecure = "هذا العنوان غير مشفّر (http). سيكون اسم المستخدم وكلمة المرور قابلين " +
            "للقراءة على الشبكة — يُرجى الانتقال إلى https للاستعمال الدائم.",
        failed = "تعذّر الوصول إلى اللوحة",
        connected = { "متّصل — $it" },
        sourceReceived = { "تمّ استلام اشتراك «$it»" }
    ),
    license = LicenseStrings(
        section = "الترخيص",
        tileHint = "المدّة التجريبية، التفعيل، رمز الجهاز",
        expiredTitle = "انتهت المدّة التجريبية",
        blockedTitle = "هذا الترخيص موقوف",
        expiredBody = "انقضت أربعة عشر يوماً مجّانية. إن أعجبك Karacast ففعّله مرّة " +
            "واحدة — بلا اشتراك ولا كلفة شهرية، والتفعيل يبقى لهذا الجهاز ما بقي.",
        blockedBody = "سُحب ترخيص هذا الجهاز. إن كنت ترى في ذلك خطأً فتواصل معنا مستعيناً " +
            "برمز الجهاز أدناه.",
        price = "‎9,99 €‎ مرّة واحدة",
        howTo = "امسح الرمز بالهاتف — الصفحة تعرف جهازك سلفاً. وما إن يتمّ الدفع حتى " +
            "يفتح التلفاز نفسه.",
        deviceCode = "رمز الجهاز",
        waiting = "في انتظار الدفع …",
        checkNow = "تحقّق الآن",
        redeemTitle = "أو أدخل رمزاً",
        redeemHint = "ثمانية أحرف، إن وصلك بالبريد.",
        redeem = "استخدام",
        activated = "تمّ التفعيل — شكراً لك!",
        failed = "لم ينجح. هل الرمز صحيح؟",
        noServer = "لا يوجد عنوان ترخيص محفوظ.",
        status = "الحالة",
        statusTrial = "نسخة تجريبية",
        statusActive = "مفعّل",
        statusExpired = "منتهٍ",
        statusBlocked = "موقوف",
        serverAddress = "عنوان الترخيص",
        testingTitle = "للتجربة",
        testingHint = "في نسخة المطوّر فقط. لا وجود لهذه الأزرار في التطبيق النهائي.",
        resetTrial = "إعادة ضبط المدّة التجريبية",
        expireTrial = "إنهاء المدّة التجريبية",
        simulateActive = "محاكاة التفعيل",
        daysLeft = { "نسخة تجريبية — بقي $it يوماً" },
        lastDay = "نسخة تجريبية — اليوم الأخير",
        activatedOn = { "فُعّل في $it" }
    )
)

fun stringsFor(language: AppLanguage): Strings = when (language) {
    AppLanguage.DE -> German
    AppLanguage.TR -> Turkish
    AppLanguage.EN -> English
    AppLanguage.AR -> Arabic
}

private val German = Strings()

/**
 * The chosen language, held for the whole process so that ViewModels can speak it
 * too. Switching writes here and every screen recomposes at once — no restart, no
 * activity dance, which is what makes a flag row on the dashboard worth having.
 */
object AppLocale {

    private val _current = MutableStateFlow(AppLanguage.DE)
    val current: StateFlow<AppLanguage> = _current.asStateFlow()

    /** For ViewModels, which have no composition to read [LocalStrings] from. */
    val strings: Strings get() = stringsFor(_current.value)

    fun set(language: AppLanguage) {
        _current.value = language
        // Dates and clock formatting come from java.text, which reads this.
        Locale.setDefault(Locale.forLanguageTag(language.tag))
    }
}

val LocalStrings = staticCompositionLocalOf { German }
