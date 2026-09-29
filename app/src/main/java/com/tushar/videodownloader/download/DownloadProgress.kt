package com.tushar.videodownloader.download

import com.tushar.videodownloader.core.DownloadError

sealed interface DownloadProgress {

    data object Preparing : DownloadProgress

    /**
     * [percent] is null when the server sends no Content-Length — the UI then shows an
     * indeterminate bar instead of a fake 0%.
     */
    data class Running(
        val bytesDownloaded: Long,
        val totalBytes: Long?,
        val bytesPerSecond: Long,
    ) : DownloadProgress {
        val percent: Int? = totalBytes
            ?.takeIf { it > 0 }
            ?.let { ((bytesDownloaded * 100) / it).toInt().coerceIn(0, 100) }
    }

    data class Completed(val fileName: String, val galleryUri: String) : DownloadProgress

    data class Failed(val error: DownloadError) : DownloadProgress
}
