package com.tushar.videodownloader.ui

import com.tushar.videodownloader.resolver.MediaKind
import com.tushar.videodownloader.resolver.MediaOption
import com.tushar.videodownloader.resolver.OptionKind
import com.tushar.videodownloader.resolver.Platform
import com.tushar.videodownloader.resolver.ResolvedMedia
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the Download button resolves the user's choice into. The ViewModel needs a
 * Context, so the mapping itself is covered here rather than through it.
 */
class SelectionTest {

    private val slides = (1..3).map {
        MediaOption(
            label = "$it",
            url = "https://cdn.example.com/$it.webp",
            kind = MediaKind.IMAGE_WEBP,
            thumbnailUrl = "https://cdn.example.com/$it-640.jpg",
        )
    }

    private val carousel = ResolvedMedia(
        sourceUrl = "https://www.instagram.com/p/X/?img_index=2",
        title = "someone — Instagram post",
        thumbnailUrl = "https://cdn.example.com/cover.jpg",
        options = slides,
        platform = Platform.INSTAGRAM,
        kind = MediaKind.IMAGE_WEBP,
        optionKind = OptionKind.ITEM,
        preferredIndex = 1,
    )

    /** Mirrors how the ViewModel turns a selection into the list the service downloads. */
    private fun chosen(selection: Selection, media: ResolvedMedia): List<MediaOption> =
        when (selection) {
            is Selection.One -> listOf(selection.option)
            Selection.All -> media.options
        }

    @Test
    fun `one slide downloads only that slide`() {
        assertEquals(listOf(slides[1]), chosen(Selection.One(slides[1]), carousel))
    }

    @Test
    fun `all downloads every slide, in the order they appear`() {
        assertEquals(slides, chosen(Selection.All, carousel))
    }

    @Test
    fun `the link's slide is what the screen opens on`() {
        assertEquals(slides[1], carousel.defaultOption)
    }

    @Test
    fun `every slide keeps its own thumbnail, so the picker shows what it is`() {
        assertEquals(
            slides.map { it.thumbnailUrl },
            carousel.options.map { it.thumbnailUrl },
        )
    }
}
