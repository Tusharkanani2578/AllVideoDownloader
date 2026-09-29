package com.tushar.videodownloader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Opens or shares a saved video. A download is only finished from the user's point of
 * view once they can watch it, so the completion card hands off to the gallery.
 */
internal object MediaActions {

    fun openInGallery(context: Context, galleryUri: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(galleryUri.toUri(), "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startSafely(intent)
    }

    fun share(context: Context, galleryUri: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
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
