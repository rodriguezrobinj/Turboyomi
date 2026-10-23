plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

sourceSets {
    main {
        kotlin {
            srcDir("src/desktop/kotlin")
            // Android-only, or with a desktop copy of the same relative path in src/desktop (NetworkHelper, the
            // WebView interceptors and WebViewUtil), so only src/main is filtered.
            // Local val: a script-level one would drag the script object into the configuration cache.
            val androidOnly = setOf(
                "eu/kanade/tachiyomi/network/NetworkHelper.kt",
                "eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt",
                "eu/kanade/tachiyomi/network/interceptor/WebViewInterceptor.kt",
                "eu/kanade/tachiyomi/util/system/WebViewUtil.kt",
                "tachiyomi/core/common/i18n/Localize.kt",
                "tachiyomi/core/common/util/system/ImageUtil.kt",
                // Its desktop counterpart is DesktopFolderProvider (desktop-core); both would bind FolderProvider
                "tachiyomi/core/common/storage/AndroidStorageFolderProvider.kt",
                "eu/kanade/tachiyomi/util/storage/DiskUtil.kt",
            )
            exclude { it.relativePath.pathString in androidOnly && "/src/main/" in it.file.invariantSeparatorsPath }
        }
    }
}

dependencies {
    implementation(projects.core.metro)
    implementation(projects.i18n)
    api(projects.androidCompat)
    api(projects.compatShims)
    // Types of CefHelper.cefApp, which the interceptors read; the jar comes at runtime with android-compat
    compileOnly(libs.jcef)

    api(libs.rxJava)
    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    api(libs.okhttp.dnsOverHttps)
    api(libs.okhttp.zstd)
    api(libs.okio)
    api(libs.apollo)

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.serialization.jsonOkio)

    implementation(libs.jsoup)
    implementation(libs.natural.comparator)
    implementation(libs.metro.runtime)

    testImplementation(libs.bundles.test)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)
}
