package com.tushar.videodownloader.status

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.tushar.videodownloader.download.MediaStoreSaver
import com.tushar.videodownloader.resolver.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads the statuses WhatsApp caches on the device, and copies them into the gallery.
 *
 * ### Why this is not the paste-URL flow
 * A WhatsApp status has no URL. Viewing one makes WhatsApp write the file into a hidden
 * folder on the device, and delete it roughly a day later. So the only way to offer them
 * is to read that folder — there is nothing for a user to paste.
 *
 * ### Access
 * The folder starts with a dot, so MediaStore does not index it, and from Android 11 the
 * app cannot reach it by path either. The user grants access once through the system
 * folder picker; that grant is persisted, so this is asked exactly once.
 */
class StatusRepository(
    private val context: Context,
    private val mediaStoreSaver: MediaStoreSaver,
) {

    private companion object {
        const val PREFS = "status_saver"
        const val KEY_TREE_URI = "tree_uri"

        /** Where WhatsApp caches statuses the user has viewed. */
        const val STATUS_PATH =
            "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"

        val VIDEO_EXTENSIONS = setOf("mp4", "3gp", "mkv", "webm")
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True once the user has granted access and the grant still holds. */
    fun hasAccess(): Boolean = grantedTreeUri() != null

    /**
     * Where to open the system folder picker.
     *
     * Deep-linking it to WhatsApp's status folder means the user usually only has to
     * confirm rather than navigate a hidden path themselves.
     */
    fun initialFolderUri(): Uri =
        Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3A" +
                Uri.encode(STATUS_PATH)
        )

    /** Stores the grant so the picker is never shown twice. */
    fun onAccessGranted(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        prefs.edit { putString(KEY_TREE_URI, treeUri.toString()) }
    }

    /**
     * Lists the statuses currently cached, newest first.
     *
     * Returns empty rather than failing when the folder is missing — WhatsApp only
     * creates it once the user has viewed a status, so "empty" is a normal state and the
     * UI says so.
     */
    suspend fun loadStatuses(): List<StatusItem> = withContext(Dispatchers.IO) {
        val treeUri = grantedTreeUri() ?: return@withContext emptyList()
        val folder = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()

        folder.listFiles()
            .filter { it.isFile }
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                val kind = name.substringAfterLast('.', "").lowercase().toMediaKind()
                    ?: return@mapNotNull null

                StatusItem(
                    uri = file.uri,
                    name = name,
                    kind = kind,
                    lastModified = file.lastModified(),
                )
            }
            .sortedByDescending { it.lastModified }
    }

    /**
     * Copies a status into the gallery.
     *
     * The document is streamed through a temp file so the existing MediaStore publisher
     * can be reused unchanged — the same code path a downloaded video takes.
     *
     * The temp file is removed on every path. [MediaStoreSaver.publish] deletes it once
     * the copy lands but deliberately keeps it on failure, so an interrupted download can
     * resume from it. Nothing is resumable here — the source is a local file — so a failed
     * save would otherwise leave a full copy of the status sitting in the cache.
     */
    suspend fun save(item: StatusItem): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "whatsapp_status_${item.name}"

            mediaStoreSaver.findExisting(fileName, item.kind)?.let { existing ->
                return@runCatching existing
            }

            val temp = File(context.cacheDir, "status_${item.name}")
            try {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Could not read ${item.name}")

                mediaStoreSaver.publish(temp, fileName, item.kind)
                fileName
            } finally {
                temp.delete()
            }
        }
    }

    private fun grantedTreeUri(): Uri? {
        val saved = prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse) ?: return null
        // The grant can be revoked from system settings, so held permissions are the
        // authority rather than what was stored.
        val stillHeld = context.contentResolver.persistedUriPermissions
            .any { it.uri == saved && it.isReadPermission }
        return saved.takeIf { stillHeld }
    }

    private fun String.toMediaKind(): MediaKind? = when (this) {
        in VIDEO_EXTENSIONS -> MediaKind.VIDEO
        in IMAGE_EXTENSIONS -> MediaKind.IMAGE
        else -> null
    }
}
