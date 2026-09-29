package com.tushar.videodownloader.download

import com.tushar.videodownloader.resolver.ResolvedMedia
import com.tushar.videodownloader.resolver.VideoQuality
import kotlin.math.absoluteValue

/**
 * Builds gallery file names. Deterministic per (source URL, quality) — that is what
 * makes duplicate detection a MediaStore query instead of a re-download.
 */
internal object FileNaming {

    private const val MAX_TITLE_LENGTH = 40

    private val UNSAFE_CHARS = Regex("""[^A-Za-z0-9 \-_]""")

    private val MEDIA_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp")

    fun buildFileName(media: ResolvedMedia, quality: VideoQuality): String {
        // A direct link's title is its file name; strip its extension so it doesn't
        // survive as junk mid-name ("clipmp4").
        val titleWithoutExtension = media.title
            .substringBeforeLast('.', media.title)
            .takeIf { media.title.substringAfterLast('.', "").lowercase() in MEDIA_EXTENSIONS }
            ?: media.title

        val safeTitle = titleWithoutExtension
            .replace(UNSAFE_CHARS, "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(MAX_TITLE_LENGTH)
            .ifBlank { "video" }

        // Source-URL hash keeps identical titles from different posts apart.
        val sourceHash = media.sourceUrl.hashCode().absoluteValue.toString(36)

        return "${media.platform.name.lowercase()}_${safeTitle}_${quality.label}_$sourceHash" +
            ".${media.kind.fileExtension}"
    }
}
