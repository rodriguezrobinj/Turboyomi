plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.android.stubs)
    api(projects.androidCompat.config)
    implementation(libs.bundles.androidcompat)
    compileOnly(libs.xmlpull)
    compileOnly(libs.apksig)
    compileOnly(libs.androidx.annotation)
    implementation(libs.bundles.polyglot)
    implementation(libs.bundles.settings)
    implementation(libs.icu4j)
    implementation(libs.bundles.twelvemonkeys)
    implementation(libs.imageio.webp)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
