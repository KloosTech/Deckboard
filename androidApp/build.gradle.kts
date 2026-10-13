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

// Release signing never lives in the repository. Non-secret values (DECKBOARD_KEYSTORE, DECKBOARD_KEY_ALIAS) come from
// local.properties or the environment. Passwords come from the environment (CI) or the macOS Keychain:
//   security add-generic-password -a "$USER" -s deckboard-upload-keystore -w
// DECKBOARD_KEY_PASSWORD falls back to the keystore password (PKCS12 keystores use one password).
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun keychainPassword(service: String): String? = runCatching {
    providers.exec {
        commandLine("security", "find-generic-password", "-s", service, "-w")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { null }
}.getOrNull()

fun signingValue(name: String): String? = System.getenv(name) ?: localProps.getProperty(name)
val releaseStorePassword: String? by lazy { signingValue("DECKBOARD_KEYSTORE_PASSWORD") ?: keychainPassword("deckboard-upload-keystore") }
val releaseKeyPassword: String? by lazy { signingValue("DECKBOARD_KEY_PASSWORD") ?: releaseStorePassword }

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
        if (keystore != null && releaseStorePassword != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = releaseStorePassword
                keyAlias = signingValue("DECKBOARD_KEY_ALIAS")
                keyPassword = releaseKeyPassword
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