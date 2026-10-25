package com.safir.iptv.mobile

import androidx.compose.runtime.staticCompositionLocalOf
import com.safir.iptv.ui.i18n.AppLanguage

/**
 * Die Wörter, die nur die Handy-App braucht.
 *
 * Alles, was beide Apps sagen — Tonspur, Favoriten, die Fehlermeldungen, die
 * Lizenz- und Play-Texte —, kommt weiter aus [com.safir.iptv.ui.i18n.Strings].
 * Hier steht nur, was es am Fernseher nicht gibt: Tippen statt OK, Mini-Player,
 * Blätter von unten. Deutsch steht in den Vorgaben, die drei anderen Sprachen
 * überschreiben jedes Feld — so fehlt keine Übersetzung unbemerkt.
 */
data class MobileStrings(
    val nChannels: (Int) -> String = { "$it Kanäle" },
    /** Gruß oben auf der Startseite, nach Tageszeit: Morgen, Tag, Abend, Nacht. */
    val greetings: List<String> = listOf("Guten Morgen", "Guten Tag", "Guten Abend", "Gute Nacht"),
    val greetingHint: String = "Was möchtest du sehen?",
    val seeAll: String = "Alle",
    val emptyStartTitle: String = "Los geht's",
    val emptyStart: String = "Tippe unten auf Live TV und wähle einen Kanal — " +
        "was du zuletzt gesehen hast, steht dann hier.",
    val fullscreen: String = "Vollbild",
    val exitFullscreen: String = "Vollbild beenden",
    val moreEpisodes: String = "Weitere Folgen",
    val minimize: String = "Verkleinern",
    val close: String = "Schließen",
    val play: String = "Abspielen",
    val pause: String = "Pause",
    val previousChannel: String = "Vorheriger Kanal",
    val nextChannel: String = "Nächster Kanal",
    val channelList: String = "Kanalliste",
    val tapHint: String = "Tippen: Vorschau · nochmal tippen: Vollbild",
    val rewind: String = "10 Sekunden zurück",
    val forward: String = "10 Sekunden vor",
    val searchMovies: String = "Film suchen",
    val searchSeries: String = "Serie suchen",
    val resumeEpisode: (Int, Int) -> String = { s, e -> "Weiterschauen · S$s F$e" },
    val startSeries: String = "Folge 1 abspielen",
    val watchLive: String = "Einschalten",
    val watchFromStart: String = "Von vorn ansehen",
    val remind: String = "Erinnern",
    val reminderOn: String = "Erinnerung an",
    val noGuide: String = "Für diese Kanäle gibt es noch kein Programm.",
    val noGuideHint: String = "In den Einstellungen unter „Zugang“ lässt es sich laden.",
    val sectionAccess: String = "Zugang",
    val sectionSubscription: String = "Abo & Lizenz",
    val sectionLanguage: String = "Sprache",
    val autoRecover: String = "Bei Abbruch automatisch neu verbinden",
    val audioPref: String = "Bevorzugte Tonspur",
    val subtitlePref: String = "Bevorzugte Untertitel",
    val automatic: String = "Automatisch",
    val unlockTitle: String = "Karacast freischalten",
    val unlockEarly: (Int) -> String = {
        "Du testest Karacast noch $it Tage. Schalte schon jetzt frei, dann läuft " +
            "danach einfach alles weiter."
    },
    val perks: List<String> = listOf(
        "Live TV, Filme und Serien deines Anbieters",
        "Programmführer mit Erinnerungen",
        "Keine Werbung"
    ),
    val activeTitle: String = "Karacast ist freigeschaltet",
    val buyOnline: String = "Lizenz online kaufen",
    val buyHint: String = "Öffnet karacast.de mit dem Code dieses Handys. Sobald bezahlt " +
        "ist, schaltet sich die App von selbst frei.",
    val setupTitle: String = "Zugang einrichten",
    val xtream: String = "Xtream Codes",
    val m3u: String = "M3U-Link",
    val showPassword: String = "Passwort anzeigen",
    val hidePassword: String = "Passwort verbergen"
)

private val MobileGerman = MobileStrings()

