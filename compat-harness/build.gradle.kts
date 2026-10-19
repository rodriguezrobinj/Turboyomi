plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    application
}

application {
    mainClass.set("mihon.desktop.harness.MainKt")
}

dependencies {
    implementation(projects.desktopCore)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
