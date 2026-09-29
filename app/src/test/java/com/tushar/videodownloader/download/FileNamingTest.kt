package com.tushar.videodownloader.download

import com.tushar.videodownloader.resolver.Platform
import com.tushar.videodownloader.resolver.ResolvedMedia
import com.tushar.videodownloader.resolver.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamingTest {

    private fun media(title: String, url: String) = ResolvedMedia(
        sourceUrl = url,
        title = title,
        thumbnailUrl = null,
        qualities = listOf(VideoQuality("720p", url, heightPx = 720)),
        platform = Platform.INSTAGRAM,
    )

    private val quality = VideoQuality("720p", "https://cdn.example.com/a.mp4", heightPx = 720)

    @Test
    fun `is stable for the same video and quality`() {
        val first = FileNaming.buildFileName(media("Clip", "https://x.com/p/1"), quality)
        val second = FileNaming.buildFileName(media("Clip", "https://x.com/p/1"), quality)

        assertEquals(first, second)
    }

    @Test
    fun `differs for the same title from a different source`() {
        val first = FileNaming.buildFileName(media("Clip", "https://x.com/p/1"), quality)
        val second = FileNaming.buildFileName(media("Clip", "https://x.com/p/2"), quality)

        assertNotEquals(first, second)
    }

    @Test
    fun `strips characters that are illegal in a file name`() {
        val name = FileNaming.buildFileName(media("""a/b\c:d*e?f"g<h>i|j""", "https://x.com/p/1"), quality)

        assertTrue(name.none { it in """/\:*?"<>|""" })
    }

    @Test
    fun `falls back to a default when the title has no usable characters`() {
        val name = FileNaming.buildFileName(media("???", "https://x.com/p/1"), quality)

        assertTrue(name.contains("video"))
    }

    @Test
    fun `strips a media extension already present in the title`() {
        val name = FileNaming.buildFileName(media("clip.mp4", "https://x.com/p/1"), quality)

        assertTrue(name.contains("clip"))
        assertTrue("extension must not survive inside the name", !name.contains("clipmp4"))
    }

    @Test
    fun `keeps a trailing segment that is not a media extension`() {
        val name = FileNaming.buildFileName(media("episode 2.final cut", "https://x.com/p/1"), quality)

        assertTrue(name.contains("final_cut"))
    }

    @Test
    fun `always ends with an mp4 extension`() {
        val name = FileNaming.buildFileName(media("Clip", "https://x.com/p/1"), quality)

        assertTrue(name.endsWith(".mp4"))
    }
}
