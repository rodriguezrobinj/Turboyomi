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
        // android.jar stubs used by android-compat. Suwayomi republishes 1.0.0 in place (on 2026-11-01 it dropped
        // android.net.Network*, which android-compat doesn't implement), so it's pinned to a commit of that branch
        exclusiveContent {
            forRepository {
                maven(url = "https://github.com/Suwayomi/Suwayomi-Server/raw/3983289d9481a8ba99c0b16350b27f41364906a2/")
            }
            filter { includeModule("com.github.Suwayomi", "android-jar") }
        }
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
include(":core:archive")
include(":core-metadata")
include(":source-local")
include(":source-api")
include(":domain")
include(":data")
include(":desktop-core")
include(":extension-loader")
include(":compat-harness")
include(":desktop-app")
include(":presentation-core")
include(":icons:material-symbols")
include(":icons:custom-material-symbols")
include(":icons:simple-icons")
