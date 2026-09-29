package com.tushar.videodownloader.resolver

import com.tushar.videodownloader.core.DownloadError
import com.tushar.videodownloader.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * Resolves HLS streams into their advertised renditions — the genuine source of
 * multi-quality selection. A media playlist passed directly resolves to one entry.
 */
class HlsResolver(
    private val httpClient: HttpClientProvider,
) : MediaResolver {

    override val platform: Platform = Platform.HLS_STREAM

    override fun canHandle(url: HttpUrl): Boolean =
        url.encodedPath.substringAfterLast('.', "").lowercase() in setOf("m3u8", "m3u")

    override suspend fun resolve(url: HttpUrl): Result<ResolvedMedia> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", HttpClientProvider.USER_AGENT)
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

                val content = response.body?.string().orEmpty()
                if (!HlsPlaylistParser.isPlaylist(content)) {
                    return@withContext Result.failure(ResolveException(DownloadError.NoMediaFound))
                }

                val qualities = if (HlsPlaylistParser.isMasterPlaylist(content)) {
                    buildVariantQualities(url, content)
                } else {
                    listOf(VideoQuality(label = "Original", url = url.toString(), isHls = true))
                }

                if (qualities.isEmpty()) {
                    return@withContext Result.failure(ResolveException(DownloadError.NoMediaFound))
                }

                Result.success(
                    ResolvedMedia(
                        sourceUrl = url.toString(),
                        title = url.pathSegments.lastOrNull()?.substringBeforeLast('.')
                            .orEmpty()
                            .ifBlank { "HLS stream" },
                        thumbnailUrl = null,
                        qualities = qualities,
                        platform = platform,
                    )
                )
            }
        } catch (e: IOException) {
            Result.failure(ResolveException(DownloadError.Unexpected(e)))
        }
    }

    private fun buildVariantQualities(playlistUrl: HttpUrl, content: String): List<VideoQuality> =
        HlsPlaylistParser.parseMaster(content)
            .mapNotNull { variant ->
                val resolved = HlsPlaylistParser.resolveUri(playlistUrl, variant.uri)
                    ?: return@mapNotNull null
                VideoQuality(
                    label = variant.toLabel(),
                    url = resolved.toString(),
                    heightPx = variant.heightPx,
                    isHls = true,
                    bandwidthBitsPerSecond = variant.bandwidthBitsPerSecond,
                )
            }
            // Keep the best bitrate per height so no two chips look identical.
            .groupBy { it.heightPx }
            .map { (_, sameHeight) -> sameHeight.maxBy { it.bandwidthBitsPerSecond } }
            .sortedByDescending { it.heightPx }

    private fun HlsPlaylistParser.Variant.toLabel(): String = when {
        heightPx > 0 -> "${heightPx}p"
        bandwidthBitsPerSecond > 0 -> "${bandwidthBitsPerSecond / 1000} kbps"
        else -> "Original"
    }
}
