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

        val fromEmbed = resolveViaEmbed(url)
        if (fromEmbed != null) return Result.success(fromEmbed)

        val fallback = super.resolve(url)

        // A post for which Instagram serves nothing at all — no video, thumbnail or
        // title — is private/restricted, not missing.
        val failure = fallback.exceptionOrNull() as? ResolveException
        if (failure?.error is DownloadError.NoMediaFound && failure.preview == null) {
            return Result.failure(ResolveException(DownloadError.AuthenticationRequired))
        }
        return fallback
    }

    private suspend fun resolveViaEmbed(url: HttpUrl): ResolvedMedia? =
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

            val videoUrl = InstagramEmbedParser.findVideoUrl(html) ?: return@withContext null

            val username = InstagramEmbedParser.findUsername(html)
            ResolvedMedia(
                sourceUrl = url.toString(),
                title = username?.let { "$it — Instagram video" } ?: "Instagram video",
                thumbnailUrl = InstagramEmbedParser.findThumbnailUrl(html),
                qualities = listOf(VideoQuality(label = "Original", url = videoUrl)),
                platform = platform,
            )
        }

    /** `/reel/<code>/` → `https://www.instagram.com/reel/<code>/embed/` */
    private fun buildEmbedUrl(url: HttpUrl): HttpUrl? {
        val segments = url.pathSegments.filter { it.isNotBlank() }
        if (segments.size < 2) return null
        return "https://www.instagram.com/${segments[0]}/${segments[1]}/embed/".toHttpUrlOrNull()
    }

    private companion object {
        // Deliberately a plain WebKit UA: a modern-Chrome UA gets the script-driven
        // embed with no inline video_url; a simple UA gets the static variant that
        // carries it (verified empirically).
        const val EMBED_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
}
