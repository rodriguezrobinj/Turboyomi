plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.apollo)
    application
}

application {
    mainClass = "mihon.desktop.app.MainKt"
    // The WebView window's browser (JCEF, windowed) reaches into AWT's internals; JetBrains Runtime opens them, other
    // JDKs need this (spec 2b). The installer's launcher must pass the same
    applicationDefaultJvmArgs = listOf(
        "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.awt.windows=ALL-UNNAMED",
    )
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

dependencies {
    implementation(libs.apollo)
    implementation(libs.jna.platform)
    implementation(libs.apollo.adapters)
    implementation(projects.desktopCore)
    implementation(projects.core.metro)
    implementation(projects.i18n)
    implementation(libs.metro.runtime)
    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.injekt)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.compose.desktop.windows)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(projects.presentationCore)
    implementation(projects.icons.materialSymbols)
    implementation(projects.icons.customMaterialSymbols)
    implementation(projects.icons.simpleIcons)
    implementation(libs.compose.animation)
    implementation(libs.compose.animationGraphics)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.toolingPreview)
    implementation(libs.compose.material3NavSuite)
    implementation(libs.jetbrains.lifecycle.viewmodelCompose)
    implementation(libs.jetbrains.lifecycle.runtimeCompose)
    implementation(libs.jetbrains.navigationevent.compose)
    implementation(libs.bundles.voyager)
    implementation(libs.composeMaterialMotion)
    implementation(libs.kotlinx.datetime)
    implementation(libs.coil.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(projects.extensionLoader)
    implementation(projects.core.archive)
    implementation(projects.coreMetadata)
    implementation(libs.bundles.serialization)
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.compose)
    implementation(libs.bundles.markdown)
    implementation(libs.swipe)
    implementation(libs.composeGrid)
    implementation(libs.composeRichEditor)
    implementation(libs.zoomable)
    implementation(libs.reorderable)
    implementation(libs.stringSimilarity)

    // Types of CefHelper.cefApp and of the WebView window's browser; the jar comes at runtime with android-compat
    compileOnly(libs.jcef)
    testImplementation(testFixtures(projects.desktopCore))
    testImplementation(libs.jcef)
    testImplementation(libs.bundles.test)
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.logback)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Platform.start boots Koin and AndroidCompat's global config: once per JVM
tasks.test {
    forkEvery = 1
    // Tests bring their own tracker keys (plan 5b-2, dev. 3)
    systemProperty("turboyomi.tracker-keys", "none")
}

// The tracker keys the developer registered (spec 5b-2 §1), when the git-ignored file is there
tasks.processResources {
    from(layout.projectDirectory.file("../tracker-keys.properties"))
}

apollo {
    // Mihon's GraphQL services for its trackers
    service("kitsu") {
        packageName.set("mihon.graphql.kitsu")
        srcDir(file("src/main/graphql/mihon/graphql/kitsu"))
        // A date, expressed as an ISO8601 string
        mapScalarToKotlinString("Date")
        // An ISO 8601-encoded datetime
        mapScalar("ISO8601DateTime", "kotlin.time.Instant", "com.apollographql.adapter.core.KotlinInstantAdapter")
        // A loose key-value map in GraphQL
        mapScalar(
            "Map",
            "kotlin.collections.Map<String, Any?>",
            "mihon.graphql.kitsu.adapter.KitsuMapAdapter",
        )
    }

    service("anilist") {
        packageName.set("mihon.graphql.anilist")
        srcDir(file("src/main/graphql/mihon/graphql/anilist"))
        // ISO 3166-1 alpha-2 country code
        mapScalarToKotlinString("CountryCode")
    }

    service("shikimori") {
        packageName.set("mihon.graphql.shikimori")
        srcDir(file("src/main/graphql/mihon/graphql/shikimori"))
        // An ISO 8601-encoded date
        mapScalarToKotlinString("ISO8601Date")
    }

    service("suwayomi") {
        packageName.set("mihon.graphql.suwayomi")
        srcDir(file("src/main/graphql/mihon/graphql/suwayomi"))
    }
}

