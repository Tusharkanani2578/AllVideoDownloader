package com.tushar.videodownloader.resolver

import com.tushar.videodownloader.core.DownloadError
import com.tushar.videodownloader.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException

/**
 * Resolves public Instagram posts and reels through the `/embed/` page — the surface
 * Instagram serves anonymously for third-party embedding, whose inline JSON carries
 * the CDN `video_url`. Falls back to the OG preview when no public video exists.
 *
 * Stories are always session-bound (no public surface exists) and are rejected up
 * front. No authentication is ever attempted.
 */
class InstagramResolver(
    private val httpClient: HttpClientProvider,
) : OpenGraphResolver(httpClient) {

    override val platform: Platform = Platform.INSTAGRAM

    override val authenticatedPathPrefixes: List<String> = listOf("/stories/")

    private val hosts = setOf("instagram.com", "www.instagram.com", "instagr.am")

    private val publicPostPrefixes = listOf("/p/", "/reel/", "/reels/", "/tv/")

    override fun canHandle(url: HttpUrl): Boolean = url.host in hosts

    override suspend fun resolve(url: HttpUrl): Result<ResolvedMedia> {
        val isStory = authenticatedPathPrefixes.any { url.encodedPath.startsWith(it) }
        val isPost = publicPostPrefixes.any { url.encodedPath.startsWith(it) }

        if (isStory) {
            return Result.failure(ResolveException(DownloadError.AuthenticationRequired))
        }
        // Profile / explore links carry no single video.
        if (!isPost) {
            return Result.failure(ResolveException(DownloadError.NoMediaFound))
        }

        // The post page itself is the widest net — it carries the video under whichever
        // field name the page variant uses. The embed is tried after it because it
        // resolves fewer posts, and it is what surfaces a photo post's full-size image.
        resolveViaPostPage(url)?.let { return it }
        resolveViaEmbed(url)?.let { return it }

        val fallback = super.resolve(url)

        // A post for which Instagram serves nothing at all — no video, thumbnail or
        // title — is private/restricted, not missing.
        val failure = fallback.exceptionOrNull() as? ResolveException
        if (failure?.error is DownloadError.NoMediaFound && failure.preview == null) {
            return Result.failure(ResolveException(DownloadError.AuthenticationRequired))
        }
        return fallback
    }

    /**
     * Resolves from the post page itself.
     *
     * Requested the way a browser navigates, because Instagram serves a thinner page
     * variant to anything that does not look like one, and that variant carries fewer of
     * the fields the video can appear under.
     *
     * Every header below is load-bearing, not decoration: dropping `Accept` and
     * `Accept-Language` alone was measured returning a page ~25% smaller with no video
     * field on it. Removing any of them silently narrows how many reels resolve.
     */
    private suspend fun resolveViaPostPage(url: HttpUrl): Result<ResolvedMedia>? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .get()
                .build()

            val html = try {
                httpClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    response.body?.string().orEmpty()
                }
            } catch (e: IOException) {
                return@withContext null
            }

            val username = InstagramPageParser.findUsername(html)
            val cover = InstagramPageParser.findImageUrl(html)

            // A carousel is checked first: its page also carries an og:image, so looking
            // for a single piece of media would resolve the cover and quietly save that
            // instead of the slides the user came for.
            carousel(url, html, username, cover)?.let { return@withContext Result.success(it) }

            val videoUrl = InstagramPageParser.findVideoUrl(html) ?: return@withContext null

            Result.success(
                ResolvedMedia(
                    sourceUrl = url.toString(),
                    title = username?.let { "$it — Instagram video" } ?: "Instagram video",
                    thumbnailUrl = cover,
                    options = listOf(MediaOption(label = "Original", url = videoUrl)),
                    platform = platform,
                    kind = MediaKind.VIDEO,
                )
            )
        }

    /**
     * A carousel post, as one entry per slide.
     *
     * The link names the slide its sender was looking at in `img_index`, counting from
     * one, so that is the slide the screen opens on. The rest stay a tap away rather than
     * being lost, since a carousel is several separate things to save, not several
     * renditions of one.
     */
    private fun carousel(
        url: HttpUrl,
        html: String,
        username: String?,
        cover: String?,
    ): ResolvedMedia? {
        val items = InstagramPageParser.findCarouselItems(html)
        if (items.size < 2) return null

        val shared = url.queryParameter("img_index")?.toIntOrNull()?.minus(1)

        return ResolvedMedia(
            sourceUrl = url.toString(),
            title = username?.let { "$it — Instagram post" } ?: "Instagram post",
            thumbnailUrl = cover,
            options = items,
            platform = platform,
            // The post's own kind only covers slides that do not state one; every slide
            // this parser produces does.
            kind = MediaKind.IMAGE_WEBP,
            optionKind = OptionKind.ITEM,
            preferredIndex = shared?.takeIf { it in items.indices } ?: 0,
        )
    }

    /**
     * Resolves through the public embed page.
     *
     * @return the media when the post carries a video; a failure when the embed gives a
     *   definite answer the caller should not override — a photo post has no video to
     *   withhold, so it must not be reported as sign-in required; `null` when the embed
     *   says nothing useful and the Open Graph path should be tried instead.
     */
    private suspend fun resolveViaEmbed(url: HttpUrl): Result<ResolvedMedia>? =
        withContext(Dispatchers.IO) {
            val embedUrl = buildEmbedUrl(url) ?: return@withContext null

            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", EMBED_USER_AGENT)
                .header("Accept", "text/html")
                .get()
                .build()

            val html = try {
                httpClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    response.body?.string().orEmpty()
                }
            } catch (e: IOException) {
                return@withContext null
            }

            val username = InstagramEmbedParser.findUsername(html)

            // Each branch fetches only what it needs. Looking for an image up front cost
            // two extra requests on every video post the embed could not resolve — and
            // one of them was the same page the Open Graph fallback then fetched again.
            InstagramEmbedParser.findVideoUrl(html)?.let { videoUrl ->
                return@withContext Result.success(
                    ResolvedMedia(
                        sourceUrl = url.toString(),
                        title = username?.let { "$it — Instagram video" } ?: "Instagram video",
                        thumbnailUrl = InstagramEmbedParser.findThumbnailUrl(html),
                        options = listOf(MediaOption(label = "Original", url = videoUrl)),
                        platform = platform,
                        kind = MediaKind.VIDEO,
                    )
                )
            }

            // A photo post has no video to withhold, and its image is the thing to save.
            // The plain embed often carries no image, so the captioned variant is tried
            // next — it references the post image at full size, where og:image is only a
            // 640px square crop.
            if (!InstagramEmbedParser.isPhotoPost(html)) return@withContext null

            val imageUrl = InstagramEmbedParser.findThumbnailUrl(html)
                ?: fetchCaptionedEmbed(url)?.let(InstagramEmbedParser::findFullImageUrl)
                ?: findOpenGraphImage(url)
                ?: return@withContext null

            Result.success(
                ResolvedMedia(
                    sourceUrl = url.toString(),
                    title = username?.let { "$it — Instagram photo" } ?: "Instagram photo",
                    thumbnailUrl = imageUrl,
                    options = listOf(MediaOption(label = "Original", url = imageUrl)),
                    platform = platform,
                    kind = MediaKind.IMAGE,
                )
            )
        }

    /** `/p/<code>/embed/captioned/` — the variant that carries the full-size image. */
    private fun fetchCaptionedEmbed(url: HttpUrl): String? {
        val segments = url.pathSegments.filter { it.isNotBlank() }
        if (segments.size < 2) return null
        val captioned = "https://www.instagram.com/${segments[0]}/${segments[1]}/embed/captioned/"
            .toHttpUrlOrNull() ?: return null

        val request = Request.Builder()
            .url(captioned)
            .header("User-Agent", EMBED_USER_AGENT)
            .header("Accept", "text/html")
            .get()
            .build()

        return try {
            httpClient.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun findOpenGraphImage(postUrl: HttpUrl): String? {
        val request = Request.Builder()
            .url(postUrl)
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .header("Accept", "text/html")
            .get()
            .build()

        return try {
            httpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                OpenGraphParser.findContent(response.body?.string().orEmpty(), "og:image")
            }
        } catch (e: IOException) {
            null
        }
    }

    /** `/reel/<code>/` → `https://www.instagram.com/reel/<code>/embed/` */
    private fun buildEmbedUrl(url: HttpUrl): HttpUrl? {
        val segments = url.pathSegments.filter { it.isNotBlank() }
        if (segments.size < 2) return null
        return "https://www.instagram.com/${segments[0]}/${segments[1]}/embed/".toHttpUrlOrNull()
    }

    private companion object {
        /**
         * For the post page. Instagram serves a fuller page — the one carrying the
         * video fields — only to something that presents as a current browser.
         */
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        /**
         * For the embed page, where the opposite holds: a modern-Chrome agent gets the
         * script-driven embed with nothing inline, while a plain agent gets the static
         * variant that carries `video_url`. Both verified against live posts.
         */
        const val EMBED_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
}
