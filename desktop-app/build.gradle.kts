plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.metro)
    application
}

application {
    mainClass = "mihon.desktop.app.MainKt"
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

    testImplementation(libs.bundles.test)
    testImplementation(libs.compose.ui.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Platform.start boots Koin and AndroidCompat's global config: once per JVM
tasks.test { forkEvery = 1 }
