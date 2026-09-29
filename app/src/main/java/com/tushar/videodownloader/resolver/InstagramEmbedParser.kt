package com.tushar.videodownloader.resolver

/**
 * Reads media fields from Instagram's public `/embed/` page, whose inline JSON carries
 * `video_url` for public video posts. Undocumented structure — a missing value means
 * "not publicly downloadable", never an error in itself.
 */
internal object InstagramEmbedParser {

    // Values sit in JSON escaped one or two levels deep; a URL runs to the first raw quote.
    private val VIDEO_URL = Regex("""video_url\\*"\s*:\s*\\*"(https[^"]+)""")
    private val DISPLAY_URL = Regex("""display_url\\*"\s*:\s*\\*"(https[^"]+)""")
    private val USERNAME = Regex("""username\\*"\s*:\s*\\*"([A-Za-z0-9._]{1,60})""")

    private val UNICODE_ESCAPE = Regex("""\\u([0-9a-fA-F]{4})""")

    /** CDN path segment Instagram uses for post images, as opposed to avatars (`-19`). */
    private const val POST_IMAGE_PATH = "t51.82787-15"

    fun findVideoUrl(embedHtml: String): String? =
        VIDEO_URL.find(embedHtml)?.groupValues?.get(1)?.let(::unescape)

    /**
     * True when the embed describes a photo post rather than a video one.
     *
     * A photo has no video to withhold, so reporting it as "requires sign-in" would be
     * wrong. Instagram types image media as `GraphImage` (`XDTGraphImage` on newer
     * responses) and video as `GraphVideo`.
     */
    fun isPhotoPost(embedHtml: String): Boolean =
        embedHtml.contains("GraphImage") && !embedHtml.contains("GraphVideo")

    fun findThumbnailUrl(embedHtml: String): String? =
        DISPLAY_URL.find(embedHtml)?.groupValues?.get(1)?.let(::unescape)

    /**
     * Full-resolution post image from the captioned embed.
     *
     * The `og:image` on the post page is a 640×640 square crop — a preview, not the
     * photo. The captioned embed references the same file under `t51.*-15` with an
     * `stp` transform that applies no crop or size cap, which is the image as posted.
     * The whole signed URL is taken verbatim: its `oh`/`oe` signature covers every
     * parameter, so editing any of them returns 403.
     */
    fun findFullImageUrl(captionedEmbedHtml: String): String? =
        captionedEmbedHtml.split('"')
            .firstOrNull { it.contains(POST_IMAGE_PATH) && it.startsWith("http") }
            ?.replace("&amp;", "&")

    fun findUsername(embedHtml: String): String? =
        USERNAME.find(embedHtml)?.groupValues?.get(1)

    // Order matters: collapse double backslashes, then \/, then unicode escapes —
    // Instagram encodes the & between URL params that way; decoding wrong breaks
    // the CDN signature.
    private fun unescape(raw: String): String {
        var value = raw
        while (value.contains("\\\\")) value = value.replace("\\\\", "\\")
        value = value.replace("\\/", "/")
        value = UNICODE_ESCAPE.replace(value) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }
        return value.trimEnd('\\')
    }
}
