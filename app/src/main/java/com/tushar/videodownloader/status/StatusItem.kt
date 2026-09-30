package com.tushar.videodownloader.status

import android.net.Uri
import com.tushar.videodownloader.resolver.MediaKind

/**
 * One status WhatsApp has cached on the device.
 *
 * @param uri SAF document URI. Readable only while the folder permission granted by the
 *   user is still held, which is why it is never persisted anywhere.
 * @param name the file's own name, used to keep the saved copy identifiable.
 * @param kind decides the gallery collection the copy is published into.
 * @param lastModified newest first, so what the user just watched is at the top.
 */
data class StatusItem(
    val uri: Uri,
    val name: String,
    val kind: MediaKind,
    val lastModified: Long,
)
