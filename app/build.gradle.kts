import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// The TubeGuard backend address. Empty (the default) leaves every TubeGuard part of the app switched off, so
// release builds behave as before until launch. Set it with -PtubeguardUrl=https://... or in gradle.properties.
// Debug builds default to the emulator's route to a wrangler dev server on the host computer.
val tubeguardUrlProperty = (project.findProperty("tubeguardUrl") as String?)?.trim()
val tubeguardReleaseUrl = tubeguardUrlProperty ?: ""
val tubeguardDebugUrl = tubeguardUrlProperty ?: "http://10.0.2.2:8787"

android {
    namespace = "dev.tvlimiter"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.brynkng.tvlimiter"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    // The Play Store copy leaves out the donation QR code, since Play does not allow
    // payments to the developer outside Google Play billing.
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("String", "DONATE_URL", "\"https://venmo.com/u/bk.ceramics\"")
        }
        create("play") {
            dimension = "store"
            buildConfigField("String", "DONATE_URL", "\"\"")
        }
    }

    // Release signing reads keystore.properties, which stays out of git. Without it,
    // release builds are left unsigned.
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        val keys = Properties().apply { keystoreFile.inputStream().use { load(it) } }
        signingConfigs {
            create("release") {
                storeFile = file(keys.getProperty("storeFile"))
                storePassword = keys.getProperty("storePassword")
                keyAlias = keys.getProperty("keyAlias")
                keyPassword = keys.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            tubeguardPlaceholders(tubeguardDebugUrl)
        }
        release {
            tubeguardPlaceholders(tubeguardReleaseUrl)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    // Only a release build with a TubeGuard address asks for the internet permission. Without one, the app
    // still has none, as the privacy policy says.
    if (tubeguardReleaseUrl.isNotEmpty()) {
        sourceSets.getByName("release").manifest.srcFile("src/tubeguard/AndroidManifest.xml")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // Draws the donation QR code; only the GitHub copy has one.
    "githubImplementation"("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
}

/** The URL as a build setting, and the manifest switches that keep TubeGuard's parts out of a build with no URL. */
fun com.android.build.api.dsl.ApplicationBuildType.tubeguardPlaceholders(url: String) {
    buildConfigField("String", "TUBEGUARD_URL", "\"${url.trimEnd('/')}\"")
    val on = url.isNotEmpty()
    manifestPlaceholders["tubeguardEnabled"] = on.toString()
}
