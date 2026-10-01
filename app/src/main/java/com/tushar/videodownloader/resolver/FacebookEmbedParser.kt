package com.tushar.videodownloader.resolver

/**
 * Reads media fields from Facebook's `plugins/video.php` embed page, which exposes
 * `hd_src` and `sd_src` for public videos. Values arrive escaped inside a script
 * string, so they need unescaping before use.
 *
 * Undocumented structure — a missing value means "not publicly downloadable", never
 * an error in itself.
 */
internal object FacebookEmbedParser {

    private val HD_SRC = Regex("""hd_src\\*"\s*:\s*\\*"(https[^"]+)""")
    private val SD_SRC = Regex("""sd_src\\*"\s*:\s*\\*"(https[^"]+)""")
    private val THUMBNAIL = Regex("""preferred_thumbnail\\*".{0,200}?uri\\*"\s*:\s*\\*"(https[^"]+)""")

    private val UNICODE_ESCAPE = Regex("""\\u([0-9a-fA-F]{4})""")

    /**
     * Renditions the embed advertises, best first. Facebook labels them by tier rather
     * than resolution, so the heights are nominal — enough to order the chips and pick
     * a sensible default.
     */
    fun findQualities(embedHtml: String): List<MediaOption> = buildList {
        HD_SRC.find(embedHtml)?.groupValues?.get(1)?.let {
            add(MediaOption(label = "HD", url = unescape(it), heightPx = 720))
        }
        SD_SRC.find(embedHtml)?.groupValues?.get(1)?.let {
            add(MediaOption(label = "SD", url = unescape(it), heightPx = 360))
        }
    }

    fun findThumbnailUrl(embedHtml: String): String? =
        THUMBNAIL.find(embedHtml)?.groupValues?.get(1)?.let(::unescape)

    // Order matters: collapse double backslashes, then \/, then unicode escapes —
    // Facebook encodes the & between URL params that way, and decoding it wrong
    // breaks the CDN signature.
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
