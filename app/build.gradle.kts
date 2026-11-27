// StarBrook News — app module (native, v1: public content only).
// Pinned: AGP 8.2.2, Kotlin 2.0.21, Compose BOM 2024.09.00 (CONTRACT.md).
// No secrets in this file: v1 fetches public URLs only. Release signing
// reads keystore.properties ONLY when the file exists locally (never
// committed); debug builds use the shared committed debug key
// (keystores/debug.keystore, conventional "android" passwords — a public
// debug key, not a secret) so every CI build signs with the SAME key and
// installs over the previous debug build instead of Android refusing the
// update (Build #8 and #12 each carried a different runner-generated key).

import java.util.Properties

apply(from = "$rootDir/gradle/font-download.gradle")
apply(from = "$rootDir/gradle/launcher-icon-download.gradle")
apply(from = "$rootDir/gradle/debug-signing-check.gradle")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
    // Compose compiler (Kotlin Compose plugin 2.0.21 = Kotlin 2.0.21).
    id("org.jetbrains.kotlin.plugin.compose")
    // Firebase (FCM push, Build #24): reads app/google-services.json
    // (project starbrook-news, both packages) at build time. The plugin
    // MUST be applied — without it FirebaseApp has no values to init
    // from and getInstance() throws at runtime.
    id("com.google.gms.google-services")
}

android {
    namespace = "com.makrydensystems.news"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.makrydensystems.news"
        minSdk = 26
        targetSdk = 34
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        // In-app updater (Build #25): the app compares this code against
        // the update manifest's versionCode, so it MUST increase with
        // every build. CI passes -PappVersionCode=${{ github.run_number }}
        // (the per-workflow run counter — a small int, NOT the run id,
        // which overflows int32). Local builds default to 1. If the
        // workflow is ever recreated and run_number restarts, the release
        // step must set a versionCode higher than any shipped build —
        // see APP_UPDATE_RELEASE.md.
        versionName = "1.0.0"
        // Portal sign-in: the Supabase anon key, fed from the
        // SUPABASE_ANON_KEY env var or the -PsupabaseAnonKey gradle
        // property. The key is the same publishable key the web portal
        // ships in its own JS — but it still never belongs in a committed
        // file. Empty means sign-in is unavailable in that build and the
        // Hub says so instead of failing mysteriously.
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"" + (project.findProperty("supabaseAnonKey") as String?
                ?: System.getenv("SUPABASE_ANON_KEY") ?: "") + "\"",
        )
    }

    signingConfigs {
        // Stable shared debug key (keystores/debug.keystore, committed):
        // every CI runner signs debug builds with the SAME key, so each
        // new debug APK installs over the previous one instead of
        // Android refusing the update. This is the conventional public
        // debug key (passwords "android") — not a secret, and not the
        // release upload key (starbrook-news-upload.jks, never committed).
        //
        // NOTE (2026-11-19): configuring the BUILT-IN debug config with
        // getByName("debug") did NOT take effect on CI — Builds #14/#19/#20
        // and the #20 sign-in re-run each shipped a different
        // runner-generated cert (verified from the v2 signing block), even
        // though the keystore file was in the repo. So the debug build type
        // now points at this explicitly CREATED config instead; the
        // built-in debug config is left at its default, unused.
        // verifyDebugSigning (gradle/debug-signing-check.gradle, hooked
        // into preBuild) prints the keystore's cert fingerprint in the CI
        // log, so the key that signed the APK is provable per build.
        create("stableDebug") {
            storeFile = rootProject.file("keystores/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Created only when keystore.properties is present in the
        // workdir (local release builds). Absent on CI and fresh
        // checkouts — release builds then fail loudly instead of
        // silently shipping unsigned.
        val keystorePropsFile = rootProject.file("keystore.properties")
        if (keystorePropsFile.exists()) {
            create("release") {
                val props = Properties().apply {
                    load(keystorePropsFile.inputStream())
                }
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Signed with the shared committed debug key (see
            // signingConfigs above) so CI debug builds install over
            // each other instead of each runner minting its own key.
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release {
            isMinifyEnabled = false
            // Signing applies only when the local keystore exists;
            // otherwise this build type stays unsigned and CI never
            // builds it in v1 (assembleDebug only).
            val keystorePropsFile = rootProject.file("keystore.properties")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Portal sign-in needs the Supabase anon key (TODO.md: pass via a
        // BuildConfig field from env, NEVER commit the key). Fed from the
        // SUPABASE_ANON_KEY env var or the -PsupabaseAnonKey gradle
        // property; empty means sign-in is unavailable in that build and
        // the Hub says so instead of failing mysteriously.
        buildConfig = true
    }
    // No composeOptions block: the Kotlin Compose plugin (above) supplies
    // the compiler at the Kotlin version. The old
    // kotlinCompilerExtensionVersion = "1.5.15" paired with Kotlin 1.9.25
    // only and broke the build against Kotlin 2.0.21 (Build #5).
}

tasks.named("preBuild").configure {
    dependsOn("downloadStarBrookFonts", "downloadLauncherIcon", "verifyDebugSigning")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Room 2.7.1 (was 2.6.1): Build #24 failed at :app:kaptDebugKotlin
    // because Room 2.6.1's metadata reader caps at Kotlin metadata
    // 2.0.0, while firebase-messaging 26.0.2 is compiled with Kotlin
    // 2.1 (metadata 2.1.0). Room 2.7.x reads 2.1.0 metadata.
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    kapt("androidx.room:room-compiler:2.7.1")
    // Firebase Cloud Messaging (Build #24): push notifications from the
    // app itself. BoM 35.2.0 = latest per the Firebase Android release
    // notes, 2026-11-11 (firebase-messaging 26.0.2). The BoM pins the
    // messaging version — never declare firebase-messaging with a
    // version of its own. google-services.json (project starbrook-news)
    // is in app/ and covers both packages; the google-services plugin
    // above reads it. No Firebase Analytics — not used, not wanted.
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-messaging")
    // Custom Tabs (2026-11-27, push-tap article links): a notification
    // carrying an article URL opens it in-app instead of the News tab.
    // 1.8.0 (Mar 2024) matches this stack's pinned era; the CustomTabs
    // API used here is stable across 1.8+.
    implementation("androidx.browser:browser:1.8.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Unit tests (Fix 1 + Fix 2, 2026-11-24): plain JUnit 4 for the pure
    // functions only — no Robolectric, no Android framework. CI runs
    // :app:testDebugUnitTest (see .github/workflows/build.yml).
    testImplementation("junit:junit:4.13.2")
}
