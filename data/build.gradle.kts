plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.metro)
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("tachiyomi.data")
            dialect(libs.sqldelight.sqliteDialect338)
            schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
            generateAsync.set(true)
        }
    }
}

// Android driver setup; the desktop one lives in desktop-core (DesktopBindings).
// Filtered on the compile task: Kotlin also compiles src/main/java, which source set excludes don't reach.
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    exclude("**/tachiyomi/data/DatabaseBindings.kt")
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.core.metro)
    implementation(libs.metro.runtime)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.jsonOkio)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.injekt)
    implementation(libs.kotlinx.datetime)

    api(libs.androidx.sqlite.bundled)
    api(libs.bundles.sqldelight.jvm)

    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
