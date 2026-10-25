import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/*
 * Karacast Mobil — die Handy-App.
 *
 * Eine eigene App mit eigener Oberfläche, aber ohne eigenen Unterbau: alles, was
 * Playlists lädt, den Programmführer liest, Filme und Serien holt, die Lizenz
 * prüft und bei Google Play bezahlt, steht einmal in app/ und wird hier
 * mitgebaut (siehe sourceSets unten). Ein Fehler, der dort behoben wird, ist
 * damit in beiden Apps behoben.
 *
 * Mitgebaut wird der ganze Code der TV-App, auch ihre Bildschirme. Das kostet
 * nichts: was die Handy-App nicht aufruft, wirft R8 beim Release-Bau heraus.
 * Der Paketname im Code bleibt com.safir.iptv (namespace), damit der
 * gemeinsame Code seine BuildConfig und R findet; die App selbst heißt anders
 * (applicationId) und installiert sich neben der TV-App.
 */

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true

val playKeystorePropsFile = rootProject.file("keystore-play.properties")
val playKeystoreProps = Properties().apply {
    if (playKeystorePropsFile.exists()) playKeystorePropsFile.inputStream().use { load(it) }
}
val hasPlayKeystore = playKeystoreProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true

android {
    namespace = "com.safir.iptv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.safir.iptv.mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "1.1.0"
        vectorDrawables.useSupportLibrary = true

        buildConfigField(
            "String",
            "UPDATE_URL",
            "\"https://raw.githubusercontent.com/memo1907fener/karacast/main/update/karacast-mobile.json\""
        )

        // Handys sind ARM; x86 läuft nur im Emulator. Wie bei der TV-App.
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
        if (hasPlayKeystore) {
            create("playUpload") {
                storeFile = rootProject.file(playKeystoreProps.getProperty("storeFile"))
                storePassword = playKeystoreProps.getProperty("storePassword")
                keyAlias = playKeystoreProps.getProperty("keyAlias")
                keyPassword = playKeystoreProps.getProperty("keyPassword")
            }
        }
    }

    // Dieselben zwei Fassungen wie bei der TV-App: die APK mit Lizenz über
    // karacast.de und eigenen Updates, und die Play-Fassung mit Google Play
    // Billing (Abo oder Einmalkauf, Preise aus der Play Console).
    flavorDimensions += "store"
    productFlavors {
        create("direct") {
            dimension = "store"
            buildConfigField("boolean", "PLAY_STORE", "false")
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        create("play") {
            dimension = "store"
            applicationId = "com.sternweb.karacast.mobile"
            buildConfigField("boolean", "PLAY_STORE", "true")
            if (hasPlayKeystore) signingConfig = signingConfigs.getByName("playUpload")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                rootProject.file("app/proguard-rules.pro")
            )
        }
    }

    // Erst hier, nach den Fassungen: vorher gibt es „direct“ und „play“ noch nicht.
    sourceSets {
        getByName("main") {
            // Der gemeinsame Code aus der TV-App, dazu die eigene Oberfläche.
            java.srcDirs("src/main/java", "../app/src/main/java")
        }
        getByName("direct") {
            java.srcDirs("../app/src/direct/java")
        }
        getByName("play") {
            java.srcDirs("../app/src/play/java")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST"
        )
    }

    splits {
        abi { isEnable = false }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    // Nur, weil der gemeinsame Code sie mitbringt (die TV-Bildschirme und die
    // Reihen auf dem TV-Startbildschirm). Die Handy-Oberfläche benutzt sie nicht.
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.nextlib.media3ext)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)

    "playImplementation"(libs.google.billing)
}
