pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("mihonx") {
            from(files("gradle/mihon.versions.toml"))
        }
    }

    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
        // android.jar stubs used by android-compat
        maven(url = "https://github.com/Suwayomi/Suwayomi-Server/raw/android-jar/")
        // JCEF (compile dependency of android-compat's webkit classes)
        maven(url = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        maven(url = "https://jogamp.org/deployment/maven")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "MangaReader"
include(":i18n")
include(":android-compat")
include(":android-compat:config")
include(":compat-shims")
include(":core:metro")
include(":core:common")
include(":source-api")
include(":domain")
include(":data")
include(":desktop-core")
include(":extension-loader")