// The exe's and the MSI's icon, from the app's SVG (spec 6a §2)
val iconIco by tasks.registering(JavaExec::class) {
    val svg = layout.projectDirectory.file("src/main/resources/turboyomi.svg")
    val ico = layout.buildDirectory.file("packaging/turboyomi.ico")
    inputs.file(svg)
    outputs.file(ico)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "mihon.desktop.app.packaging.IconRenderer"
    args(svg.asFile.path, ico.get().asFile.path)
}

// Packaging (spec 6a §2): the app's jars, a runtime cut down with jlink, and jpackage's image of them
val packagingDir = layout.buildDirectory.dir("packaging")
val jdkHome = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(23) }
    .map { it.metadata.installationPath.asFile }

// The same rule as :desktop-core:generateBuildInfo: the last v* tag, 0.1.0 without one
val appVersion = providers.exec {
    commandLine("git", "describe", "--tags", "--match", "v*", "--abbrev=0")
    isIgnoreExitValue = true
}.standardOutput.asText.map { Regex("""^v(\d+\.\d+\.\d+)$""").find(it.trim())?.groupValues?.get(1) ?: "0.1.0" }

val appJars by tasks.registering(Sync::class) {
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(packagingDir.map { it.dir("input") })
    // androidx and JetBrains publish different jars under one file name (lifecycle-runtime-desktop-2.11.0.jar): a
    // jar from Gradle's cache (files-2.1/<group>/<module>/<version>/<hash>/) takes its group as a prefix
    eachFile {
        val group = file.parentFile?.parentFile?.parentFile?.parentFile
        if (group?.parentFile?.name == "files-2.1") name = "${group.name}-$name"
    }
}

val jlinkRuntime by tasks.registering(Exec::class) {
    dependsOn(appJars)
    val input = packagingDir.map { it.dir("input") }
    val runtime = packagingDir.map { it.dir("runtime") }
    val jdk = jdkHome
    val mainJar = tasks.jar.flatMap { it.archiveFileName }
    inputs.dir(input)
    outputs.dir(runtime)
    doFirst {
        runtime.get().asFile.deleteRecursively()
        val jars = input.get().asFile.listFiles()!!.map { it.name }.filter { it.endsWith(".jar") }
        // From the app's jar through every library it reaches (--recursive). On the class path, modular jars are
        // unnamed, so jdeps doesn't try to resolve their module graph (JCEF requires jogl, asm's packages are split).
        // jdeps takes no argfile: relative names keep the command line under Windows' limit
        val jdeps = ProcessBuilder(
            jdk.get().resolve("bin/jdeps.exe").path, "--ignore-missing-deps", "--multi-release", "23",
            "--print-module-deps", "--recursive", "--class-path", jars.joinToString(";"), mainJar.get(),
        ).directory(input.get().asFile).redirectErrorStream(true).start()
        val deps = jdeps.inputStream.bufferedReader().readText().trim().lines().last()
        check(jdeps.waitFor() == 0) { "jdeps failed: $deps" }
        // What extensions load on top of the app's own needs (spec 6a §2)
        val extra = "java.desktop,java.net.http,java.sql,jdk.crypto.ec,jdk.unsupported,jdk.charsets,jdk.localedata," +
            "java.naming,java.management"
        commandLine(
            jdk.get().resolve("bin/jlink.exe").path,
            "--add-modules",
            "$deps,$extra",
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--output",
            runtime.get().asFile.path,
        )
    }
}

