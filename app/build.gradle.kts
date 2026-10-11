import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Optional release signing: create keystore.properties in the project root with
// storeFile / storePassword / keyAlias / keyPassword. If absent, release builds
// fall back to the debug key so `assembleRelease` never fails.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true

// Der Upload-Schlüssel für Google Play — ein eigener, nicht der der APK-Version.
// Google signiert die App für die Kunden selbst neu (Play App Signing); dieser
// Schlüssel weist nur nach, dass ein Upload von uns kommt, und lässt sich bei
// Verlust über die Play Console zurücksetzen. Fehlt die Datei, bleibt das Bundle
// unsigniert: dann wird es zwar gebaut, aber Google nimmt es nicht an.
val playKeystorePropsFile = rootProject.file("keystore-play.properties")
val playKeystoreProps = Properties().apply {
    if (playKeystorePropsFile.exists()) playKeystorePropsFile.inputStream().use { load(it) }
}
val hasPlayKeystore = playKeystoreProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true

android {
    namespace = "com.safir.iptv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.safir.iptv"
        minSdk = 23
        // Google Play nimmt neue Apps und Updates nur mit aktuellem Ziel-API an.
        // Android 16 ändert dabei zweierlei, beides ist abgefangen: das Vollbild
        // (MainActivity.goImmersive) und die Zurück-Taste (Manifest,
        // enableOnBackInvokedCallback).
        targetSdk = 36
        versionCode = 22
        versionName = "1.9.9"
        vectorDrawables.useSupportLibrary = true

        // The FFmpeg decoders ship a native library per processor type, and each
        // one is several megabytes. Every Android TV and every TV box worth
        // supporting is ARM, so the two ARM builds are kept and the x86 ones —
        // which only ever run in an emulator — are left out.
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

    // Zwei Fassungen derselben App:
    //
    //  * direct — die APK, wie sie bisher verteilt wird: eigene Updates über
    //    GitHub, Lizenz über karacast.de. Paketname und Schlüssel bleiben, damit
    //    jede bestehende Installation weiter Updates bekommt.
    //  * play   — für Google Play: Updates kommen vom Play Store, bezahlt wird über
    //    Google Play Billing (Einmalkauf oder Abo), und nichts spricht mit einem
    //    eigenen Server. Eigener Paketname, damit sich beide nie in die Quere
    //    kommen.
    //
    // Der Code fragt BuildConfig.PLAY_STORE; nur das Billing selbst liegt getrennt
    // unter src/play und src/direct, weil die Bibliothek nur in die Play-Fassung
    // gehört.
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
            applicationId = "com.sternweb.karacast"
            // Googles automatischer Schutz (Play Integrity) setzt Android 7 voraus.
            // Die APK-Fassung bleibt bei 23, für ältere Boxen, die nur sie bekommen.
            minSdk = 24
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Den Schlüssel bestimmt die Fassung (siehe productFlavors), nicht der
            // Build-Typ: ein hier gesetzter Schlüssel würde den der Fassung
            // überstimmen.
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
            // Compose-for-TV and large parts of Media3 are still opt-in APIs.
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

    // One APK per ABI would be smaller, but a single universal APK is far easier
    // to sideload onto a TV box. Keep it universal.
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
    // tv-material3 has no progress / slider / switch components; the phone Material3
    // artifact supplies those few widgets and coexists fine with it.
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    // Software decoders for the formats a television box often has no hardware for:
    // AC-3, E-AC-3, DTS, TrueHD, MP2 and the rest. See PlayerFactory.
    implementation(libs.nextlib.media3ext)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    // Erzeugt den QR-Code, den der Käufer mit dem Handy scannt.
    implementation(libs.zxing.core)

    "playImplementation"(libs.google.billing)
}
