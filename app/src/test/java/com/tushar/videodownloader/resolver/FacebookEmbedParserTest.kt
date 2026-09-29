package com.tushar.videodownloader.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookEmbedParserTest {

    // Mirrors the real escaping in plugins/video.php output.
    private val embedHtml = """
        {\"preferred_thumbnail\":{\"image\":{\"uri\":\"https:\\\/\\\/scontent.fna.fbcdn.net\\\/thumb.jpg\"}},
        \"hd_src\":\"https:\\\/\\\/video.fstv4-1.fna.fbcdn.net\\\/o1\\\/v\\\/t2\\\/hd.mp4?a=1\\u0026b=2\",
        \"sd_src\":\"https:\\\/\\\/video.fstv4-1.fna.fbcdn.net\\\/o1\\\/v\\\/t2\\\/sd.mp4?a=1\"}
    """.trimIndent()

    @Test
    fun `extracts both renditions with hd first`() {
        val qualities = FacebookEmbedParser.findQualities(embedHtml)

        assertEquals(2, qualities.size)
        assertEquals("HD", qualities[0].label)
        assertEquals("SD", qualities[1].label)
        assertTrue(qualities[0].heightPx > qualities[1].heightPx)
    }

    @Test
    fun `unescapes the hd url including unicode ampersands`() {
        val hd = FacebookEmbedParser.findQualities(embedHtml).first()

        assertEquals("https://video.fstv4-1.fna.fbcdn.net/o1/v/t2/hd.mp4?a=1&b=2", hd.url)
    }

    @Test
    fun `extracts the thumbnail`() {
        assertEquals(
            "https://scontent.fna.fbcdn.net/thumb.jpg",
            FacebookEmbedParser.findThumbnailUrl(embedHtml),
        )
    }

    @Test
    fun `returns only sd when no hd rendition exists`() {
        val sdOnly = """{"sd_src":"https://cdn.example.com/sd.mp4"}"""

        val qualities = FacebookEmbedParser.findQualities(sdOnly)

        assertEquals(1, qualities.size)
        assertEquals("SD", qualities[0].label)
    }

    @Test
    fun `returns nothing for a login wall`() {
        val gated = "<html><body>Log in to Facebook</body></html>"

        assertTrue(FacebookEmbedParser.findQualities(gated).isEmpty())
        assertNull(FacebookEmbedParser.findThumbnailUrl(gated))
    }
}
