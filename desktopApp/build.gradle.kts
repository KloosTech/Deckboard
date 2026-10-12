import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(kotlin("stdlib-jdk8"))
}

compose.desktop {
    application {
        mainClass = "de.kloostech.deckboard.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Deckboard"
            packageVersion = "1.0.0"
            description = "A free third-party Kanban client for Nextcloud Deck"
            vendor = "KloosTech"

            macOS {
                iconFile.set(project.file("icons/icon.icns"))
                bundleID = "de.kloostech.deckboard"
                dockName = "Deckboard"
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
            }
        }
    }
}
kotlin {
    jvmToolchain(11)
}