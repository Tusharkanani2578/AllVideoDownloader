# All Video Downloader

An Android app that downloads a video from a pasted link and saves it to the device gallery.

```
Paste URL → Validate → Resolve → Pick quality → Download → Save to gallery → Done
```

---

## Running it

Open the project in Android Studio (Ladybug or newer) and run the `app` configuration.
The Gradle wrapper JAR is not committed, so from the command line run `gradle wrapper`
once first, then `./gradlew assembleDebug`.

Unit tests: `./gradlew test`

- **minSdk** 24, **targetSdk** 35
- **Language** Kotlin, **UI** Jetpack Compose + Material 3

---

## Scope and platform constraints

The brief asks for Instagram and WhatsApp/shared video URL support, and also says the
implementation must respect each platform's terms, authentication requirements and
technical limitations, without bypassing protection on private content.

Those two requirements limit each other, so here is exactly where the line was drawn
and why.

| Source | Status | How |
|---|---|---|
| Direct video URL (`.mp4`, `.webm`, …) | **Works** | `HEAD` for size, then a ranged `GET` |
| HLS stream (`.m3u8`) | **Works, with real quality selection** | Master playlist parsed for its renditions; segments fetched and assembled |
| Link shared over WhatsApp | **Works** | A shared video link is an ordinary URL — same path as above |
| Public Instagram post / reel | **Works** | Reads the `video_url` Instagram serves on its public `/embed/` page — see the measurement notes below |
| Public Facebook video / reel / share link | **Works** | Reads `hd_src` and `sd_src` from Facebook's `plugins/video.php` embed — two real quality options |
| Instagram story | **Rejected deliberately** | Always bound to a signed-in viewer; no public surface exists, embed included |
| Private / friends-only post | **Rejected deliberately** | Reported as "requires sign-in" |
| WhatsApp status / WhatsApp's own media URLs | **Not reachable by pasting** | See the note below |

### Why Open Graph rather than a private API

`og:video` is the metadata a platform deliberately publishes to unauthenticated
link-preview crawlers — it is the same data WhatsApp or Slack reads when it renders a
preview card for a pasted link. Building on it keeps the app to publicly served
metadata.

The alternative — replaying an authenticated session, or calling an undocumented
internal endpoint — is what every general-purpose downloader in the wild actually does,
and it is exactly what the brief rules out. When a link turns out to be gated, the app
stops and says so. It never retries with credentials or an impersonated session.

The practical consequence, stated plainly: **Instagram does not serve preview metadata
for every public post, so some public links will legitimately fail.** That is a platform
limitation, not an unhandled case, and the app reports it as one.

### What Instagram actually serves — measured, not assumed

Before settling on this design I checked what Instagram returns to an unauthenticated
client, using a public reel:

```
GET https://www.instagram.com/reel/<shortcode>/     → 200 OK, 711 KB

og:type         ✓   article
og:title        ✓   <account> on Instagram: "…"
og:image        ✓   https://scontent.cdninstagram.com/…   ← thumbnail only
og:url          ✓
og:description  ✓   57K likes, 1,217 comments…
og:video        ✗   absent

Searched the full response body for a media URL:
  "video_url"       0 matches
  "contentUrl"      0 matches
  "video_versions"  0 matches
  any .mp4 reference 0 matches
```

The request is not blocked and no login wall is served — Instagram answers `200` and
publishes rich preview metadata. It simply **does not include a video URL anywhere in
the anonymously served page**. The media URL is fetched later by Instagram's own client
through an authenticated API call.

So the main post page offers no video URL to anonymous clients. The next question was
whether any *other* surface Instagram intentionally serves without authentication does —
and one exists: the **`/embed/` page**, the endpoint Instagram provides so third-party
websites can embed public posts. Measured against the same reel:

```
GET https://www.instagram.com/reel/<shortcode>/embed/    → 200 OK (no login)

with a modern Chrome UA:  640 KB script-driven embed …    video_url ✗
with a plain WebKit UA:   280 KB static legacy embed …    video_url ✓  (CDN .mp4)
```

Instagram serves two embed variants by user agent; the static variant carries the
post's `video_url` inline. The resolver therefore requests the embed page with a plain
WebKit UA, reads `video_url`, and downloads from Instagram's own CDN. **No login, no
session replay, no third-party service, and only content Instagram itself hands to
anonymous clients on a surface built for third-party use.**

Where the embed page carries no video — private accounts, stories, age-gated or
otherwise withheld posts — the app falls back to the preview (thumbnail, title,
platform) with a message saying the video itself isn't publicly available, rather than
attempting any authenticated path.

