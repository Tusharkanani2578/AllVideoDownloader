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

## Supported sources

| Source | Status | How |
|---|---|---|
| Public Instagram post / reel | **Downloads** | Reads `video_url` from Instagram's public `/embed/` page |
| Public Facebook video / reel / share link | **Downloads** | Reads `hd_src` and `sd_src` from Facebook's `plugins/video.php` embed — two real quality options |
| Direct video URL (`.mp4`, `.webm`, …) | **Downloads** | `HEAD` for size, then a ranged `GET` |
| Link shared over WhatsApp | **Downloads** | A shared video link is an ordinary URL — same path as above |
| Instagram story | **Refused** | Bound to a signed-in viewer; no public surface serves it |
| Private / friends-only post | **Refused** | Reported as "requires sign-in" |
| WhatsApp's own media URLs | **Not reachable by pasting** | Encrypted CDN blobs whose keys live in the message — there is nothing a user can paste |

Facebook is included because the brief asks for a quality selector — `360p / 720p /
1080p / 4K` in the flow, and "API/source returning multiple video qualities" among the
edge cases. Instagram's embed exposes one rendition and a progressive `.mp4` is a single
file; Facebook's embed publishes HD and SD, so the selection path runs against a source
that genuinely offers a choice.

---

## How the platforms are resolved

The brief requires respecting each platform's terms, authentication requirements and
technical limitations, and forbids bypassing protection on private content. That rules
out the approach general-purpose downloaders use — replaying a signed-in session, or
calling an internal API on the user's behalf. This app reads only what the platforms
serve to unauthenticated clients.

### Instagram

Instagram's post page serves rich preview metadata to an anonymous client but no media
URL. Verified against a public reel:

```
GET https://www.instagram.com/reel/<shortcode>/     → 200 OK, 711 KB

og:type ✓   og:title ✓   og:image ✓   og:url ✓   og:description ✓
og:video ✗

Full-body search for a media URL:
  "video_url" 0 · "contentUrl" 0 · "video_versions" 0 · any .mp4 reference 0
```

No login wall is served — the page simply carries no video URL. Instagram's own client
fetches that separately through an authenticated call.

The `/embed/` page does carry it. That endpoint exists so third-party websites can embed
public posts, and it is served without authentication:

```
GET https://www.instagram.com/reel/<shortcode>/embed/    → 200 OK (no login)

with a modern Chrome UA:  640 KB script-driven embed …    video_url ✗
with a plain WebKit UA:   280 KB static legacy embed …    video_url ✓  (CDN .mp4)
```

Instagram serves two embed variants by user agent, and the static one carries
`video_url` inline. The resolver requests that variant, reads the URL, and downloads
from Instagram's CDN. No login, no session replay, no third-party service.

Where the embed carries no video — private accounts, stories, age-gated posts — the app
shows the preview it could read and states that the video is not publicly available. It
never attempts an authenticated path.

### Facebook

`plugins/video.php` is Facebook's public video embed endpoint. For public videos it
exposes `hd_src` and `sd_src`, which become the HD and SD options. `/share/...` links
are followed to their canonical URL first. Gated videos expose nothing there and are
reported as requiring sign-in.

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

**Why a foreground service rather than `WorkManager`:** the download streams progress
into a live UI at sub-second granularity while also surviving the Activity.
`WorkManager` is built for deferrable work and its progress channel is coarser than
that; a foreground service with a `StateFlow` gives the UI an exact feed and the user a
cancellable notification. `WorkManager` becomes the right call once downloads need
queuing and retry across reboots.

**Why no DI framework:** the graph is four objects wide and stateless apart from the
HTTP client. Every class takes its dependencies through the constructor, so substituting
a fake `NetworkMonitor` or resolver in a test needs no container. Hilt earns its keep
once the graph spans feature modules.

---

## UX decisions

The flow in the brief is five steps. Each of these removes one of them, or removes a way
to get stuck inside one.

