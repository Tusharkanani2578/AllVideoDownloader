package com.tushar.videodownloader.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramEmbedParserTest {

    // Mirrors the real escaping: JSON inside a script string, quotes and slashes escaped.
    private val embedHtml = """
        <script>s.handle({"data":{\"shortcode_media\":{\"owner\":{\"username\":\"cleanup.surat\"},
        \"display_url\":\"https:\\\/\\\/scontent.cdninstagram.com\\\/v\\\/t51.82787-15\\\/thumb.jpg\",
        \"video_url\":\"https:\\\/\\\/instagram.fstv5-1.fna.fbcdn.net\\\/o1\\\/v\\\/t16\\\/f2\\\/m84\\\/clip.mp4?_nc_cat=110\\u0026_nc_oc=Ado1\\u0026oe=66F0\",
        \"is_video\":true}}})</script>
    """.trimIndent()

    @Test
    fun `extracts and unescapes the video url`() {
        assertEquals(
            "https://instagram.fstv5-1.fna.fbcdn.net/o1/v/t16/f2/m84/clip.mp4" +
                "?_nc_cat=110&_nc_oc=Ado1&oe=66F0",
            InstagramEmbedParser.findVideoUrl(embedHtml),
        )
    }

    @Test
    fun `extracts the thumbnail url`() {
        assertEquals(
            "https://scontent.cdninstagram.com/v/t51.82787-15/thumb.jpg",
            InstagramEmbedParser.findThumbnailUrl(embedHtml),
        )
    }

    @Test
    fun `extracts the username`() {
        assertEquals("cleanup.surat", InstagramEmbedParser.findUsername(embedHtml))
    }

    @Test
    fun `handles singly escaped json too`() {
        val single = """{"video_url":"https:\/\/cdn.example.com\/v\/clip.mp4?a=1&b=2"}"""

        assertEquals(
            "https://cdn.example.com/v/clip.mp4?a=1&b=2",
            InstagramEmbedParser.findVideoUrl(single),
        )
    }

    @Test
    fun `handles a completely unescaped url`() {
        val plain = """{"video_url":"https://cdn.example.com/clip.mp4?x=1"}"""

        assertEquals(
            "https://cdn.example.com/clip.mp4?x=1",
            InstagramEmbedParser.findVideoUrl(plain),
        )
    }

    @Test
    fun `returns null when the page has no video`() {
        val gated = "<html><body>Watch on Instagram</body></html>"

        assertNull(InstagramEmbedParser.findVideoUrl(gated))
        assertNull(InstagramEmbedParser.findThumbnailUrl(gated))
    }
}
