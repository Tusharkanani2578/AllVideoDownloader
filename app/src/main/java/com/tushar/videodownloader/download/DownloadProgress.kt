package com.tushar.videodownloader.download

import com.tushar.videodownloader.core.DownloadError

sealed interface DownloadProgress {

    data object Preparing : DownloadProgress

    /**
     * [percent] is null when the server sends no Content-Length — the UI then shows an
     * indeterminate bar instead of a fake 0%. HLS supplies [segmentPercent], which is
     * exact where byte totals are unknown.
     */
    data class Running(
        val bytesDownloaded: Long,
        val totalBytes: Long?,
        val bytesPerSecond: Long,
        val segmentPercent: Int? = null,
    ) : DownloadProgress {
        val percent: Int? = segmentPercent
            ?: totalBytes
                ?.takeIf { it > 0 }
                ?.let { ((bytesDownloaded * 100) / it).toInt().coerceIn(0, 100) }
    }

    data class Completed(val fileName: String, val galleryUri: String) : DownloadProgress

    data class Failed(val error: DownloadError) : DownloadProgress
}
