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
}
