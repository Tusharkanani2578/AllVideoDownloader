package com.tushar.videodownloader.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenGraphParserTest {

    @Test
    fun `reads content when property comes first`() {
        val html = """<meta property="og:video" content="https://cdn.example.com/a.mp4" />"""

        assertEquals("https://cdn.example.com/a.mp4", OpenGraphParser.findContent(html, "og:video"))
    }

    @Test
    fun `reads content when content comes first`() {
        val html = """<meta content="https://cdn.example.com/b.mp4" property="og:video" />"""

        assertEquals("https://cdn.example.com/b.mp4", OpenGraphParser.findContent(html, "og:video"))
    }

    @Test
    fun `handles single quoted attributes`() {
        val html = "<meta property='og:title' content='My clip' />"

        assertEquals("My clip", OpenGraphParser.findContent(html, "og:title"))
    }

    @Test
    fun `unescapes html entities in the content`() {
        val html = """<meta property="og:video" content="https://cdn.example.com/a.mp4?x=1&amp;y=2" />"""

        assertEquals(
            "https://cdn.example.com/a.mp4?x=1&y=2",
            OpenGraphParser.findContent(html, "og:video"),
        )
    }

    @Test
    fun `decodes hexadecimal numeric entities`() {
        val html = """<meta property="og:title" content="&#x907;&#x938; &#x930;&#x940;&#x932;" />"""

        assertEquals("इस रील", OpenGraphParser.findContent(html, "og:title"))
    }

    @Test
    fun `decodes decimal numeric entities`() {
        val html = """<meta property="og:title" content="&#2311;&#2360;" />"""

        assertEquals("इस", OpenGraphParser.findContent(html, "og:title"))
    }

    @Test
    fun `decodes entities outside the basic multilingual plane`() {
        val html = """<meta property="og:title" content="nice &#x1F525;" />"""

        assertEquals("nice 🔥", OpenGraphParser.findContent(html, "og:title"))
    }

    @Test
    fun `leaves an out of range numeric entity untouched`() {
        val html = """<meta property="og:title" content="&#xFFFFFFF;" />"""

        assertEquals("&#xFFFFFFF;", OpenGraphParser.findContent(html, "og:title"))
    }

    @Test
    fun `returns null when the tag is absent`() {
        assertNull(OpenGraphParser.findContent("<html><head></head></html>", "og:video"))
    }

    @Test
    fun `does not confuse a similarly named property`() {
        val html = """<meta property="og:video:height" content="1080" />"""

        assertEquals("1080", OpenGraphParser.findContent(html, "og:video:height"))
    }
}
