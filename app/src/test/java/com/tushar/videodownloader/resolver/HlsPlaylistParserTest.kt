package com.tushar.videodownloader.resolver

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistParserTest {

    private val master = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.42c01e"
        360/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720,CODECS="avc1.4d401f"
        720/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080
        https://cdn.example.com/1080/index.m3u8
    """.trimIndent()

    private val media = """
        #EXTM3U
        #EXT-X-TARGETDURATION:10
        #EXTINF:9.009,
        seg0.ts
        #EXTINF:9.009,
        seg1.ts
        #EXTINF:3.003,
        seg2.ts
        #EXT-X-ENDLIST
    """.trimIndent()

    @Test
    fun `recognises a master playlist`() {
        assertTrue(HlsPlaylistParser.isMasterPlaylist(master))
        assertFalse(HlsPlaylistParser.isMasterPlaylist(media))
    }

    @Test
    fun `recognises any playlist by its header`() {
        assertTrue(HlsPlaylistParser.isPlaylist(media))
        assertFalse(HlsPlaylistParser.isPlaylist("<html><body>not a playlist</body></html>"))
    }

    @Test
    fun `parses every variant with its resolution and bandwidth`() {
        val variants = HlsPlaylistParser.parseMaster(master)

        assertEquals(3, variants.size)
        assertEquals(360, variants[0].heightPx)
        assertEquals(800000L, variants[0].bandwidthBitsPerSecond)
        assertEquals("720/index.m3u8", variants[1].uri)
        assertEquals(1080, variants[2].heightPx)
    }

    @Test
    fun `pairs a variant with the uri on the following line`() {
        val variants = HlsPlaylistParser.parseMaster(master)

        assertEquals("360/index.m3u8", variants[0].uri)
        assertEquals("https://cdn.example.com/1080/index.m3u8", variants[2].uri)
    }

    @Test
    fun `parses segments and total duration`() {
        val playlist = HlsPlaylistParser.parseMedia(media)

        assertEquals(listOf("seg0.ts", "seg1.ts", "seg2.ts"), playlist.segmentUris)
        assertEquals(21.021, playlist.totalDurationSeconds, 0.001)
    }

    @Test
    fun `treats a playlist without an init segment as mpeg-ts`() {
        assertFalse(HlsPlaylistParser.parseMedia(media).isFragmentedMp4)
        assertNull(HlsPlaylistParser.parseMedia(media).initSegmentUri)
    }

    @Test
    fun `detects a fragmented mp4 playlist by its map tag`() {
        val fmp4 = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:4.0,
            seg0.m4s
        """.trimIndent()

        val playlist = HlsPlaylistParser.parseMedia(fmp4)

        assertTrue(playlist.isFragmentedMp4)
        assertEquals("init.mp4", playlist.initSegmentUri)
        assertEquals(listOf("seg0.m4s"), playlist.segmentUris)
    }

    @Test
    fun `resolves a relative segment against the playlist url`() {
        val base = "https://cdn.example.com/video/720/index.m3u8".toHttpUrl()

        assertEquals(
            "https://cdn.example.com/video/720/seg0.ts",
            HlsPlaylistParser.resolveUri(base, "seg0.ts").toString(),
        )
    }

    @Test
    fun `keeps an absolute segment url unchanged`() {
        val base = "https://cdn.example.com/video/index.m3u8".toHttpUrl()

        assertEquals(
            "https://other.example.com/a.ts",
            HlsPlaylistParser.resolveUri(base, "https://other.example.com/a.ts").toString(),
        )
    }

    @Test
    fun `flags an encrypted playlist`() {
        val encrypted = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
            #EXTINF:4.0,
            seg0.ts
        """.trimIndent()

        assertTrue(HlsPlaylistParser.parseMedia(encrypted).isEncrypted)
    }

    @Test
    fun `does not flag a playlist whose key method is none`() {
        val clear = """
            #EXTM3U
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:4.0,
            seg0.ts
        """.trimIndent()

        assertFalse(HlsPlaylistParser.parseMedia(clear).isEncrypted)
    }

    @Test
    fun `treats a plain playlist as unencrypted`() {
        assertFalse(HlsPlaylistParser.parseMedia(media).isEncrypted)
    }

    @Test
    fun `returns no variants for a playlist that has none`() {
        assertTrue(HlsPlaylistParser.parseMaster(media).isEmpty())
    }
}