private val MobileTurkish = MobileStrings(
    nChannels = { "$it kanal" },
    greetings = listOf("Günaydın", "İyi günler", "İyi akşamlar", "İyi geceler"),
    greetingHint = "Ne izlemek istersin?",
    seeAll = "Tümü",
    emptyStartTitle = "Haydi başlayalım",
    emptyStart = "Aşağıda Canlı TV'ye dokun ve bir kanal seç — en son izlediklerin " +
        "burada görünür.",
    fullscreen = "Tam ekran",
    exitFullscreen = "Tam ekrandan çık",
    moreEpisodes = "Sonraki bölümler",
    minimize = "Küçült",
    close = "Kapat",
    play = "Oynat",
    pause = "Duraklat",
    previousChannel = "Önceki kanal",
    nextChannel = "Sonraki kanal",
    channelList = "Kanal listesi",
    tapHint = "Dokun: önizleme · tekrar dokun: tam ekran",
    rewind = "10 saniye geri",
    forward = "10 saniye ileri",
    searchMovies = "Film ara",
    searchSeries = "Dizi ara",
    resumeEpisode = { s, e -> "Devam et · S$s B$e" },
    startSeries = "1. bölümü oynat",
    watchLive = "İzle",
    watchFromStart = "Baştan izle",
    remind = "Hatırlat",
    reminderOn = "Hatırlatma açık",
    noGuide = "Bu kanallar için henüz yayın akışı yok.",
    noGuideHint = "Ayarlar'da „Erişim“ altından yüklenebilir.",
    sectionAccess = "Erişim",
    sectionSubscription = "Abonelik ve lisans",
    sectionLanguage = "Dil",
    autoRecover = "Kesintide otomatik yeniden bağlan",
    audioPref = "Tercih edilen ses",
    subtitlePref = "Tercih edilen altyazı",
    automatic = "Otomatik",
    unlockTitle = "Karacast'in kilidini aç",
    unlockEarly = {
        "Karacast'i $it gün daha deniyorsun. Şimdiden kilidi açarsan sonrasında her " +
            "şey kesintisiz devam eder."
    },
    perks = listOf(
        "Sağlayıcının canlı TV, film ve dizileri",
        "Hatırlatmalı yayın akışı",
        "Reklam yok"
    ),
    activeTitle = "Karacast'in kilidi açık",
    buyOnline = "Lisansı çevrimiçi satın al",
    buyHint = "karacast.de bu telefonun koduyla açılır. Ödeme yapılınca uygulama kendi " +
        "kendine açılır.",
    setupTitle = "Erişimi ayarla",
    xtream = "Xtream Codes",
    m3u = "M3U bağlantısı",
    showPassword = "Şifreyi göster",
    hidePassword = "Şifreyi gizle"
)

private val MobileEnglish = MobileStrings(
    nChannels = { "$it channels" },
    greetings = listOf("Good morning", "Good afternoon", "Good evening", "Good night"),
    greetingHint = "What would you like to watch?",
    seeAll = "All",
    emptyStartTitle = "Let's start",
    emptyStart = "Tap Live TV below and pick a channel — what you watched last will " +
        "show up here.",
    fullscreen = "Full screen",
    exitFullscreen = "Exit full screen",
    moreEpisodes = "More episodes",
    minimize = "Minimise",
    close = "Close",
    play = "Play",
    pause = "Pause",
    previousChannel = "Previous channel",
    nextChannel = "Next channel",
    channelList = "Channel list",
    tapHint = "Tap: preview · tap again: full screen",
    rewind = "10 seconds back",
    forward = "10 seconds forward",
    searchMovies = "Search films",
    searchSeries = "Search series",
    resumeEpisode = { s, e -> "Continue · S$s E$e" },
    startSeries = "Play episode 1",
    watchLive = "Watch",
    watchFromStart = "Watch from the start",
    remind = "Remind me",
    reminderOn = "Reminder on",
    noGuide = "There is no programme guide for these channels yet.",
    noGuideHint = "It can be loaded in the settings under “Access”.",
    sectionAccess = "Access",
    sectionSubscription = "Subscription & licence",
    sectionLanguage = "Language",
    autoRecover = "Reconnect automatically after a dropout",
    audioPref = "Preferred audio",
    subtitlePref = "Preferred subtitles",
    automatic = "Automatic",
    unlockTitle = "Unlock Karacast",
    unlockEarly = {
        "You are trying Karacast for $it more days. Unlock now and everything simply " +
            "keeps working afterwards."
    },
    perks = listOf(
        "Live TV, films and series from your provider",
        "Programme guide with reminders",
        "No ads"
    ),
    activeTitle = "Karacast is unlocked",
    buyOnline = "Buy a licence online",
    buyHint = "Opens karacast.de with this phone's code. As soon as it is paid, the app " +
        "unlocks by itself.",
    setupTitle = "Set up access",
    xtream = "Xtream Codes",
    m3u = "M3U link",
    showPassword = "Show password",
    hidePassword = "Hide password"
)

