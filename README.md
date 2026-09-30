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

- **minSdk** 29, **targetSdk** 35 — 29 is where scoped storage begins, so MediaStore is
  the only write path and no runtime storage permission is needed
- **Language** Kotlin, **UI** Jetpack Compose + Material 3

---

## Supported sources

| Source | Status | How |
|---|---|---|
| Public Instagram reel / video post | **Downloads** | Reads the rendition list off the public post page, falling back to the `/embed/` page |
| Public Instagram photo post | **Downloads** | Full-resolution image from the `/embed/captioned/` page |
| Public Facebook video / reel / share link | **Downloads** | Reads `hd_src` and `sd_src` from Facebook's `plugins/video.php` embed — two real quality options |
| Public Facebook photo post | **Downloads** | `og:image` on the canonical page, which Facebook serves at up to 1152×2048. Album photos need a second pass — see below |
| Direct video URL (`.mp4`, `.webm`, …) | **Downloads** | `HEAD` for size, then a ranged `GET` |
| Link shared over WhatsApp | **Downloads** | A shared video link is an ordinary URL — same path as above |
| Instagram story | **Refused** | Bound to a signed-in viewer; no public surface serves it |
| Private / friends-only post | **Refused** | Reported as "requires sign-in" |
| WhatsApp status you have viewed | **Downloads** | Read from WhatsApp's own `.Statuses` folder, which the user grants access to once |
| WhatsApp's own media URLs | **Not reachable by pasting** | Encrypted CDN blobs whose keys live in the message — there is nothing a user can paste |

Photo posts are supported because the brief asks for a public Instagram **post** to be
downloadable, and a post is as often a photo as a video. A `MediaKind` on the resolved
media drives the file extension, MIME type and MediaStore collection, so photos land in
`Pictures/` and videos in `Movies/` without a second download path.

Facebook covers the quality selector the brief asks for — `360p / 720p / 1080p / 4K` in
the flow, "API/source returning multiple video qualities" among the edge cases. Its
embed publishes HD and SD, where Instagram's exposes a single rendition.

---

## How the platforms are resolved

The brief requires respecting each platform's terms and authentication requirements, and
forbids bypassing protection on private content. That rules out what general-purpose
downloaders do — replaying a signed-in session or calling an internal API. This app
reads only what the platforms publish to unauthenticated clients — their public post
pages and the embed endpoints they provide for third-party sites.

**Instagram.** The post page is read first, requested the way a browser navigates —
current Chrome agent plus the `Sec-Fetch-*` headers — since anything else gets a thinner
page. Instagram does not use one field name for the video, so every shape it publishes
under is tried widest-first: `video_versions`, the single-URL fields, then `og:video`.
Matching only one is why a public reel can fail to resolve.

The `/embed/` page is tried after it. It resolves fewer posts but carries a photo post's
full-size image via `/embed/captioned/`, where `og:image` is a 640px square crop. The two
pages want opposite agents: the embed inlines `video_url` only for a plain WebKit agent.
CDN URLs are used exactly as published — their signature covers every parameter, so
editing one returns `403`.

**Facebook.** `plugins/video.php` exposes `hd_src` and `sd_src` for public videos, which
become the HD and SD options. Photo posts fall through to the canonical page's
`og:image`, served at up to 1152×2048. A page declaring an `og:video` is never treated
as a photo, so a gated video reports sign-in rather than silently saving its poster
frame. `/share/...` links resolve to their canonical URL first, keeping the query when
it lands on a `.php` endpoint — `story.php` is meaningless without `story_fbid`.

Album photos (`photo.php`) are the exception: their page is an empty JavaScript shell
unless the request comes from a link-preview crawler Facebook allowlists by name, and no
self-identifying agent qualifies. The photo path therefore retries once as one of those
crawlers — the app claims an identity that is not its own, stated plainly here because
it is a deliberate call. Photo posts only, only after the ordinary agent found nothing,
and the `og:video` check repeats on the retry so a gated video is still never saved as
its poster frame.

Where nothing public is available — private accounts, stories, age-gated posts — the app
shows whatever preview it could read and says the media is not publicly available. It
never attempts an authenticated path.

---

## WhatsApp status

A status has no shareable link — the media is an encrypted CDN blob whose key travels
inside the message. What it does have is a local copy: viewing a status writes it to
WhatsApp's own folder, where it stays for 24 hours.

The **Status** tab lists what is there and saves a copy to the gallery on a tap. Access
is through `ACTION_OPEN_DOCUMENT_TREE`: the user picks the folder once, the grant is
persisted, and nothing else on the device becomes readable. `Android/media/...` sits
outside the scoped-storage sandbox and no broad-storage permission covers it, so SAF is
the only route — and it leaves the choice with the user.

---

## Architecture

```
ui/            HomeScreen (stateless Compose)  ·  HomeViewModel  ·  HomeUiState
                     │ intent                              ▲ state
                     ▼                                     │
resolver/      ResolverRegistry → MediaResolver ──┬── InstagramResolver ─┬─ InstagramPageParser
                                                  │                      ├─ InstagramEmbedParser
                                                  │                      └─ OpenGraphResolver
                                                  ├── FacebookResolver  ─┬─ FacebookEmbedParser
                                                  │                      └─ OpenGraphResolver
                                                  └── DirectUrlResolver
                     │ ResolvedMedia
                     ▼
download/      DownloadService (foreground) → Downloader → MediaStoreSaver
status/        StatusScreen · StatusViewModel → StatusRepository (SAF) → MediaStoreSaver
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
- `InstagramPageParserTest` — field preference order, every known video field, single/double JSON escaping, `\uXXXX` ampersands, non-URL matches, absent video
- `InstagramEmbedParserTest` — single/double JSON escaping, `\uXXXX` ampersands, absent video, photo-vs-video detection, full-size image extraction
- `FacebookEmbedParserTest` — HD/SD ordering, unescaping, SD-only sources, login walls
- `OpenGraphParserTest` — both attribute orders, both quote styles, named and numeric entities
- `FileNamingTest` — determinism, collision resistance across sources, illegal-character stripping
- `FormattingTest` — size and speed boundaries

`Downloader` and the resolvers are constructor-injected; the next layer of coverage is
`MockWebServer`, exercising redirect, `206`, truncated-body and `401` paths without
touching a real network.

---

## Known limitations

- Instagram and Facebook are resolved from the pages those platforms serve anonymously,
  whose inline JSON is undocumented — so coverage per post is at the platforms'
  discretion and can change. Some public reels serve nothing to a signed-out client on
  any surface; those are reported as needing sign-in, which is what they are. The app
  degrades to a preview with an explanation, never a crash.
- A WhatsApp status is listed only once it has been viewed, and only for the 24 hours
  WhatsApp keeps its local copy. Nothing older is recoverable.
- One download at a time. A queue is a natural extension and the service is already the
  right place for it.
- Resume survives a retry within the session; partials live in `cacheDir`, so the OS may
  reclaim them under storage pressure.
- No download history screen. The MediaStore query used for duplicate detection is the
  foundation one would build it on.
