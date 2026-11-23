# StarBrook app updates — release step (in-app updater, Build #25)

The app is sideloaded (no Play Store). Since Build #25 it checks for
its own updates and prompts with an in-app **Update** button: tap it,
the APK downloads in the app, and the system installer opens. Android
always asks the user to tap Install once — the app never promises a
silent update, and the dialog copy says so.

## Server side

Public JSON manifest, same bucket as the video manifest:

- **Manifest URL:** `https://bamrgsjgvakyucyqtwlg.supabase.co/storage/v1/object/public/news-videos/app-update.json`
- **APK URL:** `https://bamrgsjgvakyucyqtwlg.supabase.co/storage/v1/object/public/news-videos/app/starbrook-news-debug.apk`
  (one stable "latest" object, overwritten per build — never a Drive
  link: Drive serves an interstitial page for large files and the app
  cannot download through it.)

Manifest shape:

```json
{
  "versionCode": 42,
  "versionName": "Build 25",
  "apkUrl": "https://bamrgsjgvakyucyqtwlg.supabase.co/storage/v1/object/public/news-videos/app/starbrook-news-debug.apk",
  "releaseNotes": "Breaking flashes now surface at the top of Home, with an update banner. Toggle and ZIP fixes.",
  "mandatory": false
}
```

- `versionCode` — the CI run's `github.run_number` (see below). The
  app prompts only when this is HIGHER than its own
  `BuildConfig.VERSION_CODE`.
- `versionName` — display string, e.g. `"Build 25"`.
- `apkUrl` — direct download URL of the APK built by THAT SAME CI run.
  The APK's own versionCode must equal the manifest's, or the app
  keeps prompting after install.
- `releaseNotes` — one or two plain sentences, shown in the prompt.
- `mandatory` — `true` means the prompt cannot be dismissed (no
  "Later", no back-press out). Use sparingly — a broken mandatory
  build bricks the app's front door until the next build.

## Release step (every build — whoever delivers the build does this)

1. CI green on main. Note the workflow run number (`github.run_number`
   — the per-workflow counter on the run page, NOT the long run id).
2. Download the `starbrook-news-debug` artifact (`app-debug.apk`).
   Verify it is the run from step 1.
3. Upload it to the public bucket at
   `news-videos/app/starbrook-news-debug.apk` (overwrite).
4. Write `news-videos/app-update.json` with `versionCode` = the run
   number from step 1, `versionName` = `"Build N"`, `apkUrl` = the
   bucket URL above, `releaseNotes` = what's new, `mandatory` as
   decided.
5. Verify, live: fetch the manifest URL (200, versionCode matches
   step 1) and HEAD the apkUrl (200).

## How the app decides

- On launch, and once every 24h while open, the app fetches
  `app-update.json` (one small JSON; a failed fetch is silent).
- It prompts only when `manifest.versionCode > BuildConfig.VERSION_CODE`
  and the user has not tapped "Later" on that version.
- Content updates (editions, flashes) never trigger it — it reads only
  this manifest and only the versionCode.
- Tapping **Update** downloads the APK to app-private cache storage
  and opens the system installer via FileProvider. If "install unknown
  apps" is not allowed for StarBrook yet, the dialog sends the user to
  Android's per-app settings screen with plain wording, and Update
  retries afterwards.
- A downloaded APK is reused: backing out of the installer leaves an
  **Install** button that opens it again without re-downloading.

## versionCode — read this before touching the workflow

`app/build.gradle.kts` takes the versionCode from
`-PappVersionCode` (CI passes `${{ github.run_number }}`); local
builds default to 1. `github.run_number` is the small per-workflow
run counter — NOT `github.run_id` (the long id overflows int32).
If the workflow is ever recreated and the counter restarts, the
release step must pick a versionCode HIGHER than any shipped build
and keep incrementing from there, or installed apps will never see
another update (and an APK with a lower code will not install over
a higher one).