// jpackage's arguments shared by the image and the MSI; a provider, so the configuration cache can keep it
val jpackageArgs = jdkHome.zip(packagingDir) { jdk, packaging ->
    listOf(
        jdk.resolve("bin/jpackage.exe").path,
        "--name", "Turboyomi", "--vendor", "Turboyomi",
        "--input", packaging.dir("input").asFile.path,
        "--main-class", "mihon.desktop.app.MainKt",
        "--runtime-image", packaging.dir("runtime").asFile.path,
        "--icon", packaging.file("turboyomi.ico").asFile.path,
        // The same as applicationDefaultJvmArgs (spec 2b)
        "--java-options", "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
        "--java-options", "--add-exports=java.desktop/sun.awt.windows=ALL-UNNAMED",
    )
}.zip(tasks.jar.flatMap { it.archiveFileName }) { args, jar -> args + listOf("--main-jar", jar) }
    .zip(appVersion) { args, version -> args + listOf("--app-version", version) }

val appImage by tasks.registering(Exec::class) {
    dependsOn(appJars, jlinkRuntime, iconIco)
    val image = packagingDir.map { it.dir("image") }
    val args = jpackageArgs
    inputs.dir(packagingDir.map { it.dir("input") })
    inputs.dir(packagingDir.map { it.dir("runtime") })
    outputs.dir(image)
    doFirst {
        image.get().asFile.deleteRecursively()
        commandLine(
            args.get() + listOf("--type", "app-image", "--dest", image.get().asFile.path),
        )
    }
}

val packageZip by tasks.registering(Zip::class) {
    from(appImage)
    archiveFileName = appVersion.map { "Turboyomi-$it.zip" }
    destinationDirectory = packagingDir
}

// The per-user MSI (spec 6a §3 B). Needs WiX 3.14: on PATH, where its installer or the WIX variable put it, or its
// binaries zip unpacked into %LOCALAPPDATA%/Programs/WiX314/bin
val wixBin = providers.environmentVariable("LOCALAPPDATA").map { "$it/Programs/WiX314/bin" }
    .zip(providers.environmentVariable("WIX").orElse("")) { local, wix -> listOf(local, "$wix/bin") }
    .zip(providers.environmentVariable("PATH")) { dirs, path ->
        (dirs + "C:/Program Files (x86)/WiX Toolset v3.14/bin" + path.split(";"))
            .map(::File).firstOrNull { it.resolve("candle.exe").isFile }?.path.orEmpty()
    }

val packageMsi by tasks.registering(Exec::class) {
    dependsOn(appJars, jlinkRuntime, iconIco)
    val args = jpackageArgs
    val out = packagingDir
    val wix = wixBin
    val resources = layout.projectDirectory.dir("packaging/msi")
    inputs.dir(packagingDir.map { it.dir("input") })
    inputs.dir(packagingDir.map { it.dir("runtime") })
    inputs.dir(resources)
    outputs.file(out.zip(appVersion) { dir, version -> dir.file("Turboyomi-$version.msi") })
    doFirst {
        check(wix.get().isNotEmpty()) {
            "WiX 3.14 isn't installed: winget install WiXToolset.WiXToolset, or unpack wix314-binaries.zip from " +
                "https://github.com/wixtoolset/wix3/releases into %LOCALAPPDATA%/Programs/WiX314/bin"
        }
        out.get().asFile.listFiles()!!.filter { it.extension == "msi" }.forEach { it.delete() }
        environment("PATH", "${wix.get()};${System.getenv("PATH")}")
        // jpackage's own temp folder (%TEMP%) keeps WiX under MAX_PATH; one inside this project would not
        commandLine(
            args.get() + listOf(
                "--type", "msi", "--win-per-user-install", "--win-menu", "--win-shortcut",
                // Relative to %LOCALAPPDATA%: jpackage's default, %LOCALAPPDATA%\Turboyomi, is the app's own local data
                "--install-dir", "Programs\\Turboyomi", "--win-menu-group", "Turboyomi",
                // Fixed, so a newer MSI replaces the installed one (spec 6a §3 B)
                "--win-upgrade-uuid", "7C3E9A52-41D8-4B6F-A0E2-9D5B3C8F1A64",
                "--resource-dir", resources.asFile.path,
                "--dest", out.get().asFile.path,
            ),
        )
    }
}
