plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.metro)
}

dependencies {
    api(projects.sourceApi)
    api(projects.domain)
    api(projects.data)
    api(projects.core.common)
    api(projects.extensionLoader)
    implementation(projects.core.metro)
    implementation(projects.androidCompat)

    implementation(libs.metro.runtime)
    implementation(libs.injekt)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.serialization)
    implementation(libs.logback)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Platform.start boots Koin and AndroidCompat's global config: once per JVM
tasks.test { forkEvery = 1 }
