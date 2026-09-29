package com.tushar.videodownloader.resolver

/**
 * One downloadable rendition. For HLS, [url] is the media-playlist URL and
 * [bandwidthBitsPerSecond] enables a size estimate before any segment is fetched.
 */
data class VideoQuality(
    val label: String,
    val url: String,
    val sizeBytes: Long? = null,
    val heightPx: Int = 0,
    val isHls: Boolean = false,
    val bandwidthBitsPerSecond: Long = 0L,
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
    HLS_STREAM("HLS stream"),
}
