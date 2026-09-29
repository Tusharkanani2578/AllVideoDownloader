package com.tushar.videodownloader.download

import com.tushar.videodownloader.core.DownloadError
import com.tushar.videodownloader.network.HttpClientProvider
import com.tushar.videodownloader.network.NetworkMonitor
import com.tushar.videodownloader.resolver.ResolvedMedia
import com.tushar.videodownloader.resolver.VideoQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * Streams a video to disk and publishes it to the gallery.
 *
 * The transfer is one ranged request, which is what makes resuming simple: the partial
 * file left on disk is itself the resume point.
 */
class Downloader(
    private val httpClient: HttpClientProvider,
    private val networkMonitor: NetworkMonitor,
    private val mediaStoreSaver: MediaStoreSaver,
    private val tempDir: File,
) {

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 200L
    }

    /**
     * Emits progress until completion or failure. Cancelling the collecting scope
     * stops the transfer; the partial file is kept so a later attempt can resume.
     */
    fun download(media: ResolvedMedia, quality: VideoQuality): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Preparing)

        if (!networkMonitor.isOnline()) {
            emit(DownloadProgress.Failed(DownloadError.NoNetwork))
            return@flow
        }

        try {
            transfer(media, quality)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            emit(DownloadProgress.Failed(DownloadError.Timeout))
        } catch (e: InterruptedIOException) {
            emit(DownloadProgress.Failed(DownloadError.Interrupted(0)))
        } catch (e: IOException) {
            emit(DownloadProgress.Failed(e.toDownloadError()))
        } catch (e: Exception) {
            emit(DownloadProgress.Failed(DownloadError.Unexpected(e)))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<DownloadProgress>.transfer(
        media: ResolvedMedia,
        quality: VideoQuality,
    ) {
        val fileName = FileNaming.buildFileName(media, quality)
        if (rejectIfAlreadySaved(fileName)) return
        if (rejectIfNoRoom(quality.sizeBytes)) return

        val tempFile = File(tempDir, "$fileName.part")
        val alreadyHave = if (tempFile.exists()) tempFile.length() else 0L

        val request = Request.Builder()
            .url(quality.url)
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .apply { if (alreadyHave > 0) header("Range", "bytes=$alreadyHave-") }
            .get()
            .build()

        httpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                emit(DownloadProgress.Failed(DownloadError.ServerError(response.code)))
                return
            }

            // 206 = server honoured the Range header; 200 = it resent from byte zero,
            // so the partial must be discarded.
            val isResuming = response.code == 206 && alreadyHave > 0
            val startingFrom = if (isResuming) alreadyHave else 0L

            val body = response.body
            if (body == null) {
                emit(DownloadProgress.Failed(DownloadError.NoMediaFound))
                return
            }

            val totalBytes = body.contentLength().takeIf { it > 0 }?.plus(startingFrom)

            // Re-check storage now the real size is known.
            if (totalBytes != null && rejectIfNoRoom(totalBytes - startingFrom)) return

            val written = body.byteStream().use { input ->
                FileOutputStream(tempFile, isResuming).use { output ->
                    copyWithProgress(input, output, startingFrom, totalBytes)
                }
            }

            // Truncated body = connection dropped; keep the partial for resume.
            if (totalBytes != null && written < totalBytes) {
                emit(DownloadProgress.Failed(DownloadError.Interrupted(written)))
                return
            }

            val uri = mediaStoreSaver.publish(tempFile, fileName)
            emit(DownloadProgress.Completed(fileName, uri.toString()))
        }
    }

    private suspend fun FlowCollector<DownloadProgress>.rejectIfAlreadySaved(
        fileName: String,
    ): Boolean {
        val existing = mediaStoreSaver.findExisting(fileName) ?: return false
        emit(DownloadProgress.Failed(DownloadError.AlreadyDownloaded(existing)))
        return true
    }

    private suspend fun FlowCollector<DownloadProgress>.rejectIfNoRoom(
        requiredBytes: Long?,
    ): Boolean {
        if (mediaStoreSaver.hasRoomFor(requiredBytes)) return false
        emit(
            DownloadProgress.Failed(
                DownloadError.InsufficientStorage(
                    requiredBytes = requiredBytes ?: 0L,
                    availableBytes = mediaStoreSaver.availableBytes(),
                )
            )
        )
        return true
    }

    private suspend fun FlowCollector<DownloadProgress>.copyWithProgress(
        input: InputStream,
        output: FileOutputStream,
        startingFrom: Long,
        totalBytes: Long?,
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var written = startingFrom
        var lastEmitAt = 0L
        var lastEmitBytes = startingFrom

        while (true) {
            currentCoroutineContext().ensureActive()

            val read = input.read(buffer)
            if (read == -1) break

            output.write(buffer, 0, read)
            written += read

            val now = System.currentTimeMillis()
            if (now - lastEmitAt >= PROGRESS_INTERVAL_MS) {
                val elapsed = (now - lastEmitAt).coerceAtLeast(1L)
                emit(
                    DownloadProgress.Running(
                        bytesDownloaded = written,
                        totalBytes = totalBytes,
                        bytesPerSecond = if (lastEmitAt == 0L) 0 else ((written - lastEmitBytes) * 1000) / elapsed,
                    )
                )
                lastEmitAt = now
                lastEmitBytes = written
            }
        }
        output.flush()
        return written
    }

    /** Distinguishes a disk that filled mid-write from a generic connection drop. */
    private fun IOException.toDownloadError(): DownloadError =
        if (message?.contains("ENOSPC", ignoreCase = true) == true) {
            DownloadError.InsufficientStorage(0L, mediaStoreSaver.availableBytes())
        } else {
            DownloadError.Interrupted(0)
        }

    fun discardPartial(media: ResolvedMedia, quality: VideoQuality) {
        File(tempDir, "${FileNaming.buildFileName(media, quality)}.part").delete()
    }
}
