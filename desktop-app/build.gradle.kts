plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.apollo)
    application
}

application {
    mainClass = "mihon.desktop.app.MainKt"
    // The WebView window's browser (JCEF, windowed) reaches into AWT's internals; JetBrains Runtime opens them, other
    // JDKs need this (spec 2b). The installer's launcher must pass the same
    applicationDefaultJvmArgs = listOf(
        "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.awt.windows=ALL-UNNAMED",
    )
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

dependencies {
    implementation(libs.apollo)
    implementation(libs.apollo.adapters)
    implementation(projects.desktopCore)
    implementation(projects.core.metro)
    implementation(projects.i18n)
    implementation(libs.metro.runtime)
    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.injekt)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.compose.desktop.windows)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(projects.presentationCore)
    implementation(projects.icons.materialSymbols)
    implementation(projects.icons.customMaterialSymbols)
    implementation(projects.icons.simpleIcons)
    implementation(libs.compose.animation)
    implementation(libs.compose.animationGraphics)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.toolingPreview)
    implementation(libs.compose.material3NavSuite)
    implementation(libs.jetbrains.lifecycle.viewmodelCompose)
    implementation(libs.jetbrains.lifecycle.runtimeCompose)
    implementation(libs.jetbrains.navigationevent.compose)
    implementation(libs.bundles.voyager)
    implementation(libs.composeMaterialMotion)
    implementation(libs.kotlinx.datetime)
    implementation(libs.coil.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(projects.extensionLoader)
    implementation(projects.core.archive)
    implementation(projects.coreMetadata)
    implementation(libs.bundles.serialization)
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.compose)
    implementation(libs.bundles.markdown)
    implementation(libs.swipe)
    implementation(libs.composeGrid)
    implementation(libs.composeRichEditor)
    implementation(libs.diskLruCache)
    implementation(libs.zoomable)
    implementation(libs.reorderable)
    implementation(libs.stringSimilarity)

    // Types of CefHelper.cefApp and of the WebView window's browser; the jar comes at runtime with android-compat
    compileOnly(libs.jcef)
    testImplementation(testFixtures(projects.desktopCore))
    testImplementation(libs.jcef)
    testImplementation(libs.bundles.test)
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.logback)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Platform.start boots Koin and AndroidCompat's global config: once per JVM
tasks.test {
    forkEvery = 1
    // Tests bring their own tracker keys (plan 5b-2, dev. 3)
    systemProperty("mangareader.tracker-keys", "none")
}

// The tracker keys the developer registered (spec 5b-2 §1), when the git-ignored file is there
tasks.processResources {
    from(layout.projectDirectory.file("../tracker-keys.properties"))
}

apollo {
    // Mihon's GraphQL services for its trackers
    service("kitsu") {
        packageName.set("mihon.graphql.kitsu")
        srcDir(file("src/main/graphql/mihon/graphql/kitsu"))
        // A date, expressed as an ISO8601 string
        mapScalarToKotlinString("Date")
        // An ISO 8601-encoded datetime
        mapScalar("ISO8601DateTime", "kotlin.time.Instant", "com.apollographql.adapter.core.KotlinInstantAdapter")
        // A loose key-value map in GraphQL
        mapScalar(
            "Map",
            "kotlin.collections.Map<String, Any?>",
            "mihon.graphql.kitsu.adapter.KitsuMapAdapter",
        )
    }

    service("anilist") {
        packageName.set("mihon.graphql.anilist")
        srcDir(file("src/main/graphql/mihon/graphql/anilist"))
        // ISO 3166-1 alpha-2 country code
        mapScalarToKotlinString("CountryCode")
    }

    service("shikimori") {
        packageName.set("mihon.graphql.shikimori")
        srcDir(file("src/main/graphql/mihon/graphql/shikimori"))
        // An ISO 8601-encoded date
        mapScalarToKotlinString("ISO8601Date")
    }

    service("suwayomi") {
        packageName.set("mihon.graphql.suwayomi")
        srcDir(file("src/main/graphql/mihon/graphql/suwayomi"))
    }
}
