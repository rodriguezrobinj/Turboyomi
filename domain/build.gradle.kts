plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.core.common)
    implementation(projects.core.metro)
    implementation(libs.metro.runtime)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.serialization)
    implementation(libs.kotlinx.datetime)

    api(libs.androidx.paging.common)

    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.androidx.compose.runtimeAnnotation)

    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
