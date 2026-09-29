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

    fun findVideoUrl(embedHtml: String): String? =
        VIDEO_URL.find(embedHtml)?.groupValues?.get(1)?.let(::unescape)

    fun findThumbnailUrl(embedHtml: String): String? =
        DISPLAY_URL.find(embedHtml)?.groupValues?.get(1)?.let(::unescape)

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
