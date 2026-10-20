plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)

    alias(mihonx.plugins.spotless)

    alias(libs.plugins.valkyrie)
}

spotless {
    format("svg") {
        target("src/**/*.svg")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

valkyrie {
    packageName = "mihon.icons.simpleicons"
    generateAtSync = true

    imageVector {
        suppressUnusedReceiverWarning = true
    }

    iconPack {
        name = "SimpleIcons"
        targetSourceSet = "main"
    }
}

dependencies {
    api(libs.compose.ui)
}
