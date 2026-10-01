package com.tushar.videodownloader.download

import com.tushar.videodownloader.core.DownloadError
import com.tushar.videodownloader.resolver.MediaKind

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
        /** Position in the batch, 1-based; both 1 when a single item is downloading. */
        val itemNumber: Int = 1,
        val itemCount: Int = 1,
    ) : DownloadProgress {
        val percent: Int? = totalBytes
            ?.takeIf { it > 0 }
            ?.let { ((bytesDownloaded * 100) / it).toInt().coerceIn(0, 100) }
    }

    /**
      * @param kind carried through so Open and Share can type their intent correctly.
      * @param savedCount how many items landed. The name and URI are the last of them,
      *   so Open and Share still have something concrete to act on after saving a batch.
      */
    data class Completed(
        val fileName: String,
        val galleryUri: String,
        val kind: MediaKind,
        val savedCount: Int = 1,
    ) : DownloadProgress

    data class Failed(val error: DownloadError) : DownloadProgress
}
