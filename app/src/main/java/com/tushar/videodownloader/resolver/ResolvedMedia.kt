package com.tushar.videodownloader.resolver

/**
 * One thing the user can choose to download — see [OptionKind], since the choice is not
 * always between qualities.
 *
 * @param sizeBytes null when the source advertises no length, which disables the
 *   pre-flight storage check for this item rather than guessing at one.
 * @param heightPx vertical resolution, used to order renditions and pick a default.
 * @param kind what this option is, where it differs from the post's own kind. A carousel
 *   can hold photos and videos side by side and each is saved as what it is; null means
 *   the option is the same kind as the post.
 * @param thumbnailUrl a picture of this option, where it has one of its own. Renditions
 *   of one video share the post's thumbnail and leave this null; a carousel's slides are
 *   different pictures, and showing them is the only way to pick one on sight.
 */
data class MediaOption(
    val label: String,
    val url: String,
    val sizeBytes: Long? = null,
    val heightPx: Int = 0,
    val kind: MediaKind? = null,
    val thumbnailUrl: String? = null,
)

/**
 * What a resolved link turned out to be. Drives the container, MIME type and which
 * MediaStore collection the download is published into.
 *
 * WebP is its own kind because Instagram serves a carousel's full-resolution images in
 * that format and will not re-encode them — its CDN URLs are signed over every
 * parameter — so saving one as WebP is honest where naming it `.jpg` would not be.
 */
enum class MediaKind(val mimeType: String, val fileExtension: String) {
    VIDEO("video/mp4", "mp4"),
    IMAGE("image/jpeg", "jpg"),
    IMAGE_WEBP("image/webp", "webp");

    val isVideo: Boolean get() = this == VIDEO
}

/** What the entries in [ResolvedMedia.options] offer a choice between. */
enum class OptionKind {
    /** Renditions of one piece of media — Facebook's HD and SD of the same video. */
    RENDITION,

    /** Separate items inside one post — the slides of an Instagram carousel. */
    ITEM,
}

data class ResolvedMedia(
    val sourceUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val options: List<MediaOption>,
    val platform: Platform,
    val kind: MediaKind = MediaKind.VIDEO,
    val optionKind: OptionKind = OptionKind.RENDITION,
    /**
     * Which option to open on, where the link itself says. A carousel share link carries
     * `img_index` naming the slide the sender was looking at, and opening on any other
     * one would ignore what they actually shared.
     */
    val preferredIndex: Int? = null,
) {
    /** The highest rendition, or the item the link pointed at. */
    val defaultOption: MediaOption
        get() = preferredIndex?.let(options::getOrNull) ?: options.maxBy { it.heightPx }

    /** What [option] should be saved as — its own kind wherever it has one. */
    fun kindOf(option: MediaOption): MediaKind = option.kind ?: kind
}

enum class Platform(val displayName: String) {
    INSTAGRAM("Instagram"),
    FACEBOOK("Facebook"),
    WHATSAPP("WhatsApp"),
    DIRECT_LINK("Direct link"),
}
