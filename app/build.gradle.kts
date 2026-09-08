import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(FileInputStream(f))
}
val spotifyClientId: String = localProperties.getProperty("spotify.client.id") ?: ""

android {
    namespace = "com.example.spotifymini"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.spotifymini"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // The Spotify Auth SDK's own manifest declares its redirect intent-filter
        // using these placeholders — must match redirectUri in MainActivity.kt
        // ("spotifyminiplayer://callback" -> scheme "spotifyminiplayer", host "callback")
        manifestPlaceholders["redirectSchemeName"] = "spotifyminiplayer"
        manifestPlaceholders["redirectHostName"] = "callback"

        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Spotify App Remote + Auth SDKs go in app/libs as .aar files (see README)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}