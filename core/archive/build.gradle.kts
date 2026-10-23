plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
}

sourceSets {
    main {
        kotlin {
            srcDir("src/desktop/kotlin")
            // Android-only (libarchive over mmap, SAF file descriptors), or with a desktop copy of the same relative
            // path in src/desktop.
            // Local val: a script-level one would drag the script object into the configuration cache.
            val androidOnly = setOf(
                "mihon/core/archive/ArchiveInputStream.kt",
                "mihon/core/archive/ArchiveReader.kt",
                "mihon/core/archive/EpubReader.kt",
                "mihon/core/archive/UniFileExtensions.kt",
                "mihon/core/archive/ZipWriter.kt",
            )
            exclude { it.relativePath.pathString in androidOnly && "/src/main/" in it.file.invariantSeparatorsPath }
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.i18n)
    implementation(libs.jsoup)
    implementation(libs.sevenzipjbinding)
    runtimeOnly(libs.sevenzipjbinding.windows)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback)
}
