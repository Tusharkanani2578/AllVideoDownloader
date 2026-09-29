package com.tushar.videodownloader.resolver

import com.tushar.videodownloader.core.DownloadError
import com.tushar.videodownloader.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * Handles links that already point at a media file — plain `.mp4`-style URLs and
 * WhatsApp-shared CDN links. A HEAD request confirms the link and reveals the size
 * for the pre-flight storage check without pulling the body.
 */
class DirectUrlResolver(
    private val httpClient: HttpClientProvider,
) : MediaResolver {

    override val platform: Platform = Platform.DIRECT_LINK

    private val mediaExtensions = setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp")

    private val whatsappHosts = setOf("mmg.whatsapp.net", "media.whatsapp.net")

    override fun canHandle(url: HttpUrl): Boolean {
        val extension = url.encodedPath.substringAfterLast('.', "").lowercase()
        return extension in mediaExtensions || url.host in whatsappHosts
    }

    override suspend fun resolve(url: HttpUrl): Result<ResolvedMedia> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).head().build()

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

                val contentType = response.header("Content-Type").orEmpty()
                if (contentType.isNotEmpty() && !contentType.startsWith("video/")) {
                    return@withContext Result.failure(ResolveException(DownloadError.NoMediaFound))
                }

                val size = response.header("Content-Length")?.toLongOrNull()
                val fileName = url.pathSegments.lastOrNull().orEmpty().ifBlank { "video.mp4" }

                Result.success(
                    ResolvedMedia(
                        sourceUrl = url.toString(),
                        title = fileName,
                        thumbnailUrl = null,
                        qualities = listOf(
                            VideoQuality(label = "Original", url = url.toString(), sizeBytes = size)
                        ),
                        platform = if (url.host in whatsappHosts) Platform.WHATSAPP else Platform.DIRECT_LINK,
                    )
                )
            }
        } catch (e: IOException) {
            Result.failure(ResolveException(DownloadError.Unexpected(e)))
        }
    }
}
