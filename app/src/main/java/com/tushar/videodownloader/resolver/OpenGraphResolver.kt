package com.tushar.videodownloader.resolver

import com.tushar.videodownloader.core.DownloadError
import com.tushar.videodownloader.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * Base resolver for platforms that publish Open Graph tags on public pages — the same
 * metadata link-preview crawlers read. No authentication is ever attempted; gated
 * pages resolve to [DownloadError.AuthenticationRequired].
 */
abstract class OpenGraphResolver(
    private val httpClient: HttpClientProvider,
) : MediaResolver {

    /** Paths that are always session-bound, rejected before any request. */
    protected open val authenticatedPathPrefixes: List<String> = emptyList()

    override suspend fun resolve(url: HttpUrl): Result<ResolvedMedia> = withContext(Dispatchers.IO) {
        if (authenticatedPathPrefixes.any { url.encodedPath.startsWith(it) }) {
            return@withContext Result.failure(ResolveException(DownloadError.AuthenticationRequired))
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .get()
            .build()

        try {
            httpClient.client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    return@withContext Result.failure(
                        ResolveException(DownloadError.AuthenticationRequired)
                    )
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        ResolveException(DownloadError.ServerError(response.code))
                    )
                }

                val html = response.body?.string().orEmpty()
                if (html.isBlank()) {
                    return@withContext Result.failure(ResolveException(DownloadError.NoMediaFound))
                }

                // A 200 that landed on the login flow means the post is gated.
                if (response.request.url.encodedPath.contains("/login")) {
                    return@withContext Result.failure(
                        ResolveException(DownloadError.AuthenticationRequired)
                    )
                }

                val title = OpenGraphParser.findContent(html, "og:title")
                val thumbnail = OpenGraphParser.findContent(html, "og:image")

                val videoUrl = OpenGraphParser.findContent(html, "og:video")
                    ?: OpenGraphParser.findContent(html, "og:video:secure_url")

                if (videoUrl == null) {
                    // Preview data without a video URL: the platform withholds the
                    // video from anonymous clients. Report why, keep what was read.
                    val error = if (title != null || thumbnail != null) {
                        DownloadError.NoPublicMedia(platform.displayName)
                    } else {
                        DownloadError.NoMediaFound
                    }
                    return@withContext Result.failure(
                        ResolveException(
                            error = error,
                            preview = title?.let { MediaPreview(it, thumbnail, platform) },
                        )
                    )
                }

                Result.success(
                    ResolvedMedia(
                        sourceUrl = url.toString(),
                        title = title ?: "${platform.displayName} video",
                        thumbnailUrl = thumbnail,
                        qualities = buildQualities(html, videoUrl),
                        platform = platform,
                    )
                )
            }
        } catch (e: IOException) {
            Result.failure(ResolveException(DownloadError.Unexpected(e)))
        }
    }

    /** OG advertises one rendition; label it by its real height, don't invent options. */
    protected open fun buildQualities(html: String, videoUrl: String): List<VideoQuality> {
        val height = OpenGraphParser.findContent(html, "og:video:height")?.toIntOrNull() ?: 0
        val label = if (height > 0) "${height}p" else "Original"
        return listOf(VideoQuality(label = label, url = videoUrl, heightPx = height))
    }
}
