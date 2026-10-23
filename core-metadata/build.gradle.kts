plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.sourceApi)

    implementation(libs.bundles.serialization)
}
