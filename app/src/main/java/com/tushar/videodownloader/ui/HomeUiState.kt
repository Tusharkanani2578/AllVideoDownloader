package com.tushar.videodownloader.ui

import com.tushar.videodownloader.core.DownloadError
import com.tushar.videodownloader.download.DownloadProgress
import com.tushar.videodownloader.resolver.MediaPreview
import com.tushar.videodownloader.resolver.ResolvedMedia
import com.tushar.videodownloader.resolver.MediaOption

/**
 * Everything the home screen renders, in one immutable snapshot — so the UI can never
 * show two contradictory things at once.
 */
data class HomeUiState(
    val urlInput: String = "",
    val stage: Stage = Stage.Idle,
    val media: ResolvedMedia? = null,
    /** Metadata for a link that resolved but has no downloadable video. */
    val preview: MediaPreview? = null,
    val selectedOption: MediaOption? = null,
    val download: DownloadProgress? = null,
    val error: DownloadError? = null,
    val clipboardSuggestion: String? = null,
) {

    enum class Stage { Idle, Fetching, Resolved, Downloading, Done }

    val canFetch: Boolean get() = urlInput.isNotBlank() && stage != Stage.Fetching

    val isDownloading: Boolean get() = stage == Stage.Downloading

    /**
     * Retry is offered only where a second attempt could succeed. A private video or
     * unsupported host never will, so no button is drawn.
     */
    val isRetryable: Boolean
        get() = when (error) {
            is DownloadError.Interrupted,
            is DownloadError.NoNetwork,
            is DownloadError.Timeout,
            is DownloadError.ServerError,
            is DownloadError.Unexpected,
            -> true

            else -> false
        }
}
