rootProject.name = "deckboard"

pluginManagement {
    repositories {
        // Kompound Gradle plugin until it is on the Plugin Portal / Maven Central.
        mavenLocal {
            content { includeGroup("tech.kloos.kompound") }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        kotlin("jvm") version "2.2.20"
    }
}

dependencyResolutionManagement {
    repositories {
        // Kompound (drag & drop) until it is on Maven Central.
        mavenLocal {
            content { includeGroup("tech.kloos.kompound") }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":desktopApp")
include(":macosApp")
include(":shared")