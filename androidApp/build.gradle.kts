import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// Release signing comes from local.properties or environment variables, never from the repository:
// DECKBOARD_KEYSTORE, DECKBOARD_KEYSTORE_PASSWORD, DECKBOARD_KEY_ALIAS, DECKBOARD_KEY_PASSWORD
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(name: String): String? = System.getenv(name) ?: localProps.getProperty(name)

android {
    namespace = "de.kloostech.deckboard"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "de.kloostech.deckboard"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        val keystore = signingValue("DECKBOARD_KEYSTORE")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = signingValue("DECKBOARD_KEYSTORE_PASSWORD")
                keyAlias = signingValue("DECKBOARD_KEY_ALIAS")
                keyPassword = signingValue("DECKBOARD_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}