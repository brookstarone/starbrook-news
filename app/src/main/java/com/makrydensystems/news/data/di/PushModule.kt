package com.makrydensystems.news.data.di

/**
 * Push wiring point (v1 stub — deliberately empty).
 *
 * FCM wiring lands here once the Firebase project exists — no
 * google-services.json in v1. When the project exists, this object gains
 * the token-registration + topic-subscription calls (breaking / morning /
 * evening / weather-alerts) and the app gains the Firebase Messaging
 * dependency; until then there are no Firebase imports anywhere in the
 * app and the build must succeed with no google-services.json present.
 */
object PushModule {
    // Intentionally nothing here yet — see TODO.md ("Push (FCM)").
}
