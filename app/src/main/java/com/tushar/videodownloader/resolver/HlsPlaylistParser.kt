package com.tushar.videodownloader.resolver

import okhttp3.HttpUrl

/**
 * Reads the two HLS playlist forms the app needs: a *master* playlist advertising
 * renditions (the real source of the quality list) and a *media* playlist listing one
 * rendition's segments. Encryption, alternate audio and subtitles are out of scope.
 */
internal object HlsPlaylistParser {

    data class Variant(
        val uri: String,
        val bandwidthBitsPerSecond: Long,
        val widthPx: Int,
        val heightPx: Int,
    )

    data class MediaPlaylist(
        val initSegmentUri: String?,
        val segmentUris: List<String>,
        val totalDurationSeconds: Double,
        val isEncrypted: Boolean,
    ) {
        /** An EXT-X-MAP init segment only appears for fragmented MP4 (vs MPEG-TS). */
        val isFragmentedMp4: Boolean get() = initSegmentUri != null
    }

    private val STREAM_INF = Regex("""#EXT-X-STREAM-INF:(.*)""")
    private val ATTRIBUTE = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""")
    private val EXTINF = Regex("""#EXTINF:([0-9.]+)""")
    private val MAP_URI = Regex("""#EXT-X-MAP:.*URI="([^"]+)"""")

    fun isMasterPlaylist(content: String): Boolean = content.contains("#EXT-X-STREAM-INF")

    fun isPlaylist(content: String): Boolean = content.trimStart().startsWith("#EXTM3U")

    /** Each STREAM-INF tag is paired with the variant URI on the next non-comment line. */
    fun parseMaster(content: String): List<Variant> {
        val lines = content.lines()
        val variants = mutableListOf<Variant>()

        lines.forEachIndexed { index, line ->
            val match = STREAM_INF.matchEntire(line.trim()) ?: return@forEachIndexed

            val attributes = ATTRIBUTE.findAll(match.groupValues[1])
                .associate { it.groupValues[1] to it.groupValues[2].trim('"') }

            val uri = lines.drop(index + 1)
                .firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("#") }
                ?.trim()
                ?: return@forEachIndexed

            val (width, height) = attributes["RESOLUTION"]
                ?.split("x")
                ?.let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
                ?: (0 to 0)

            variants += Variant(
                uri = uri,
                bandwidthBitsPerSecond = attributes["BANDWIDTH"]?.toLongOrNull() ?: 0L,
                widthPx = width,
                heightPx = height,
            )
        }
        return variants
    }

    fun parseMedia(content: String): MediaPlaylist {
        val lines = content.lines().map { it.trim() }

        val segments = lines.filter { it.isNotBlank() && !it.startsWith("#") }

        val duration = lines.sumOf { line ->
            EXTINF.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        }

        val initSegment = lines.firstNotNullOfOrNull { MAP_URI.find(it)?.groupValues?.get(1) }

        // METHOD=NONE explicitly means unencrypted from that point on.
        val encrypted = lines.any { it.startsWith("#EXT-X-KEY") && !it.contains("METHOD=NONE") }

        return MediaPlaylist(
            initSegmentUri = initSegment,
            segmentUris = segments,
            totalDurationSeconds = duration,
            isEncrypted = encrypted,
        )
    }

    /** HLS playlists routinely use relative segment paths; rebase against the playlist URL. */
    fun resolveUri(base: HttpUrl, reference: String): HttpUrl? = base.resolve(reference)
}
