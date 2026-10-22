plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.metro)
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

sourceSets {
    main {
        // Null implementations of subsystems later subprojects port (downloads: 4, trackers: 5)
        kotlin.srcDir("src/stubs/kotlin")
    }
}

dependencies {
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
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Platform.start boots Koin and AndroidCompat's global config: once per JVM
tasks.test { forkEvery = 1 }
