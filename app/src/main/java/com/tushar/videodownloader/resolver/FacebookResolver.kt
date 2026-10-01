package com.tushar.videodownloader.resolver

import com.tushar.videodownloader.core.DownloadError
import com.tushar.videodownloader.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/**
 * Resolves public Facebook videos and reels through `plugins/video.php` — the embed
 * endpoint Facebook publishes so third-party sites can embed public videos. It
 * exposes `hd_src` and `sd_src`, giving two genuine quality options.
 *
 * Short `/share/...` links are followed to their canonical URL first, since the embed
 * endpoint needs the real video URL. Private and friends-only videos expose nothing
 * on this surface and fall back to the preview path with an honest message.
 */
class FacebookResolver(
    private val httpClient: HttpClientProvider,
) : OpenGraphResolver(httpClient) {

    override val platform: Platform = Platform.FACEBOOK

    private val hosts = setOf(
        "facebook.com", "www.facebook.com", "m.facebook.com", "fb.watch", "fb.com",
    )

    override fun canHandle(url: HttpUrl): Boolean = url.host in hosts

    override suspend fun resolve(url: HttpUrl): Result<ResolvedMedia> {
        resolveViaEmbed(url)?.let { return Result.success(it) }
        resolvePhoto(url)?.let { return Result.success(it) }

        val fallback = super.resolve(url)

        // Facebook answers a gated video with a full login page rather than an error,
        // so "nothing readable at all" here means sign-in required, not missing.
        val failure = fallback.exceptionOrNull() as? ResolveException
        if (failure?.error is DownloadError.NoMediaFound && failure.preview == null) {
            return Result.failure(ResolveException(DownloadError.AuthenticationRequired))
        }
        return fallback
    }

    private suspend fun resolveViaEmbed(url: HttpUrl): ResolvedMedia? =
        withContext(Dispatchers.IO) {
            val canonical = resolveShareLink(url) ?: url

            val encoded = URLEncoder.encode(canonical.toString(), "UTF-8")
            val embedUrl = "https://www.facebook.com/plugins/video.php?href=$encoded"
                .toHttpUrlOrNull() ?: return@withContext null

            val html = fetchHtml(embedUrl) ?: return@withContext null

            val options = FacebookEmbedParser.findQualities(html)
            if (options.isEmpty()) return@withContext null

            ResolvedMedia(
                sourceUrl = url.toString(),
                title = "Facebook video",
                // Reels carry no thumbnail in the embed payload, while other video
                // types do. Falling back to the post's og:image covers both, and that
                // tag is served even where the video itself is gated.
                thumbnailUrl = FacebookEmbedParser.findThumbnailUrl(html)
                    ?: findOpenGraphImage(canonical),
                options = options,
                platform = platform,
            )
        }

    private fun findOpenGraphImage(canonical: HttpUrl): String? =
        fetchHtml(canonical)?.let { OpenGraphParser.findContent(it, "og:image") }

    /**
     * Resolves a photo post through the canonical page's `og:image`.
     *
     * Facebook serves that at up to 1152×2048 — the image as posted — whereas the post
     * embed only offers a 280px thumbnail. Reached only after the video path found
     * nothing, and abandoned when the page declares an `og:video`, since that means a
     * gated video rather than a photo and should be reported as needing sign-in.
     *
     * Two agents are tried, because Facebook does not serve the same page to both — see
     * [PHOTO_USER_AGENTS]. The `og:video` check is repeated for each, so a gated video
     * is still never mistaken for a photo and saved as its poster frame.
     */
    private suspend fun resolvePhoto(url: HttpUrl): ResolvedMedia? = withContext(Dispatchers.IO) {
        val canonical = resolveShareLink(url) ?: url

        for (userAgent in PHOTO_USER_AGENTS) {
            val html = fetchHtml(canonical, userAgent) ?: continue

            if (OpenGraphParser.findContent(html, "og:video") != null) return@withContext null
            val imageUrl = OpenGraphParser.findContent(html, "og:image") ?: continue

            return@withContext ResolvedMedia(
                sourceUrl = url.toString(),
                title = "Facebook photo",
                thumbnailUrl = imageUrl,
                options = listOf(MediaOption(label = "Original", url = imageUrl)),
                platform = platform,
                kind = MediaKind.IMAGE,
            )
        }
        null
    }

    /** `/share/r/<id>/` and `fb.watch` links redirect to the canonical video URL. */
    private fun resolveShareLink(url: HttpUrl): HttpUrl? {
        val needsResolving = url.host == "fb.watch" || url.encodedPath.startsWith("/share/")
        if (!needsResolving) return url

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .get()
            .build()

        return try {
            httpClient.client.newCall(request).execute().use { response ->
                val resolved = response.request.url
                // A `.php` endpoint carries its identity in the query — `story.php`
                // is nothing without `story_fbid`. Path-based URLs like `/reel/<id>/`
                // only carry tracking there, so those are cleaned.
                if (resolved.encodedPath.endsWith(".php")) {
                    resolved
                } else {
                    resolved.newBuilder().query(null).build()
                }
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun fetchHtml(url: HttpUrl, userAgent: String = EMBED_USER_AGENT): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
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

    private companion object {
        const val EMBED_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

        /**
         * An album photo's page (`photo.php`) is an empty JavaScript shell unless the
         * request comes from a link-preview crawler Facebook allowlists by name; no
         * self-identifying agent qualifies. So the app sends one of those names, and is
         * claiming an identity that is not its own — stated here because it is a
         * deliberate call, not an oversight. It changes who the server thinks is asking,
         * never what is asked for.
         */
        const val PREVIEW_CRAWLER_USER_AGENT = "Twitterbot/1.0"

        /** Tried in order: the ordinary agent first, the crawler only if that found nothing. */
        val PHOTO_USER_AGENTS = listOf(EMBED_USER_AGENT, PREVIEW_CRAWLER_USER_AGENT)
    }
}
