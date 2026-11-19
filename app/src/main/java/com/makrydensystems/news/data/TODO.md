# StarBrook News data layer — deferred (v1 does public data only)

v1 ships with **public data only**: edition JSONs, breaking.json,
latest.json, the briefing manifest, NWS weather, Zippopotam, RainViewer,
and local Room storage. No credential of any kind is handled in this layer
— no API keys, no anon keys, no tokens, no passwords, not in code, not in
files. Each item below names what it needs and what v1 does instead.

## 1. PostgREST Originals (StarBrook Originals)
- **Needs:** the Supabase anon key (parent supplies later) + the exact
  PostgREST query against `news_original_stories` (published rows). The
  contract points at the live read-page JS
  (`~/workspace/fullstory/website/read.js`) and
  `~/workspace/fullstory/server/functions/story-body-extract/index.ts`
  for the verified query — read those before implementing; never print or
  commit the key (pass via a BuildConfig field from env).
- **v1 instead:** `Story.isOriginal` / `Story.beat` exist on the model and
  default to `false` / `null`; no Originals are fetched, so no story is
  ever shown with the gold Original badge in v1. The `story-body-extract`
  call for the native read view is likewise deferred with it (same key).
- **Also deferred with it:** the RSS feed (`StarBrookApi.RSS_URL` is
  declared as a constant only — nothing fetches it in v1).

## 2. Supabase Auth — portal login
- **Needs:** the same Supabase project + anon key, mirroring the portal
  flow (`makrydensystems.com/account/`, session persists like the
  portal's `mk-portal-session`); operator gate is the in-function
  `callerEmail()` check, not the UI.
- **v1 instead:** no sign-in at all. Reading is free with no login
  (no paywall, no ads). The login UI components (`LoginForm`,
  `AccountRow`) exist in the design layer but have no data behind them
  yet; Settings exposes only local prefs.

## 3. Shelf sync
- **Needs:** Supabase Auth (item 2) + the portal Shelf table/API.
- **v1 instead:** Shelf is local Room only (`saved_stories` table,
  `saveStory` / `unsaveStory` / `savedFlow` / `isSavedFlow`). It renders
  instantly offline and never leaves the device. Sync is additive later:
  local rows remain the offline cache.

## 4. Master Room / CADE chat
- **Needs:** Supabase Auth (item 2) + the `master-room` function
  (verify_jwt=false by design, in-function operator gate) for status
  cards and the CADE chat stream.
- **v1 instead:** nothing. No Master Room screen data, no chat calls.
  Non-operators never see the screen; operators get it once items 2 + 4
  land together.

## 5. Push (FCM)
- **Needs:** a Firebase project (open item — which Google account;
  do NOT create it) + `google-services.json` + the Firebase Messaging
  dependency. Wiring point: `data/di/PushModule.kt` (stub — comment
  only, no Firebase imports in v1).
- **v1 instead:** no push at all. The notification *toggles* in
  `SettingsStore` (breaking/morning/evening/weather) are stored locally
  and ready to drive subscriptions the moment FCM lands; until then
  they change nothing. Breaking/edition freshness still arrives via
  the quiet background refresh in the repository flows (re-fetch on
  collect + `refreshAll()`), not via push.

## 6. Feels-like temperature (minor, NWS)
- **Needs:** a gridpoint read (`/gridpoints/{office}/{x},{y}` →
  `properties.apparentTemperature`) to fill
  `CurrentConditions.feelsLikeF`, which is null in v1 — NWS
  forecast/hourly periods carry no feels-like value.
- **v1 instead:** `feelsLikeF = null`; the UI must hide the feels-like
  line when it is null rather than showing a placeholder.

## 7. RainViewer tile URL — VERIFIED + FIXED in v1
- Coordinator check 2026-11-19 ~13:00 CST: a tile built from the epoch
  returns HTTP 410; a tile built from host + frame `path` returns HTTP
  200 (direct HEAD, live). The interface was changed accordingly:
  `radarFrames(): List<RadarFrame>` (time + path) and
  `radarTileUrl(frame, z, x, y)` builds host + path +
  `/256/{z}/{x}/{y}/2/1_1.png`. Radar ships on the verified pattern.
