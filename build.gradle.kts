// Top-level build file — StarBrook News (native, v1).
// Pinned versions live here and in app/build.gradle.kts (see CONTRACT.md).
// Do not upgrade without coordinator approval.
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.0.21" apply false
    // Compose compiler comes from this plugin (version = Kotlin version).
    // Do NOT set composeOptions.kotlinCompilerExtensionVersion: the 1.5.x
    // artifact pairs with Kotlin 1.9.x only (Build #5 failed on exactly that).
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // Firebase (FCM push, Build #24): the google-services plugin reads
    // app/google-services.json (project starbrook-news) and generates
    // the values FirebaseApp needs. Version 4.6.0 = latest per the
    // Firebase Android release notes, 2026-11-11.
    id("com.google.gms.google-services") version "4.6.0" apply false
}
