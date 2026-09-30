package com.tushar.videodownloader.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramPageParserTest {

    @Test
    fun `reads the rendition list before any single-url field`() {
        val html = """
            {"video_versions":[{"url":"https:\/\/cdn.example.com\/best.mp4"}],
             "video_url":"https:\/\/cdn.example.com\/fallback.mp4"}
        """.trimIndent()

        assertEquals(
            "https://cdn.example.com/best.mp4",
            InstagramPageParser.findVideoUrl(html),
        )
    }

    @Test
    fun `falls through every known field name`() {
        val cases = mapOf(
            """{"video_url":"https://a/1.mp4"}""" to "https://a/1.mp4",
            """{"playable_url_quality_hd":"https://a/2.mp4"}""" to "https://a/2.mp4",
            """{"playable_url":"https://a/3.mp4"}""" to "https://a/3.mp4",
            """{"browser_native_hd_url":"https://a/4.mp4"}""" to "https://a/4.mp4",
            """{"browser_native_sd_url":"https://a/5.mp4"}""" to "https://a/5.mp4",
            """<meta property="og:video" content="https://a/6.mp4">""" to "https://a/6.mp4",
            """<meta property="og:video:url" content="https://a/7.mp4">""" to "https://a/7.mp4",
            """<meta property="og:video:secure_url" content="https://a/8.mp4">""" to "https://a/8.mp4",
        )

        cases.forEach { (html, expected) ->
            assertEquals(expected, InstagramPageParser.findVideoUrl(html))
        }
    }

    @Test
    fun `unescapes slashes and unicode ampersands`() {
        val html = """{"video_url":"https:\/\/cdn.example.com\/a.mp4?x=1&oe=2"}"""

        assertEquals(
            "https://cdn.example.com/a.mp4?x=1&oe=2",
            InstagramPageParser.findVideoUrl(html),
        )
    }

    @Test
    fun `collapses doubly escaped slashes`() {
        val html = """{"video_url":"https:\\\/\\\/cdn.example.com\\\/a.mp4"}"""

        assertEquals("https://cdn.example.com/a.mp4", InstagramPageParser.findVideoUrl(html))
    }

    @Test
    fun `decodes the html entities a meta tag escapes the url with`() {
        val html = """<meta property="og:image" content="https://cdn.example.com/t.jpg?a=1&amp;oh=2&amp;oe=3">"""

        // A literal "&amp;" reaching the CDN invalidates the signature and returns 403.
        assertEquals(
            "https://cdn.example.com/t.jpg?a=1&oh=2&oe=3",
            InstagramPageParser.findImageUrl(html),
        )
    }

    @Test
    fun `ignores a match that is not a url`() {
        val html = """{"video_url":"pending"}"""

        assertNull(InstagramPageParser.findVideoUrl(html))
    }

    @Test
    fun `returns null when the page carries no video`() {
        assertNull(InstagramPageParser.findVideoUrl("<html><body>nothing here</body></html>"))
    }

    @Test
    fun `reads the image and the username`() {
        val html = """{"username":"cleanup.surat","display_url":"https:\/\/cdn.example.com\/i.jpg"}"""

        assertEquals("https://cdn.example.com/i.jpg", InstagramPageParser.findImageUrl(html))
        assertEquals("cleanup.surat", InstagramPageParser.findUsername(html))
    }
}
