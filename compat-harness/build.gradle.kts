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

// The harness on the packaged runtime (spec 6a §3 C): -PharnessJava=<image>/runtime/bin/java.exe
providers.gradleProperty("harnessJava").orNull?.let { java -> tasks.named<JavaExec>("run") { executable(java) } }