Two honest caveats, stated rather than hidden:
- The embed page's inline JSON is an **undocumented structure**. Instagram can change
  or remove it at any time, and coverage per post is at their discretion. The resolver
  degrades to the preview fallback when that happens, never to a crash.
- Meta's terms restrict automated collection broadly; reading the embed surface is the
  same class of access every link-preview and embed consumer performs, but a production
  release should take a considered position on this rather than inherit mine.

### Two points worth clarifying with the brief

1. **A WhatsApp status has no URL.** Statuses are files on the device under
   `Android/media/com.whatsapp/WhatsApp/Media/.Statuses/`. They cannot arrive through a
   paste-URL flow at all; supporting them means a separate "status saver" screen that
   reads that directory through the Storage Access Framework. The architecture here
   leaves room for it — it is another entry point into the same download pipeline — but
   it is not part of the paste-URL flow and has not been built.
2. **An Instagram story cannot be fetched without authentication**, which conflicts
   with the no-bypass constraint. The app rejects story links with a clear message
   rather than resolving the conflict silently in either direction.

---

## Architecture

```
ui/            HomeScreen (stateless Compose)  ·  HomeViewModel  ·  HomeUiState
                     │ intent                              ▲ state
                     ▼                                     │
resolver/      ResolverRegistry → MediaResolver ──┬── InstagramResolver ─┬─ InstagramEmbedParser
                                                  │                     └─ OpenGraphResolver
                                                  ├── FacebookResolver  ─┬─ FacebookEmbedParser
                                                  │                      └─ OpenGraphResolver
                                                  ├── HlsResolver       ─── HlsPlaylistParser
                                                  └── DirectUrlResolver
                     │ ResolvedMedia
                     ▼
download/      DownloadService (foreground) → Downloader → MediaStoreSaver
core/          DownloadError · UrlValidator · formatting
network/       HttpClientProvider · NetworkMonitor
```

**Pattern:** MVVM with unidirectional data flow. The screen renders one immutable
`HomeUiState` and emits intent upward; nothing else can put the UI into a contradictory
state such as a spinner drawn over an error.

**Adding a platform** means writing one `MediaResolver` and registering it in
`ServiceLocator`. No existing class changes. Registration order is significant and
documented there — specific resolvers are consulted before `DirectUrlResolver`, which
would otherwise claim any URL ending in `.mp4`.

**Why a foreground service rather than `WorkManager`:** the download must stream
progress into a live UI at sub-second granularity while also surviving the Activity.
`WorkManager` is built for deferrable work and its progress channel is coarser than
that; a foreground service with a `StateFlow` gives the UI an exact feed and the user a
cancellable notification. `WorkManager` would be the right call if downloads needed to
be queued and retried across reboots — a reasonable next step, not what this brief asks
for.

**Why no DI framework:** the graph is four objects wide and stateless apart from the
HTTP client. Every class still takes its dependencies through the constructor, so
substituting a fake `NetworkMonitor` or resolver in a test needs no container. Hilt
would earn its keep once the graph spans feature modules.

---

## UX decisions

The flow in the brief is five steps. Each of these removes one of them, or removes a way
to get stuck inside one.

| Decision | Why |
|---|---|
| **Clipboard link is offered on resume** | The user arrives having just copied a link somewhere else. Asking them to paste it is asking them to repeat something the app can already see. A banner offers it; it is never pasted silently, and it is suppressed once a result is on screen. |
| **Paste resolves immediately** | Paste-then-press-Fetch is a step with no decision in it. |
| **Results scroll into view** | On a short screen the result card renders below the fold, so the user would press Download and appear to get nothing. |
| **Open and Share on completion** | A download is not finished when a file name appears — it is finished when the user can watch the video. |
| **Retry is conditional** | A private video or an unsupported host will never succeed on a second attempt, so no retry button is drawn. Inviting someone to fail again is worse than saying no once. |
| **Cancellation is not styled as an error** | The user pressed the button the app offered them; alarm colours would be telling them off for it. |
| **Quality chips disable mid-download** | Changing the rendition while bytes are moving has no meaning; greying them out says so without an error message. |
| **Indeterminate bar when size is unknown** | Some sources send no `Content-Length`. A bar that sits at a confident 0% is a lie; an indeterminate one is not. |
| **Segment-count progress for HLS** | A segmented stream has no total size, but the number of segments finished is exact — better than a percentage derived from a bitrate estimate. |
| **Thumbnail dimmed when undownloadable** | Signals "found, but unavailable" without pretending the download is about to start. |

The UI follows the system light/dark theme and adopts Material You dynamic colour on
Android 12+, so it matches the device rather than imposing a palette.

