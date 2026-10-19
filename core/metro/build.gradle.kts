plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.metro)
}

dependencies {
    implementation(projects.androidCompat)
    implementation(libs.metro.runtime)
}
