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
        val fromEmbed = resolveViaEmbed(url)
        if (fromEmbed != null) return Result.success(fromEmbed)

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

            val qualities = FacebookEmbedParser.findQualities(html)
            if (qualities.isEmpty()) return@withContext null

            ResolvedMedia(
                sourceUrl = url.toString(),
                title = "Facebook video",
                // Reels carry no thumbnail in the embed payload, while other video
                // types do. Falling back to the post's og:image covers both, and that
                // tag is served even where the video itself is gated.
                thumbnailUrl = FacebookEmbedParser.findThumbnailUrl(html)
                    ?: findOpenGraphImage(canonical),
                qualities = qualities,
                platform = platform,
            )
        }

    private fun findOpenGraphImage(canonical: HttpUrl): String? =
        fetchHtml(canonical)?.let { OpenGraphParser.findContent(it, "og:image") }

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
                // Strip tracking parameters; the embed endpoint wants a clean URL.
                response.request.url.newBuilder().query(null).build()
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun fetchHtml(url: HttpUrl): String? {
        val request = Request.Builder()
            .url(url)
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

    private companion object {
        const val EMBED_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
}
