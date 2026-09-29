package com.tushar.videodownloader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.tushar.videodownloader.resolver.MediaKind

/**
 * Opens or shares a saved download through the system.
 *
 * A download is only finished from the user's point of view once they can see it, so the
 * completion bar hands off to the gallery rather than ending at a file name. The intent
 * is typed from the [MediaKind] that was downloaded — a photo saved to
 * `MediaStore.Images` must not be handed to a viewer as a video MIME type.
 *
 * Both calls are no-ops when nothing on the device can handle the intent, which is
 * preferable to crashing on a ROM with no gallery app.
 */
internal object MediaActions {

    fun open(context: Context, galleryUri: String, kind: MediaKind) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(galleryUri.toUri(), kind.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startSafely(intent)
    }

    fun share(context: Context, galleryUri: String, kind: MediaKind) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = kind.mimeType
            putExtra(Intent.EXTRA_STREAM, galleryUri.toUri())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startSafely(Intent.createChooser(intent, null))
    }

    private fun Context.startSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Nothing on this device can handle it; the file is still saved.
        }
    }
}
