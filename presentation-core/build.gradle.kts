plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.compose.compiler)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

dependencies {
    api(projects.core.common)
    api(projects.i18n)
    implementation(projects.icons.materialSymbols)

    api(libs.compose.runtime)
    api(libs.compose.foundation)
    api(libs.compose.ui)
    api(libs.compose.material3)
    api(libs.compose.material3NavSuite)
    implementation(libs.compose.animation)
    implementation(libs.compose.animationGraphics)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.toolingPreview)

    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.compose)

    testImplementation(libs.bundles.test)
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.compose.desktop.windows)
    testRuntimeOnly(libs.junit.platform.launcher)
}
