package com.tushar.videodownloader.resolver

/**
 * One downloadable rendition.
 *
 * @param sizeBytes null when the source advertises no length, which disables the
 *   pre-flight storage check for this item rather than guessing at one.
 * @param heightPx vertical resolution, used to order renditions and pick a default.
 */
data class VideoQuality(
    val label: String,
    val url: String,
    val sizeBytes: Long? = null,
    val heightPx: Int = 0,
)

/**
 * What a resolved link turned out to be. Drives the container, MIME type and which
 * MediaStore collection the download is published into.
 */
enum class MediaKind(val mimeType: String, val fileExtension: String) {
    VIDEO("video/mp4", "mp4"),
    IMAGE("image/jpeg", "jpg"),
}

data class ResolvedMedia(
    val sourceUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val qualities: List<VideoQuality>,
    val platform: Platform,
    val kind: MediaKind = MediaKind.VIDEO,
) {
    val bestQuality: VideoQuality get() = qualities.maxBy { it.heightPx }
}

enum class Platform(val displayName: String) {
    INSTAGRAM("Instagram"),
    FACEBOOK("Facebook"),
    WHATSAPP("WhatsApp"),
    DIRECT_LINK("Direct link"),
}
