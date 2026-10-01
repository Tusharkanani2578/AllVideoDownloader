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

    private const val CAROUSEL_KEY = """"carousel_media":["""

    /** The first candidate is the largest; Instagram orders them descending. */
    private val CANDIDATE_URL = Regex(""""image_versions2":\{"candidates":\[\{"url":"(.*?)"""")

    private val DISPLAY_URI = Regex(""""display_uri":"(.*?)"""")

    private val ORIGINAL_HEIGHT = Regex(""""original_height":(\d+)""")

    fun findVideoUrl(html: String): String? = firstMatch(html, VIDEO_PATTERNS)

    fun findImageUrl(html: String): String? = firstMatch(html, IMAGE_PATTERNS)

    fun findUsername(html: String): String? =
        USERNAME.find(html)?.groupValues?.get(1)

    /**
     * One entry per slide of a carousel post, in the order they appear.
     *
     * A carousel keeps its media inside `carousel_media` and leaves the top of the page
     * with only an `og:image` cover, which is why looking only at the top resolves
     * nothing for these posts.
     *
     * Images come from `image_versions2`, which carries the full resolution — the
     * `display_uri` beside it is a 640px crop. Those are WebP, hence [MediaKind.IMAGE_WEBP];
     * the CDN will not serve them as anything else, since its URLs are signed over every
     * parameter.
     */
    fun findCarouselItems(html: String): List<MediaOption> =
        carouselObjects(html).mapIndexedNotNull { index, item ->
            val isVideo = item.contains(""""__typename":"XIGPolarisVideoMedia"""") ||
                item.contains(""""video_versions"""")

            val url = if (isVideo) {
                firstMatch(item, VIDEO_PATTERNS)
            } else {
                match(item, CANDIDATE_URL) ?: match(item, DISPLAY_URI)
            } ?: return@mapIndexedNotNull null

            MediaOption(
                label = "${index + 1}",
                url = url,
                heightPx = ORIGINAL_HEIGHT.find(item)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                kind = if (isVideo) MediaKind.VIDEO else MediaKind.IMAGE_WEBP,
                // The 640px crop, which is the right size for a tile and saves pulling
                // the full-resolution image just to draw one.
                thumbnailUrl = match(item, DISPLAY_URI) ?: match(item, CANDIDATE_URL),
            )
        }

    /**
     * Splits `carousel_media` into its items.
     *
     * Brace-matched rather than pattern-matched, because each item nests objects of its
     * own and a regex would either stop at the first inner brace or run past the end of
     * the item into the next one.
     */
    private fun carouselObjects(html: String): List<String> {
        val start = html.indexOf(CAROUSEL_KEY)
        if (start < 0) return emptyList()

        val items = mutableListOf<String>()
        var depth = 0
        var itemStart = -1
        var i = start + CAROUSEL_KEY.length

        while (i < html.length) {
            when (html[i]) {
                '{' -> {
                    if (depth == 0) itemStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && itemStart >= 0) items += html.substring(itemStart, i + 1)
                }
                ']' -> if (depth == 0) return items
            }
            i++
        }
        return items
    }

    private fun match(text: String, pattern: Regex): String? =
        pattern.find(text)?.groupValues?.get(1)?.let(::unescape)?.takeIf { it.startsWith("http") }

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