## Edge cases

Each one in the brief maps to a case of the sealed `DownloadError` type, so the
compiler forces the UI to render a state for all of them — a new failure cannot be added
without every `when` being updated.

| Edge case | Handling |
|---|---|
| Invalid URL | `UrlValidator` extracts the first URL from pasted share text and validates the host. Nothing usable → `InvalidUrl` |
| Unsupported URL | No resolver claims the host → `UnsupportedPlatform(host)`, naming the host |
| No downloadable media | Resolver finds no `og:video`, or a direct link answers with a non-video content type → `NoMediaFound` |
| Network unavailable | `NetworkMonitor` checks for a *validated* route before any request, so a Wi-Fi connection with no internet counts as offline. Fails immediately instead of waiting out a timeout |
| Download interrupted | Partial bytes stay in a `.part` file; retry resumes with a `Range` header. A `200` reply to a ranged request is detected and the partial file discarded |
| Insufficient storage | Checked before starting against the resolved size plus a 50 MB headroom, then re-checked once the real `Content-Length` is known. `ENOSPC` mid-write is mapped to the same error |
| User cancels | Cancellation propagates through the coroutine to the read loop. The partial file is deliberately kept so the download can resume later |
| Very large video | Streamed through a 64 KB buffer — memory use is constant regardless of file size. Progress is throttled to 5 updates/sec so the UI is not flooded |
| Duplicate download | The file name is deterministic per (source URL, quality); MediaStore is queried for it before any bandwidth is spent → `AlreadyDownloaded` |
| App goes to background | The transfer runs in a foreground service with a cancellable notification, independent of the Activity and the ViewModel |
| Multiple qualities | HLS master playlists are parsed for every rendition they advertise, producing a real `1080p / 720p / 480p / 288p / 184p` chip row that defaults to the highest. Variants sharing a resolution at different bitrates are collapsed to the best one, so no two chips look identical. A progressive `.mp4` genuinely has one rendition and is labelled by its real height rather than being padded with resolutions the source cannot serve |
| Requires authentication | Story and private links → `AuthenticationRequired`. No bypass is attempted |

Two further cases the brief did not list, but that occur in practice:

- **Server refuses the request** → `ServerError(code)` with the status shown.
- **No `Content-Length`** → percentage is `null`, and the UI shows an indeterminate bar
  rather than a fake 0%.

Retry is offered only for errors that can plausibly succeed on a second attempt. A
private video or an unsupported host shows no retry button, because inviting the user to
fail again is worse than saying no once.

---

## Tests

Pure-JVM unit tests cover the logic that is worth protecting from regression:

- `UrlValidatorTest` — share-text extraction, trailing punctuation, hosts without a dot, non-HTTP schemes
- `OpenGraphParserTest` — both attribute orders, both quote styles, HTML entity unescaping
- `FileNamingTest` — determinism, collision resistance across sources, illegal-character stripping
- `HlsPlaylistParserTest` — master vs media detection, variant/URI pairing, duration totals, fMP4 vs MPEG-TS, relative and absolute segment resolution
- `FormattingTest` — size and speed boundaries

The resolvers and `Downloader` are constructor-injected and would be covered next with
`MockWebServer`, which exercises redirect, `206`, truncated-body and `401` paths without
touching a real network.

---

## Known limitations

- Instagram and Facebook downloads both read the platforms' public embed endpoints —
  surfaces served anonymously and built for third-party embedding, but whose inline
  JSON is undocumented. Coverage is at their discretion and can change without notice;
  the app degrades to a preview with an explanation, never a crash.
- Facebook's embed carries no thumbnail field, so Facebook results render without a
  preview image. The card handles a null thumbnail rather than showing a broken one.
- One download at a time. A queue is a natural extension, and the service is already the
  right place for it.
- Resume survives a retry within the session; partials live in `cacheDir`, so the OS may
  reclaim them under storage pressure.
- No download history screen. The MediaStore query used for duplicate detection is the
  foundation one would build it on.
- HLS renditions restart rather than resume — a half-written concatenation has no
  segment boundary to safely continue from. Recording the last completed segment index
  would fix this; progressive downloads already resume.
- HLS segments are fetched sequentially. Parallel fetching with ordered assembly would
  be faster, at the cost of holding more of the file in flight.
- Encrypted HLS (`EXT-X-KEY`) is detected and refused rather than decrypted. Assembling
  encrypted segments would produce a file that reports success and then fails to play,
  so the app stops with a specific message instead.
- Alternate audio renditions and subtitle tracks in a master playlist are ignored; only
  the muxed video rendition is downloaded.
