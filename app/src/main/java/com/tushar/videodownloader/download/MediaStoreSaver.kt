package com.tushar.videodownloader.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.tushar.videodownloader.resolver.MediaKind
import java.io.File

/**
 * Publishes finished downloads into the gallery through MediaStore, which needs no
 * storage permission for media the app itself creates. This is why `minSdk` is 29:
 * below it the app would need a runtime storage permission and a second write path.
 *
 * Video and images live in separate MediaStore collections and separate public
 * directories, so every operation is routed by [MediaKind].
 */
class MediaStoreSaver(private val context: Context) {

    private companion object {
        const val FOLDER_NAME = "AllVideoDownloader"

        /** Refuse to start unless this much room is left beyond the file itself. */
        const val STORAGE_HEADROOM_BYTES = 50L * 1024 * 1024
    }

    fun availableBytes(): Long {
        val stat = StatFs(context.cacheDir.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    /** A null size means the source declared none; skip the check rather than guess. */
    fun hasRoomFor(requiredBytes: Long?): Boolean {
        if (requiredBytes == null) return true
        return availableBytes() > requiredBytes + STORAGE_HEADROOM_BYTES
    }

    fun findExisting(fileName: String, kind: MediaKind): String? {
        context.contentResolver.query(
            kind.collectionUri(),
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(fileName),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                )
            }
        }
        return null
    }

    fun publish(tempFile: File, fileName: String, kind: MediaKind): Uri {
        // IS_PENDING keeps the half-written file invisible to other apps until done.
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, kind.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${kind.publicDirectory()}/$FOLDER_NAME")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(kind.collectionUri(), values)) {
            "MediaStore refused to create an entry for $fileName"
        }

        try {
            resolver.openOutputStream(uri)?.use { output ->
                tempFile.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Could not open an output stream for $uri")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            // Never leave a pending, unreadable row behind.
            resolver.delete(uri, null, null)
            throw e
        }

        tempFile.delete()
        return uri
    }

    private fun MediaKind.collectionUri(): Uri = when (this) {
        MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        MediaKind.IMAGE, MediaKind.IMAGE_WEBP -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    private fun MediaKind.publicDirectory(): String = when (this) {
        MediaKind.VIDEO -> Environment.DIRECTORY_MOVIES
        MediaKind.IMAGE, MediaKind.IMAGE_WEBP -> Environment.DIRECTORY_PICTURES
    }
}
