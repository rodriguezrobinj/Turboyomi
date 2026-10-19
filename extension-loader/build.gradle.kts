plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.metro)
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.core.metro)
    implementation(libs.metro.runtime)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.apksig)
    implementation(libs.apk.parser)
    implementation(libs.asm.tree)
    implementation(libs.asm.analysis)
    implementation(libs.dex2jar.translator)
    implementation(libs.dex2jar.tools)
    implementation(libs.kotlin.logging)

    testImplementation(libs.bundles.test)
    testImplementation(libs.okhttp.core)
    testImplementation(libs.dex2jar.writer)
    testRuntimeOnly(libs.junit.platform.launcher)
}
