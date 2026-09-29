package com.tushar.videodownloader.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import java.io.File

/**
 * Publishes finished downloads into the gallery. Android 10+ writes through
 * MediaStore (no storage permission needed for app-created media); Android 9 and
 * below falls back to a direct write, which is why the manifest declares
 * WRITE_EXTERNAL_STORAGE with maxSdkVersion 28.
 */
class MediaStoreSaver(private val context: Context) {

    private companion object {
        val RELATIVE_PATH = "${Environment.DIRECTORY_MOVIES}/AllVideoDownloader"

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

    fun findExisting(fileName: String): String? {
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media.DISPLAY_NAME),
            "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
            arrayOf(fileName),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME))
            }
        }
        return null
    }

    fun publish(tempFile: File, fileName: String, mimeType: String = "video/mp4"): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishScoped(tempFile, fileName, mimeType)
        } else {
            publishLegacy(tempFile, fileName)
        }
    }

    private fun publishScoped(tempFile: File, fileName: String, mimeType: String): Uri {
        // IS_PENDING keeps the half-written file invisible to other apps until done.
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = requireNotNull(
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ) { "MediaStore refused to create an entry for $fileName" }

        try {
            resolver.openOutputStream(uri)?.use { output ->
                tempFile.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Could not open an output stream for $uri")

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            // Never leave a pending, unreadable row behind.
            resolver.delete(uri, null, null)
            throw e
        }

        tempFile.delete()
        return uri
    }

    private fun publishLegacy(tempFile: File, fileName: String): Uri {
        val moviesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "AllVideoDownloader",
        ).apply { mkdirs() }

        val target = File(moviesDir, fileName)
        tempFile.copyTo(target, overwrite = true)
        tempFile.delete()

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATA, target.absolutePath)
        }
        return context.contentResolver
            .insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: Uri.fromFile(target)
    }
}
