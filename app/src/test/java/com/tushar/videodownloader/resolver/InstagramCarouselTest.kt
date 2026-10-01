package com.tushar.videodownloader.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramCarouselTest {

    private fun imageItem(code: String, url: String, height: Int = 1920) = """
        {"__typename":"XIGPolarisImageMedia","code":"$code",
         "display_uri":"https://cdn.example.com/$code-640.jpg",
         "original_height":$height,"original_width":1440,
         "image_versions2":{"candidates":[{"url":"$url"},{"url":"https://cdn.example.com/small.webp"}]}}
    """.trimIndent().replace("\n", "")

    private fun videoItem(code: String, url: String) = """
        {"__typename":"XIGPolarisVideoMedia","code":"$code","original_height":1080,
         "video_versions":[{"url":"$url"}]}
    """.trimIndent().replace("\n", "")

    private fun page(vararg items: String) =
        """{"carousel_media":[${items.joinToString(",")}],"username":"someone"}"""

    @Test
    fun `reads every slide in order`() {
        val html = page(
            imageItem("a", "https://cdn.example.com/a.webp"),
            imageItem("b", "https://cdn.example.com/b.webp"),
            imageItem("c", "https://cdn.example.com/c.webp"),
        )

        val items = InstagramPageParser.findCarouselItems(html)

        assertEquals(3, items.size)
        assertEquals(listOf("1", "2", "3"), items.map { it.label })
        assertEquals(
            listOf("a.webp", "b.webp", "c.webp").map { "https://cdn.example.com/$it" },
            items.map { it.url },
        )
    }

    @Test
    fun `prefers the full resolution over the display crop`() {
        // display_uri is a 640px crop; image_versions2 carries the image as posted.
        val html = page(imageItem("a", "https://cdn.example.com/full.webp"))

        assertEquals(
            "https://cdn.example.com/full.webp",
            InstagramPageParser.findCarouselItems(html).single().url,
        )
    }

    @Test
    fun `labels each slide with what it actually is`() {
        val html = page(
            imageItem("a", "https://cdn.example.com/a.webp"),
            videoItem("b", "https://cdn.example.com/b.mp4"),
        )

        val items = InstagramPageParser.findCarouselItems(html)

        assertEquals(MediaKind.IMAGE_WEBP, items[0].kind)
        assertEquals(MediaKind.VIDEO, items[1].kind)
    }

    @Test
    fun `does not run past the end of the carousel`() {
        val html = page(imageItem("a", "https://cdn.example.com/a.webp")) +
            ""","unrelated":[{"url":"https://cdn.example.com/other.webp"}]"""

        assertEquals(1, InstagramPageParser.findCarouselItems(html).size)
    }

    @Test
    fun `returns nothing for a post that is not a carousel`() {
        val html = """{"video_url":"https://cdn.example.com/a.mp4","username":"someone"}"""

        assertTrue(InstagramPageParser.findCarouselItems(html).isEmpty())
    }

    @Test
    fun `opens on the slide the link names, counting from one`() {
        val items = (1..3).map { MediaOption(label = "$it", url = "https://cdn.example.com/$it") }
        val media = ResolvedMedia(
            sourceUrl = "https://www.instagram.com/p/X/?img_index=2",
            title = "someone — Instagram post",
            thumbnailUrl = null,
            options = items,
            platform = Platform.INSTAGRAM,
            kind = MediaKind.IMAGE_WEBP,
            optionKind = OptionKind.ITEM,
            preferredIndex = 1,
        )

        assertEquals("2", media.defaultOption.label)
    }

    @Test
    fun `falls back to the tallest rendition when the link names no slide`() {
        val media = ResolvedMedia(
            sourceUrl = "https://www.facebook.com/x",
            title = "Facebook video",
            thumbnailUrl = null,
            options = listOf(
                MediaOption(label = "SD", url = "https://cdn.example.com/sd", heightPx = 360),
                MediaOption(label = "HD", url = "https://cdn.example.com/hd", heightPx = 1080),
            ),
            platform = Platform.FACEBOOK,
        )

        assertEquals("HD", media.defaultOption.label)
    }

    @Test
    fun `saves each slide as what it is, not as what the post is`() {
        val photo = MediaOption(label = "1", url = "https://a/1", kind = MediaKind.IMAGE_WEBP)
        val video = MediaOption(label = "2", url = "https://a/2", kind = MediaKind.VIDEO)
        val media = ResolvedMedia(
            sourceUrl = "https://www.instagram.com/p/X/",
            title = "someone — Instagram post",
            thumbnailUrl = null,
            options = listOf(photo, video),
            platform = Platform.INSTAGRAM,
            kind = MediaKind.IMAGE_WEBP,
            optionKind = OptionKind.ITEM,
        )

        assertEquals("webp", media.kindOf(photo).fileExtension)
        assertEquals("mp4", media.kindOf(video).fileExtension)
    }
}
