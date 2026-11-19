# StarBrook News — NATIVE app: build contract (coordinator-owned)

**Status:** NATIVE rebuild. The web-shell approach is DEAD (Matthew's word 2026-11-19 ~12:23 CST).
**Workdir:** `~/workspace/starbrook-news-native/` — this will become GitHub repo `brookstarone/starbrook-news`.
**Package:** `com.makrydensystems.news` · **App:** StarBrook News 1.0.0 (versionCode 1) · **minSdk 26, targetSdk 34, compileSdk 34**

## Toolchain (already on this VM — use these, do not re-download)
- JDK: `~/workspace/android-tools/jdk17` (Temurin 17.0.21) — set `JAVA_HOME` to it.
- Android SDK: `~/workspace/android-tools/sdk` (build-tools 34.0.0, platform android-34, platform-tools 37.0.1, cmdline-tools latest) — set `ANDROID_SDK_ROOT` to it. NO emulator, NO system images installed yet; install via sdkmanager only if needed for screenshots (integration phase decides).
- Gradle: `~/workspace/android-tools/gradle-8.2` — the repo uses the Gradle **wrapper** (8.2) so CI uses the same.
- `local.properties` (sdk.dir) is NEVER committed. CI sets the SDK via `android-actions/setup-android`.

## Pinned versions (do not upgrade without coordinator approval)
- AGP 8.2.2 · Kotlin 2.0.21 · Compose BOM 2024.09.00 · navigation-compose 2.7.7
- Compose compiler: Kotlin Compose plugin 2.0.21 (version = Kotlin version). NEVER set `composeOptions.kotlinCompilerExtensionVersion` — the 1.5.x artifact pairs with Kotlin 1.9.x only (Build #5).
- Coil 2.7.0 · Media3 1.3.1 · kotlinx-serialization-json 1.7.3 · OkHttp 4.12.0
- Room 2.6.1 · DataStore Preferences 1.1.1 · lifecycle-viewmodel-compose 2.8.5
- Single module `app`. MVVM + Repository. No Hilt (manual DI in `di/AppContainer.kt` — keep it simple, keep it working).

## Design tokens (design agent owns the exact values; these roles are FIXED)
- Dark-first. Background near-black with a purple tint (web Mix uses ~#0A0714). Gold accent #E8C266. Live red dot (breaking). Static Live Star logo (NEVER animated).
- Type: **Liberation Sans Bold** for display/headlines, **Inter** for body/UI (LOCKED 2026-11-12). Bundle as res/font; fetch TTFs at build time from public URLs (fonts are OFL/free) — NO binary files in git (GitHub connector is UTF-8 only).
- Logo: find the canonical Live Star logo in the website repo (`brookstarone/makryden-master-demo`, public assets) — SVG preferred (text, git-safe). If only PNG exists, the build downloads it from its public makrydensystems.com URL at build time.

## Theme API (design agent implements; screen builders consume — signatures are FIXED)
Package `com.makrydensystems.news.ui.theme`:
- `StarBrookTheme(content: @Composable () -> Unit)` — wraps MaterialTheme with StarBrook colors/type.
- `object StarBrookColors` (as CompositionLocal via StarBrookTheme): `background, surface, surfaceVariant, textPrimary, textSecondary, gold, liveRed, border, accentPurple, onGold`.
- `object StarBrookType`: `display, headline, title, body, bodySmall, caption, label` — TextStyles using the two families.
- `StarBrookShapes`: `card = 16.dp`, `chip = 999.dp`, `sheet = 24.dp top`.

## Component API (design agent implements; signatures FIXED)
Package `com.makrydensystems.news.ui.components`:
- `FeaturedCard(story: Story, onClick: () -> Unit, modifier: Modifier = Modifier)`
- `NewsCard(story: Story, onClick: () -> Unit, modifier: Modifier = Modifier)`
- `BreakingStrip(breaking: Breaking?, onClick: () -> Unit)` — hidden entirely when null.
- `LiveNowRow(item: LiveItem, onClick: () -> Unit)` — one line: red dot + headline + time.
- `SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null)`
- `VideoCard(video: BriefingVideo, onClick: () -> Unit)` — poster + duration chip.
- `GoldOriginalBadge()` — gold pill "StarBrook Original".
- `SkeletonCard()` / `SkeletonList(count: Int)` — shimmer, used while cache is empty.
- `EmptyState(title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null)`
- `ErrorState(message: String, onRetry: () -> Unit)`
- `StarBrookTopBar(title: String)` and bottom nav `StarBrookBottomNav` (News / Weather / Saved / Settings).

## Data contracts (data agent implements; VERIFIED live 2026-11-19)
Base: `https://bamrgsjgvakyucyqtwlg.supabase.co/storage/v1/object/public/`
- `news-editions/edition-morning.json`, `edition-evening.json` → `{"stories":[{"title","source","excerpt","link","published":ms-epoch,"section","image","n"}]}` (15 stories; sections national/local/tech/business/culture).
- `news-editions/breaking.json` → `{"breaking":null | {...},"detectedAt":...}` — null means nothing qualifies; strip clears.
- `news-editions/latest.json` → `{"stories":[{"t","s","a","u","published","label"}]}` (compact keys; label "live").
- `news-videos/news-briefing-manifest.json` → `{"latest":"2026-11-19-morning","videos":[{"id","date","edition","breaking","title","duration":"m:ss","file","poster"}]}`. Video URL = base + `news-videos/<file>`; poster likewise. Weather video: `news-videos/weather/midday-<zip>-<date>.mp4` (HEAD to check existence).
- Originals: published rows of `news_original_stories` via Supabase PostgREST with the **anon key** (public in portal JS — read it from the website repo's public JS, NEVER print or commit it; pass via BuildConfig field from env). Verify the exact query by reading the live read page JS (`~/workspace/fullstory/website/read.js`) and `~/workspace/fullstory/server/functions/story-body-extract/index.ts`.
- Story body: `story-body-extract` function (same dir as above) — read how the read page invokes it; mirror that call.
- Weather (all public, no keys) — **NWS IS THE STANDARD (Matthew's word ~12:33 CST 2026-11-19, "I like national weather service"; video-builder switch DONE ~12:47, proven with live midday + evening test builds). Open-Meteo is NOT used** — it failed the midday video twice on 2026-11-19 (11:20 timeout + 12:18 429s) and killed the 73102 evening video twice on Nov 18. Forecast via `https://api.weather.gov/points/<lat>,<lon>` → forecast + forecastHourly URLs; alerts `https://api.weather.gov/alerts/active?point=<lat>,<lon>`; ZIP→lat/lon via `https://api.zippopotam.us/us/<zip>`; RainViewer `https://api.rainviewer.com/public/weather-maps.json` + tiles `https://tilecache.rainviewer.com/v2/radar/<time>/256/<z>/<x>/<y>/2/1_1.png`.
- **INSTANT STANDARD (his words):** render cached data immediately, refresh quietly behind it. NEVER a blank load, NEVER a website-style spinner. Cache-first repository: Room cache read first, network refresh in background.
- **ZIP DEFAULT RULE:** default/example ZIP must NOT be 73120 or OKC. No visitor ZIP = prompt, no video, no OKC. Weather ZIP and local-news ZIP are the SAME setting.
- Shelf v1 = local Room only (no login required — free, no paywall/ads/login-to-read). Account sync is a later step.
- Push v1 = hooks only. A `PushModule` stub with a clearly marked wiring point. App MUST compile with NO `google-services.json` and NO Firebase project. Do NOT create a Firebase project.

## Navigation (navigation-compose)
Routes: `news` (home), `weather`, `saved`, `settings`, `story?url={encoded}` (native read view), `video/{videoId}`, `search`. Bottom nav: News / Weather / Saved / Settings.

## Screens (Phase 2 builders)
- Home/News: Morning/Evening edition switcher, featured lead, Top Stories, Live now compact list (no horizontal swipe), breaking strip, video briefings row, Originals with gold badge + byline "Reported by the StarBrook Newsroom — [Beat] Desk. Edited by CADE."
- Read view: native rendering of extracted article body (title, source, time, hero image, paragraphs). No WebView anywhere in the app.
- Search, Shelf (saved, offline), Weather tab (current + 7/15-day, RainViewer radar as native tiles, per-ZIP video, NWS alerts), Settings (editions, sections, ZIP, notification toggles).

## Direction update — 2026-11-19 ~12:30 CST (Matthew's words: "Needs to match the browser if not even better in my opinion. Push the boundaries hard and impress me with the design. I want a live fully working app. Log in portal all built in the app. Plays videos radar. Wire the backend to cade so it can push updates.")
1. **DESIGN BAR — match the browser or better.** The live web News page (the Mix) and Weather page are the reference, NOT a ceiling. The native app must equal or exceed them in polish; it must never feel like a lesser version of the site. Push the boundaries hard; impress him with the design.
2. **LIVE, FULLY WORKING** — real data from the live backend only, every screen working end to end. No mock content, no placeholders, no dead buttons.
3. **PORTAL LOGIN BUILT INTO THE APP** — the Makryden portal (https://makrydensystems.com/account/) lives inside the app: sign in with the same Makryden account (Supabase Auth — mirror the portal's flow; session persists like the portal's `mk-portal-session`). Signed-in features inside the app: Newsroom prefs (editions, sections, ZIP), Shelf (saved stories, SYNCED with the portal Shelf — local Room remains the offline cache), and Master Room access for operators (brookstarone@gmail.com + mattdouglas9216@gmail.com) including the CADE chat (via the `master-room` function, verify_jwt=false by design, in-function `callerEmail()` operator gate). Do NOT touch the existing StarBrook app's sign-out behavior (`doDocsSignOut` stays byte-identical).
4. **VIDEOS + RADAR PLAY IN-APP** — daily briefing videos and breaking flashes play natively (Media3/ExoPlayer); radar works in the Weather tab (native RainViewer tiles).
5. **CADE PUSHES UPDATES** — wire the backend so CADE's pipeline can push updates to the app: when the pipeline fires breaking news or a new edition lands (breaking.json, edition JSONs, `news_breaking_sends`), the app gets it — native push (FCM) plus live content refresh, no reopen/reinstall needed. Build the wiring and the app side so it works the moment the FCM project exists (the project itself is still an open item — which Google account — do NOT create it). Keep the in-app self-update path for new app versions (the Updater pattern from the scope).
- New components (design agent adds, same rules as the component API): `LoginForm`, `AccountRow`, `ChatBubble`, `ChatInput`, `MasterRoomCard`.
- Shelf rule updated: v1 = local Room offline cache + portal sync when signed in (was: local only).

## Signing (coordinator-owned)
- Keystore generated on Linux with the VM's keytool; alias `starbrook-news`; stored in `keystore.properties` (0600, workdir only, NEVER committed) + `starbrook-news-upload.jks` (0600, workdir only). Passwords are NEVER printed, NEVER in chat, NEVER in the repo. Parent moves them to Secure Vault + GitHub secrets later; CI signing step is written to read GitHub secrets and skip gracefully when absent (local release build is signed with the local keystore).
- CI workflow `.github/workflows/build.yml`: ubuntu-24.04, JDK 17, setup-android, `./gradlew assembleDebug assembleRelease`, upload APK artifacts. CI green is the gate.

## Rules
- Zero mojibake/garbled text. Zero crashes on the happy path. Every screen has loading (skeleton) / empty / error states.
- Reusable old code: `~/workspace/starbrook-news-build/app/src/main/java/com/makryden/starbrook/news/` (Oct 20, old WebView app) — data agent reviews NewsModels/EditionBuilder/NewsSanitize/RssParser/NewsFetcher for reusable PARSING logic only; UI is not reusable.
- DO NOT publish/deploy: no download-page update, no starbrook-releases public release, no Play submission, no Play account creation.
