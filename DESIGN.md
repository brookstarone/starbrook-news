# StarBrook News — native app design system (Phase 1)

**Status:** Phase 1 complete — theme, components, fonts, logo, icon.
**Bar (Matthew, 2026-11-19):** match the browser if not better. Push the boundaries hard.
The live web News page (the Mix) and Weather page are the reference, **not the
ceiling**. This app must never feel like a lesser version of the site.
**Package:** `com.makrydensystems.news` · **Dark-first, dark-only.** There is no
light theme: StarBrook is a night broadcast.

---

## 1. Palette

Matches the live site EXACTLY (Matthew's word 2026-11-19 ~13:53 CST).
Tokens are the live `:root` values from the News/Weather pages (identical
on both). Gold is the only accent that shouts; purple is the quiet second
voice; red is reserved for live/breaking/alert — nowhere else.

| Token | Hex | Site token | Role |
|---|---|---|---|
| `background` | `#0E0A1A` | `--night` | App background (the Mix page bg) |
| `surface` | `#1B1233` | `--card` | Cards, sheets, top bar, bottom nav |
| `surfaceVariant` | `#160F2B` | `--deep` | Pressed states, chips, image fallback, skeletons |
| `textPrimary` | `#F3EEFF` | `--text` | Headlines, body |
| `textSecondary` | `#A79DC4` | `--dim` | Source lines, timestamps, meta, captions |
| `gold` | `#E8C266` | `--gold` | Accent, CTAs, selected tab, Original badge, play |
| `goldsoft` | `#2A1F45` | `--goldsoft` | Gold-tinted dark surface, selected chips |
| `golddark` | `#C9A227` | `--golddark` | Darker gold, pressed/hover gold |
| `liveRed` | `#B91C1C` | `--red` | Live dot, breaking strip, errors, alerts |
| `reddeep` | `#7F1D1D` | `--reddeep` | Deep red, breaking backgrounds |
| `redsoft` | `#FCA5A5` | `--redsoft` | Light red, alert text on dark |
| `border` | `#3A2E5C` | `--line` | Hairlines, field outlines, dividers |
| `accentPurple` | `#8B7CF6` | — (no site token) | Secondary highlights, links, CADE accents |
| `onGold` | `#1A1206` | — (functional) | Text/icons on gold |

The Live Star logo artwork keeps its own red dot (`#EA252A`, measured off
the logo PNG) — only the UI red token follows the site. Logo gold measures
`#E7BC59`; the UI uses the site gold `#E8C266` everywhere including the
logo — one gold.

**Extended (guidance, not tokens — use sparingly, always on dark):**
weather radar uses the RainViewer palette as served; NWS alert banners
use `liveRed` (severe) / `gold` (watch) / `accentPurple` (advisory).
Success states use `gold`, never green — the app has one accent. (The site
also defines `--green #34D399`; not adopted as a token.)

## 2. Type scale

Two families, locked 2026-11-12. **Liberation Sans Bold** for display /
headlines (the broadcast voice). **Inter** for body / UI (legibility at
small sizes). Line heights are generous on purpose.

| Style | Family | Size / Line height | Use |
|---|---|---|---|
| `display` | Liberation Sans Bold | 32 / 36 | Hero, featured lead, error mark |
| `headline` | Liberation Sans Bold | 26 / 31 | Card headlines, section headers (Mix lead is 27px mobile) |
| `title` | Liberation Sans Bold | 19 / 24 | NewsCard headlines, subheads, dialog titles |
| `body` | Inter Regular | 16 / 24 | Excerpts, read-view paragraphs, chat |
| `bodySmall` | Inter Regular | 14 / 20 | Secondary copy, card excerpts |
| `caption` | Inter Regular | 12 / 16 | Source lines, timestamps, meta |
| `label` | Inter SemiBold | 12 / 16, +0.5 tracking | Buttons, chips, eyebrows, tabs |

Rules: headlines `maxLines` + ellipsis everywhere (3 for featured,
3 for cards, 2 for video titles, 1 for Live now rows). Never a fixed
height on a text container. Never all-caps body copy; eyebrows only.

## 3. Spacing & shapes

Spacing scale (dp): **4 · 8 · 12 · 16 · 20 · 24 · 32 · 48**.
Screen edge padding 16. Card-to-card gap 12. Section-to-section gap 24.
Card internal padding 12–16. Minimum touch target 48×48 (bottom nav,
chips, retry, send).

Shapes: card `16.dp`, chip `999.dp` (fully round), sheet `24.dp` top only.

## 4. Logo — the Live Star

**Provenance.** No SVG of the logo exists anywhere: the website repo
(`brookstarone/makryden-master-demo`) has no public logo asset, and
`newslogo-work/`, `starbrook-logo-concepts/`, `news-video-work/logo-concepts/`
hold PNG/WebP concepts only. The canonical mark is the **240×195 PNG
embedded base64 in the live News page masthead**
(`https://makrydensystems.com/news/`, `img.newsmark`, extracted from
`~/workspace/news-redesign-work/news_mix.html`).

**Vector.** `res/drawable/ic_live_star.xml` is a measurement-faithful
trace of that PNG (verified pixel-by-pixel, not eyeballed):

- 4-point star, straight edges: tips `(93,2) (164,97) (93,193) (2,97)`;
  waist `(73,76) (113,76) (114,117) (72,117)`.
- Red dot: centre `(125.5,21.5)`, r `8.5`, `#EA252A`.
- Three `)` broadcast arcs, circular, stroke 9, round caps:
  A centre `(106,97)` r `86`, `(153,25)→(153,169)`;
  B centre `(-130.5,97)` r `343.5`, `(206,28)→(206,166)`;
  C centre `(140,97)` r `94`, `(160,5)→(160,190)`.
- Viewport `240×195`. Gold `#E8C266` throughout.

**Rules. STATIC ONLY — never animated, never rotated, never recoloured.**
The web pulses its red dot; the app keeps it still. The logo is the
brand; it does not move. `LiveStarMark()` renders it; `LiveDot()` is the
matching 8dp red dot for Live now rows and the breaking strip.

**Launcher icon.** Adaptive icon (`res/mipmap-anydpi-v26/ic_launcher.xml`
+ `ic_launcher_round.xml`): background `#0A0714`
(`res/drawable/ic_launcher_background.xml`), foreground
(`res/drawable/ic_launcher_foreground.xml`) = the Live Star scaled
0.32 and centred (spans x 15.6–92.4, y 22.8–85.2 in the 108 viewport,
so star and arcs survive the mask). minSdk 26, so adaptive-only is
correct — no legacy PNG needed.

## 5. Fonts

| File | Source | SHA-256 |
|---|---|---|
| `inter_regular.ttf` | Inter 4.1, `extras/ttf/Inter-Regular.ttf` | `40d692fce188e4471e2b3cba937be967878f631ad3ebbbdcd587687c7ebe0c82` |
| `inter_medium.ttf` | Inter 4.1, `extras/ttf/Inter-Medium.ttf` | `97ad806f526e41546d46365bb3a393145f75b7b1568913db74549ad8b8dba872` |
| `inter_semibold.ttf` | Inter 4.1, `extras/ttf/Inter-SemiBold.ttf` | `78a843fade9d4612a5567302fb595b56976eb5fcebf4fea5a5912d638bafcde3` |
| `inter_bold.ttf` | Inter 4.1, `extras/ttf/Inter-Bold.ttf` | `288316099b1e0a47a4716d159098005eef7c0066921f34e3200393dbdb01947f` |
| `liberation_sans_bold.ttf` | Liberation Sans 2.1.5, Ubuntu `fonts-liberation` | `3973aa5054fb467dd5627245d3dc82e37bf16fe075756156a570455871351582` |

Download URLs (pinned, also in `gradle/font-download.gradle`):
- Inter zip: `https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip`
  (SHA-256 `9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e`,
  SIL OFL 1.1).
- Liberation .deb: `http://archive.ubuntu.com/ubuntu/pool/main/f/fonts-liberation/fonts-liberation_2.1.5-3_all.deb`
  (SHA-256 `065c2ab1abc9108b17d401016dc594b79750904390f095845c93bb06e1153acc`,
  SIL OFL 1.1). Liberation publishes **no** release tarball with TTFs
  (GitHub releases carry no assets; the source tarball is `.sfd`
  only), so the canonical Ubuntu distro package is the pinned source;
  file used is `usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf`.
  A third-party GitHub mirror was checked and **rejected** (version
  2.00.1, not 2.1.5).

**Binaries are never committed.** `res/font/*.ttf` on disk now are
local build artifacts. Fresh checkouts regenerate them via
`./gradlew :app:downloadStarBrookFonts` (SHA-256-verified, incremental
— present + matching hash means no download). **Skeleton agent:** add
`apply(from = "$rootDir/gradle/font-download.gradle")` in
`app/build.gradle.kts`, hook `preBuild.dependsOn("downloadStarBrookFonts")`,
and gitignore `app/src/main/res/font/*.ttf`. The task needs `ar` + `zstd`
(or python3 + `zstandard`) for the Liberation .deb; both exist on
GitHub ubuntu-24.04 runners.

## 6. Components

All in `ui/components/`, exact contract signatures (plus the five new
portal components below). Every component handles long headlines,
missing images, and null excerpts without clipping or overflow — text
never runs past an edge. No mock content, no placeholder text, no dead
buttons: every action has a real handler, every state is a real state.

| Component | Anatomy |
|---|---|
| `FeaturedCard` | 16:9 image (Live Star fallback) → section eyebrow (gold, uppercase) → headline 26sp/3 lines → excerpt 14sp/2 lines → Original byline if applicable → `source · time`. Original badge overlays the image top-start. |
| `NewsCard` | Row: text (weight, title 19sp/3 lines, excerpt/2 lines, `source · time`) + fixed 96dp thumb (Live Star fallback). Null excerpt omits the line — no gap. |
| `BreakingStrip` | **Hidden entirely when `breaking == null`** — no empty red bar. Live red card, white dot + `BREAKING` + title (19sp/3 lines) + `source · age · N outlets`. One event = one treatment. |
| `LiveNowRow` | One line: 8dp red dot + headline (14sp, 1 line, ellipsis, weight) + time (12sp, never wraps). The Mix compact list — no horizontal swipe. |
| `SectionHeader` | Headline 26sp + optional gold action (`See all`). No action renders without a handler. |
| `VideoCard` | 16:9 poster (Live Star fallback) + centred gold play badge + duration chip bottom-end (live-red + `BREAKING` chip for flashes) → title 19sp/2 lines → `edition briefing · date`. Opens the native Media3 player. |
| `GoldOriginalBadge` | Gold pill, `StarBrook Original`, onGold text. Same words, same style, every screen. |
| `SkeletonCard` / `SkeletonList` | Shimmer (1.1s diagonal sheen, `surface → surfaceVariant`), shaped like a NewsCard. Cold start only — cached feeds render instantly. |
| `EmptyState` | Live Star 56dp + title + body (what to do next) + optional real action. |
| `ErrorState` | Plain language, no codes, one way out: gold `Try again`. |
| `StarBrookTopBar` | Live Star 36dp + title (19sp) + slogan `In the moment. In the now.` under the wordmark only. Hairline divider. |
| `StarBrookBottomNav` | News / Weather / Saved / Settings. Hand-drawn Canvas glyphs (newspaper, sun-behind-cloud, bookmark, sliders) — zero icon-font dependency. Gold = current tab, surface pill indicator. |

### New portal components (direction update 2026-11-19)

Signatures defined here (the contract names them; these are the exact
signatures Phase 2 builds against):

```kotlin
LoginForm(
  onSignIn: (email: String, password: String, keepSignedIn: Boolean) -> Unit,
  isLoading: Boolean = false,
  error: String? = null,
  onForgotPassword: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
)
AccountRow(email: String, displayName: String? = null, onSignOut: () -> Unit, modifier: Modifier = Modifier)
ChatBubble(message: ChatMessage, modifier: Modifier = Modifier)
ChatInput(onSend: (String) -> Unit, enabled: Boolean = true, placeholder: String = "Message CADE…", modifier: Modifier = Modifier)
MasterRoomCard(title: String, subtitle: String, statusLabel: String, statusIsAlert: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier)
data class ChatMessage(val text: String, val isFromUser: Boolean, val timestamp: Long = 0L, val senderName: String? = null)
```

- **LoginForm** — mirrors the portal (`makrydensystems.com/account/`):
  email + password (Supabase Auth password flow), `Keep me signed in`
  default on (session persists like the portal's `mk-portal-session`),
  Show/Hide password, inline field errors, server errors in a
  red-tinted box in **plain language** — never a raw Supabase message
  (e.g. "That email and password didn't match — check them and try
  again."). No password is ever displayed or logged. Signing in is
  optional: free to read, no paywall, no ads.
- **AccountRow** — initial avatar (gold circle, first letter of the
  email/display name), email + `Makryden account`, gold `Sign out`.
  App sign-out only — it never touches the browser app's sign-out.
- **ChatBubble / ChatInput** — CADE chat in the Master Room. CADE
  bubbles sit left on surface with a gold `CADE` label; yours sit
  right on gold. Send is disabled on blank input; the field grows to
  3 lines then scrolls internally. Chat intro is one line; bubbles
  carry the rest.
- **MasterRoomCard** — operator overview: gold (ok) or live-red
  (needs eyes) left bar, title + subtitle + status chip. Tapping
  opens the detail.

## 7. Model fields this layer assumes

The data layer owns the models; these are the fields the components
read (names follow the live JSON keys in CONTRACT.md). If the data
agent names them differently, the components' imports are the only
thing that changes — the signatures stay fixed.

```kotlin
// data.model — package com.makrydensystems.news.data.model
data class Story(
  val title: String, val source: String, val excerpt: String?,
  val link: String, val published: Long, // ms epoch
  val section: String, val image: String?,
  val isOriginal: Boolean = false, val beat: String? = null,
)
data class Breaking(
  val title: String, // JSON b.t
  val source: String, // b.s
  val age: String?, // b.a — display string, e.g. "25 min ago"
  val outletCount: Int = 1, // b.n — trust mark
  val url: String, // read-page link
)
data class LiveItem(
  val title: String, // JSON t
  val source: String, // s
  val age: String?, // a
  val url: String, // u
  val published: Long, val label: String = "live",
)
data class BriefingVideo(
  val id: String, val date: String, val edition: String,
  val isBreaking: Boolean = false, val title: String,
  val duration: String, // "m:ss"
  val file: String, val posterUrl: String?,
)
```

## 8. Motion

- **Press:** 0.96 scale on cards + surface → surfaceVariant, spring
  (`stiffness 400, damping 30`). Never a ripple alone — the whole card
  answers the thumb.
- **Shimmer:** 1.1s diagonal sheen, linear, endless. Skeletons only.
- **Live dot / Live Star:** static. No pulse, no rotation, no bounce.
- **Tab change / edition switch:** crossfade 150ms. No slides, no
  hero animations on first paint — the feed is already there.
- **Breaking arrival (CADE push):** strip fades/slides in once (200ms),
  then stays still. It never re-animates on refresh.
- **Reduced motion:** nothing in this system depends on motion for
  meaning; all information is present in the static frame.

## 9. Screens — what "designed perfect" means

Phase 2 builders: follow this, don't improvise the hierarchy.

- **Home / News (the Mix, native).** Order is the Mix order: top bar →
  breaking strip (only when live) → edition switcher (Morning /
  Evening, gold pill) → featured lead → Top Stories → Live now
  (compact vertical list, 3 by default, `See all live updates`
  expands in place) → video briefings → Originals (gold badge +
  byline `Reported by the StarBrook Newsroom — [Beat] Desk. Edited
  by CADE.`) → Markets compact. No horizontal swipe anywhere. Cached
  edition renders instantly; refresh happens quietly behind it.
  **Perfect =** the Mix's calm, but denser and faster: no loading
  screen, no jank, the lead image already there.
- **Read view.** Native rendering: title (display), source + time,
  hero image (Live Star fallback), paragraphs in `body` 16/24 with
  24dp paragraph gaps. No WebView anywhere. Pull the extracted body
  from `story-body-extract`; a failed extract is an ErrorState with
  Retry, never a blank page.
- **Weather.** Forecast source is **NWS (api.weather.gov)** — his word
  2026-11-19 ("I like national weather service"); Open-Meteo is out
  after repeated 429s killed video builds. Current conditions hero
  (big temp in Liberation Sans Bold, condition, feels-like, wind,
  humidity) → hourly strip (vertical list on small screens, never a
  swipe trap) → 7/15-day list → **radar as native RainViewer tiles** on a dark map, gold
  location pin, play/pause on the loop, timestamp chip → NWS alerts
  as banners (live-red severe / gold watch) → per-ZIP midday video
  card. **ZIP rule: no visitor ZIP = prompt, no video, no OKC, no
  73120. Ever.** Weather ZIP and local-news ZIP are the same setting.
  **Perfect =** the web Weather page's completeness with a native
  map that feels like a weather app, not a screenshot.
- **Video player.** Native Media3, 16:9, gold progress + gold play,
  poster as the pre-play frame, title + edition below, duration chip.
  Breaking flashes play the same way. No embedded web player.
- **Search.** Field on top (gold focus ring), results as NewsCards,
  empty state `No stories matched "…"`, error state with Retry.
- **Saved / Shelf.** Local Room cache renders instantly offline;
  signed in, it syncs with the portal Shelf. Empty state:
  `Nothing saved yet` + `Tap the bookmark on any story to keep it here.`
- **Settings / Account.** AccountRow on top (or LoginForm when signed
  out) → editions toggles (Morning/Evening/Breaking) → sections
  picker → ZIP field (same setting as Weather; never prefilled with
  73120) → notification toggles. Every toggle writes immediately and
  shows its state; nothing silently fails.
- **Login.** LoginForm centred, Live Star 48dp, one screen, no tabs.
  On success the session persists (portal parity) and the screen
  becomes AccountRow. Operators (`brookstarone@gmail.com`,
  `mattdouglas9216@gmail.com`) get the Master Room entry.
- **Master Room (operators only).** Status cards (MasterRoomCard) on
  top — edition state, breaking monitor, video pipeline — then the
  CADE chat (ChatBubble + ChatInput). The chat intro is one line.
  Non-operators never see this screen; the gate is the in-function
  `callerEmail()` operator check, not the UI.
- **Breaking push (CADE).** A pushed update surfaces as the
  BreakingStrip (same patterns, same red) plus a quiet refresh of
  Live now and the edition — no reopen, no reinstall, no modal.

## 10. Overflow test cases (checked by reasoning + preview)

Longest realistic strings the components must survive:

- Headline (112 chars): `Supreme Court blocks Texas data-center freeze in 5-4 ruling that could reshape the national AI buildout fight` — FeaturedCard 3 lines, NewsCard 3 lines, no push-out of meta.
- Breaking (98 chars): `White House announces Gaza peace declaration signed by 12 nations after marathon talks in Cairo` + `3 outlets` meta.
- Live now row: 90-char headline + `2h ago` — one line, ellipsis, time intact.
- Video title (76 chars): `StarBrook News Evening Briefing — November 19, 2026` + `1:32` chip.
- Source line: `The Hollywood Reporter · 3h ago` — one line.
- Chat: 400-char CADE reply — bubble grows, wraps, never clips.
- Email: `matthew.douglas.longname@example-domain.com` — AccountRow ellipsizes, avatar stays 44dp.
- Null excerpt + null image together — card renders headline + meta + Live Star thumb, no gap, no blank.

## 11. What the skeleton agent owes this layer

- `app/build.gradle.kts`: apply `gradle/font-download.gradle`, hook
  `preBuild.dependsOn("downloadStarBrookFonts")`, add
  `io.coil-kt:coil-compose:2.7.0` (components use `AsyncImage`),
  gitignore `app/src/main/res/font/*.ttf`.
- Data models in `com.makrydensystems.news.data.model` with the
  fields in §7 (especially `Story.isOriginal` / `Story.beat`,
  `Breaking.url`, `BriefingVideo.posterUrl` / `isBreaking`).
- Nothing else: no changes to these files' signatures without the
  design agent. The theme/component APIs in CONTRACT.md are fixed.
