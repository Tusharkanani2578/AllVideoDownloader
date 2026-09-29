package com.tushar.videodownloader.resolver

import com.tushar.videodownloader.core.DownloadError
import okhttp3.HttpUrl

/**
 * Strategy for turning a share link into downloadable media. Adding a platform means
 * one implementation registered in ServiceLocator — no existing code changes.
 *
 * Implementations must not authenticate or reach content the platform does not serve
 * publicly; gated links resolve to [DownloadError.AuthenticationRequired].
 */
interface MediaResolver {

    val platform: Platform

    fun canHandle(url: HttpUrl): Boolean

    suspend fun resolve(url: HttpUrl): Result<ResolvedMedia>
}

/**
 * Typed failure, optionally carrying whatever preview data the resolver managed to
 * read before failing — a valid link can still be undownloadable.
 */
class ResolveException(
    val error: DownloadError,
    val preview: MediaPreview? = null,
) : Exception(error.userMessage)

data class MediaPreview(
    val title: String,
    val thumbnailUrl: String?,
    val platform: Platform,
)