private val MobileArabic = MobileStrings(
    nChannels = { "$it قناة" },
    greetings = listOf("صباح الخير", "نهارك سعيد", "مساء الخير", "تصبح على خير"),
    greetingHint = "ماذا تريد أن تشاهد؟",
    seeAll = "الكل",
    emptyStartTitle = "لنبدأ",
    emptyStart = "اضغط على البث المباشر بالأسفل واختر قناة — ما شاهدته مؤخراً سيظهر هنا.",
    fullscreen = "ملء الشاشة",
    exitFullscreen = "الخروج من ملء الشاشة",
    moreEpisodes = "الحلقات التالية",
    minimize = "تصغير",
    close = "إغلاق",
    play = "تشغيل",
    pause = "إيقاف مؤقت",
    previousChannel = "القناة السابقة",
    nextChannel = "القناة التالية",
    channelList = "قائمة القنوات",
    tapHint = "لمسة: معاينة · لمسة أخرى: ملء الشاشة",
    rewind = "رجوع 10 ثوانٍ",
    forward = "تقديم 10 ثوانٍ",
    searchMovies = "ابحث عن فيلم",
    searchSeries = "ابحث عن مسلسل",
    resumeEpisode = { s, e -> "متابعة · م$s ح$e" },
    startSeries = "تشغيل الحلقة 1",
    watchLive = "شاهد",
    watchFromStart = "شاهد من البداية",
    remind = "ذكّرني",
    reminderOn = "التذكير مفعّل",
    noGuide = "لا يوجد دليل برامج لهذه القنوات بعد.",
    noGuideHint = "يمكن تحميله من الإعدادات تحت „الوصول“.",
    sectionAccess = "الوصول",
    sectionSubscription = "الاشتراك والترخيص",
    sectionLanguage = "اللغة",
    autoRecover = "إعادة الاتصال تلقائياً عند الانقطاع",
    audioPref = "الصوت المفضل",
    subtitlePref = "الترجمة المفضلة",
    automatic = "تلقائي",
    unlockTitle = "فعّل كاراكاست",
    unlockEarly = {
        "تجرّب كاراكاست لمدة $it أيام أخرى. فعّله الآن وسيستمر كل شيء بعدها دون انقطاع."
    },
    perks = listOf(
        "البث المباشر والأفلام والمسلسلات من مزوّدك",
        "دليل البرامج مع التذكيرات",
        "بدون إعلانات"
    ),
    activeTitle = "كاراكاست مفعّل",
    buyOnline = "اشترِ الترخيص عبر الإنترنت",
    buyHint = "يفتح karacast.de برمز هذا الهاتف. بمجرد الدفع يتفعّل التطبيق تلقائياً.",
    setupTitle = "إعداد الوصول",
    xtream = "Xtream Codes",
    m3u = "رابط M3U",
    showPassword = "إظهار كلمة المرور",
    hidePassword = "إخفاء كلمة المرور"
)

fun mobileStringsFor(language: AppLanguage): MobileStrings = when (language) {
    AppLanguage.DE -> MobileGerman
    AppLanguage.TR -> MobileTurkish
    AppLanguage.EN -> MobileEnglish
    AppLanguage.AR -> MobileArabic
}

val LocalMobileStrings = staticCompositionLocalOf { MobileGerman }
