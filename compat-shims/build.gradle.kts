plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
}

dependencies {
    api(projects.androidCompat)
    api(libs.slf4j.api)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback)
}
