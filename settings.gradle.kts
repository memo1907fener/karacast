pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SafirIPTV"
include(":app")
// Die Handy-App. Sie baut den gemeinsamen Code aus app/ mit (Playlist, EPG,
// Filme, Lizenz) und hat nur ihre eigene Oberfläche — siehe mobile/build.gradle.kts.
include(":mobile")
