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
            // Android-only until the desktop WebView (subproject 2) and image pipeline (subproject 2/4) exist.
            // Files with a desktop copy in src/desktop share their relative path, so only src/main is filtered.
            // Local val: a script-level one would drag the script object into the configuration cache.
            val androidOnly = setOf(
                "eu/kanade/tachiyomi/network/NetworkHelper.kt",
                "eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt",
                "eu/kanade/tachiyomi/network/interceptor/WebViewInterceptor.kt",
                "eu/kanade/tachiyomi/util/system/WebViewUtil.kt",
                "tachiyomi/core/common/i18n/Localize.kt",
                "tachiyomi/core/common/util/system/ImageUtil.kt",
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

    api(libs.rxJava)
    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    api(libs.okhttp.dnsOverHttps)
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
