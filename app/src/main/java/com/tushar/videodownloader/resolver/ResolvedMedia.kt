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

data class ResolvedMedia(
    val sourceUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val qualities: List<VideoQuality>,
    val platform: Platform,
) {
    val bestQuality: VideoQuality get() = qualities.maxBy { it.heightPx }
}

enum class Platform(val displayName: String) {
    INSTAGRAM("Instagram"),
    FACEBOOK("Facebook"),
    WHATSAPP("WhatsApp"),
    DIRECT_LINK("Direct link"),
}
