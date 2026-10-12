plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Native (non-JVM) macOS build for Apple Silicon. Run: ./gradlew :macosApp:runDebugExecutableMacosArm64
kotlin {
    macosArm64 {
        binaries.executable {
            entryPoint = "de.kloostech.deckboard.macos.main"
        }
    }
    sourceSets {
        macosMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.compose.ui)
        }
    }
}
