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

// Distributable app bundle for the native target (see package-macos.sh for signing/notarization environment variables):
//   ./gradlew :macosApp:packageMacosApp      -> build/macos-app/Deckboard.app
//   ./gradlew :macosApp:packageMacosDmg      -> build/macos-app/Deckboard-<version>.dmg
//   ./gradlew :macosApp:notarizeMacosDmg     -> signed with Developer ID, notarized and stapled
// Keep in sync with APP_VERSION in shared/ui/AboutDialog.kt.
val macosAppVersion = "1.0.0"
val macosExecutable = layout.buildDirectory.file("bin/macosArm64/releaseExecutable/macosApp.kexe")
val macosResourceDirs = listOf(
    layout.buildDirectory.dir("generated/compose/resourceGenerator/assembledResources/macosArm64Main/composeResources"),
    project(":shared").layout.buildDirectory.dir("generated/compose/resourceGenerator/assembledResources/macosArm64Main/composeResources"),
)

fun registerMacosPackaging(name: String, mode: String, description: String) = tasks.register<Exec>(name) {
    group = "distribution"
    this.description = description
    dependsOn("linkReleaseExecutableMacosArm64", "assembleMacosArm64MainResources", ":shared:assembleMacosArm64MainResources")
    workingDir = projectDir
    commandLine(
        listOf("bash", "package-macos.sh", mode, macosExecutable.get().asFile.path, macosAppVersion) +
            macosResourceDirs.map { it.get().asFile.path },
    )
}
registerMacosPackaging("packageMacosApp", "app", "Builds Deckboard.app (ad-hoc signed unless DECKBOARD_MAC_SIGN_IDENTITY is set)")
registerMacosPackaging("packageMacosDmg", "dmg", "Builds a DMG containing Deckboard.app")
registerMacosPackaging("notarizeMacosDmg", "notarize", "Signs with Developer ID, notarizes and staples a DMG")
