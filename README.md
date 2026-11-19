# StarBrook News — native Android app

Standalone StarBrook News app: News + Weather, native (Kotlin +
Jetpack Compose). Free, no paywall, no ads, no login to read.
Dark-first. The live web News page (the Mix) and Weather page are the
reference — the app must match them or beat them.

**Package:** `com.makrydensystems.news` · **minSdk 26, targetSdk 34**
· **Pinned:** AGP 8.2.2, Kotlin 2.0.21, Compose BOM 2024.09.00
(see `CONTRACT.md` for the full build contract and `DESIGN.md` for the
design system).

## v1 scope — public content only

v1 fetches ONLY public URLs, no auth of any kind:

- Supabase public storage JSONs (editions, breaking, latest, video
  manifest) + the public RSS feed
- NWS weather (`api.weather.gov`) + RainViewer radar — **NWS is the
  standard; Open-Meteo is out** (it 429'd the video builds)

Deferred to a later step (see `app/src/main/java/com/makrydensystems/news/data/TODO.md`):
PostgREST/Originals, Supabase Auth (portal login), Shelf sync, Master
Room / CADE chat, FCM push, release signing, Play.

## Build

CI (GitHub Actions, `ubuntu-24.04`) builds the debug APK on every
push to `main` — CI green is the gate. Local builds need JDK 17 +
Android SDK 34; Gradle cannot run on the build VM (JVM loopback is
blocked), so CI is the only build path there.

No Gradle wrapper jar is committed (it is a binary and this repo's
push flow is UTF-8 only); CI uses `gradle/actions/setup-gradle` with
the pinned Gradle 8.2.

Fonts (Inter + Liberation Sans Bold, OFL) are downloaded at build
time by `gradle/font-download.gradle` (SHA-256 verified) — TTF
binaries are never committed.

## Secrets

There are none in this repo and there must never be: no API keys, no
tokens, no passwords, no keystore. The release keystore lives only in
the coordinator's workdir and the parent's Secure Vault; CI signing
lands when the parent places the secrets. Never print a secret value
anywhere.
