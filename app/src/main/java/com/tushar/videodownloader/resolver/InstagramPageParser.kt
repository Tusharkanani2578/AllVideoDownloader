package com.tushar.videodownloader.resolver

/**
 * Finds a media URL in an Instagram post page.
 *
 * Instagram does not use one field name for the video. Which one appears depends on the
 * page variant it decides to serve — the rendition list, a single playback URL, the
 * browser-native pair, or just the Open Graph tag. Looking for only one of them is why a
 * reel can be public and still fail to resolve, so every known shape is tried in turn.
 *
 * Ordered widest-first: `video_versions` carries the rendition list, so it survives page
 * changes that drop the single-URL fields, and `og:video` is the last resort because it
 * is the one Instagram most often omits.
 */
internal object InstagramPageParser {

    /** Every field the page is known to carry the video under, in preference order. */
    private val VIDEO_PATTERNS = listOf(
        Regex(""""video_versions":\[\{.*?"url":"(.*?)""""),
        Regex(""""video_url":"(.*?)""""),
        Regex(""""playable_url_quality_hd":"(.*?)""""),
        Regex(""""playable_url":"(.*?)""""),
        Regex(""""browser_native_hd_url":"(.*?)""""),
        Regex(""""browser_native_sd_url":"(.*?)""""),
        Regex("""<meta property="og:video(?::secure_url|:url)?" content="(.*?)""""),
    )

    private val IMAGE_PATTERNS = listOf(
        Regex(""""display_url":"(.*?)""""),
        Regex("""<meta property="og:image" content="(.*?)""""),
    )

    private val USERNAME = Regex(""""username":"([A-Za-z0-9._]{1,60})"""")

    private val UNICODE_ESCAPE = Regex("""\\u([0-9a-fA-F]{4})""")

    fun findVideoUrl(html: String): String? = firstMatch(html, VIDEO_PATTERNS)

    fun findImageUrl(html: String): String? = firstMatch(html, IMAGE_PATTERNS)

    fun findUsername(html: String): String? =
        USERNAME.find(html)?.groupValues?.get(1)

    private fun firstMatch(html: String, patterns: List<Regex>): String? =
        patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(html)?.groupValues?.get(1)?.let(::unescape)?.takeIf { it.startsWith("http") }
        }

    /**
     * Undoes the escaping around a URL taken off the page.
     *
     * Two different escapings are undone because the same URL can come from either of
     * two places: an inline JSON field escapes it one way, a meta tag the other. Order
     * matters within the JSON part — double backslashes collapse first so `\\\/` reduces
     * cleanly to `/`, then `\uXXXX` decodes, which is how Instagram writes the `&`
     * between CDN parameters.
     *
     * The HTML entities are undone last, for the meta-tag case: a tag writes that same
     * separator as `&amp;`, and passing it through verbatim invalidates the URL's
     * signature. That is why a reel's thumbnail came back `403` while its video played
     * — the video came from JSON, the thumbnail from a tag.
     */
    private fun unescape(raw: String): String {
        var value = raw
        while (value.contains("""\\""")) value = value.replace("""\\""", """\""")
        value = value.replace("""\/""", "/")
        value = UNICODE_ESCAPE.replace(value) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }
        return value.trimEnd('\\').unescapeHtmlEntities()
    }
}
