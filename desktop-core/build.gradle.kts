plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
}

dependencies {
    api(projects.sourceApi)
    api(projects.domain)
    api(projects.data)
    api(projects.core.common)
    api(projects.extensionLoader)
    implementation(projects.core.metro)
    implementation(projects.androidCompat)
    // Types of CefHelper.cefApp; the jar itself comes at runtime with android-compat
    compileOnly(libs.jcef)

    implementation(libs.metro.runtime)
    implementation(libs.injekt)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)
    // Dispatchers.Main for extensions (and later Compose): the Swing event thread
    runtimeOnly(libs.kotlinx.coroutines.swing)
    implementation(libs.bundles.serialization)
    implementation(libs.logback)
    implementation(libs.jna.platform)

    testImplementation(libs.bundles.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.jcef)
    // Mihon's messages, which the Cloudflare test compares
    testImplementation(projects.i18n)
    testFixturesImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Platform.start boots Koin and AndroidCompat's global config: once per JVM
tasks.test {
    forkEvery = 1
    // The real embedded browser is downloaded and started only by cefTest
    useJUnitPlatform { excludeTags("cef") }
}

// Tests against the real embedded browser (spec 2b §5). It's downloaded once into build/cef at the repo's root
val cefTest by tasks.registering(Test::class) {
    description = "Runs the tests that need the real embedded browser"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    forkEvery = 1
    useJUnitPlatform { includeTags("cef") }
    systemProperty("cef.dir", layout.settingsDirectory.dir("build/cef").asFile.absolutePath)
    // JCEF's windowed browser reaches into AWT's internals; JetBrains Runtime opens them, other JDKs need this
    jvmArgs("--add-exports=java.desktop/sun.awt=ALL-UNNAMED", "--add-exports=java.desktop/sun.awt.windows=ALL-UNNAMED")
}