| Decision | Why |
|---|---|
| **Clipboard link is offered on resume** | The user arrives having just copied a link somewhere else. A banner offers it; it is never pasted silently, and it is suppressed once a result is on screen. |
| **Paste resolves immediately** | Paste-then-press-Fetch is a step with no decision in it. |
| **Results scroll into view** | On a short screen the result renders below the fold, so the user would press Download and appear to get nothing. |
| **Open and Share on completion** | A download is finished when the user can watch the video, not when a file name appears. |
| **Retry is conditional** | A private video or an unsupported host will never succeed on a second attempt, so no retry button is drawn. |
| **Cancellation is not styled as an error** | The user pressed the button the app offered them; alarm colours would be telling them off for it. |
| **Quality chips disable mid-download** | Changing the rendition while bytes are moving has no meaning; greying them out says so without an error message. |
| **Indeterminate bar when size is unknown** | Some sources send no `Content-Length`. A bar sitting at a confident 0% is a lie; an indeterminate one is not. |
| **Thumbnail dimmed when undownloadable** | Signals "found, but unavailable" without pretending the download is about to start. |
| **`singleTop` launch mode** | A link shared while the app is open reaches the running screen instead of stacking a second copy behind it. |

The UI follows the system light/dark theme and adopts Material You dynamic colour on
Android 12+, so it matches the device rather than imposing a palette.

---

## Edge cases

Each one in the brief maps to a case of the sealed `DownloadError` type, so the compiler
forces the UI to render a state for all of them — a new failure cannot be added without
every `when` being updated.

| Edge case | Handling |
|---|---|
| Invalid URL | `UrlValidator` extracts the first URL from pasted share text and validates the host. Nothing usable → `InvalidUrl` |
| Unsupported URL | No resolver claims the host → `UnsupportedPlatform(host)`, naming the host |
| No downloadable media | The link resolves but carries no media, or a direct link answers with a non-video content type → `NoMediaFound`. Where a preview exists but the video is withheld → `NoPublicMedia`, which says which platform and why |
| Network unavailable | `NetworkMonitor` requires a *validated* route before any request, so Wi-Fi with no internet counts as offline. Fails immediately instead of waiting out a timeout |
| Download interrupted | Partial bytes stay in a `.part` file; retry resumes with a `Range` header. A `200` reply to a ranged request is detected and the partial discarded |
| Insufficient storage | Checked before starting against the resolved size plus a 50 MB headroom, then re-checked once the real `Content-Length` is known. `ENOSPC` mid-write maps to the same error |
| User cancels | Cancellation propagates through the coroutine to the read loop. The partial file is deliberately kept so the download can resume later |
| Very large video | Streamed through a 64 KB buffer — memory use is constant regardless of file size. Progress is throttled to 5 updates/sec so the UI is not flooded |
| Duplicate download | The file name is deterministic per (source URL, quality); MediaStore is queried for it before any bandwidth is spent → `AlreadyDownloaded` |
| App goes to background | The transfer runs in a foreground service with a cancellable notification, independent of the Activity and the ViewModel |
| Multiple qualities | `ResolvedMedia` carries a rendition list and the UI renders a chip per entry, defaulting to the highest. Facebook supplies HD and SD; a source with one rendition is labelled by its real height rather than padded with resolutions it cannot serve |
| Requires authentication | Story and private links → `AuthenticationRequired`. No bypass is attempted |

Two further cases the brief did not list, but that occur in practice:

- **Server refuses the request** → `ServerError(code)`, with the status shown.
- **No `Content-Length`** → percentage is `null` and the UI shows an indeterminate bar
  rather than a fake 0%.

---

## Tests

Pure-JVM unit tests cover the logic worth protecting from regression:

- `UrlValidatorTest` — share-text extraction, trailing punctuation, hosts without a dot, non-HTTP schemes
- `InstagramEmbedParserTest` — single/double JSON escaping, `\uXXXX` ampersands, absent video
- `FacebookEmbedParserTest` — HD/SD ordering, unescaping, SD-only sources, login walls
- `OpenGraphParserTest` — both attribute orders, both quote styles, named and numeric entities
- `FileNamingTest` — determinism, collision resistance across sources, illegal-character stripping
- `FormattingTest` — size and speed boundaries

`Downloader` and the resolvers are constructor-injected; the next layer of coverage is
`MockWebServer`, exercising redirect, `206`, truncated-body and `401` paths without
touching a real network.

---

## Known limitations

- Instagram and Facebook are resolved through the platforms' public embed endpoints.
  Those are served anonymously and built for third-party embedding, but their inline
  JSON is undocumented, so coverage per post is at the platforms' discretion and can
  change. The app degrades to a preview with an explanation, never a crash.
- One download at a time. A queue is a natural extension and the service is already the
  right place for it.
- Resume survives a retry within the session; partials live in `cacheDir`, so the OS may
  reclaim them under storage pressure.
- No download history screen. The MediaStore query used for duplicate detection is the
  foundation one would build it on.
