plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.metro)
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

sourceSets {
    main {
        kotlin {
            srcDir("src/desktop/kotlin")
            // With a desktop copy of the same relative path in src/desktop (ComicInfo read without Android's parser).
            // Local val: a script-level one would drag the script object into the configuration cache.
            val androidOnly = setOf("tachiyomi/source/local/LocalSource.kt")
            exclude { it.relativePath.pathString in androidOnly && "/src/main/" in it.file.invariantSeparatorsPath }
        }
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.i18n)

    implementation(projects.core.archive)
    implementation(projects.core.common)
    implementation(projects.coreMetadata)
    implementation(projects.domain)

    implementation(libs.metro.runtime)

    implementation(libs.bundles.serialization)

    implementation(libs.injekt)
    implementation(libs.jsoup)

    implementation(libs.kotlinx.datetime)
}
