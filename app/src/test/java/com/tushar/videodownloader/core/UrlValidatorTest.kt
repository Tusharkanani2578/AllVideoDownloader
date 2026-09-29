package com.tushar.videodownloader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {

    @Test
    fun `accepts a plain url`() {
        val result = UrlValidator.validate("https://www.instagram.com/reel/ABC123/")

        assertEquals("www.instagram.com", result.getOrThrow().host)
    }

    @Test
    fun `extracts the url from surrounding share text`() {
        val shared = "Check this out! https://www.instagram.com/reel/ABC123/ via Instagram"

        val result = UrlValidator.validate(shared)

        assertEquals("/reel/ABC123/", result.getOrThrow().encodedPath)
    }

    @Test
    fun `strips punctuation glued to the end of a link`() {
        val result = UrlValidator.validate("watch this (https://example.com/clip.mp4).")

        assertEquals("/clip.mp4", result.getOrThrow().encodedPath)
    }

    @Test
    fun `rejects text with no url`() {
        val result = UrlValidator.validate("just some words")

        assertTrue(result.isFailure)
        assertEquals(DownloadError.InvalidUrl, (result.exceptionOrNull() as ValidationException).error)
    }

    @Test
    fun `rejects a blank input`() {
        assertTrue(UrlValidator.validate("   ").isFailure)
    }

    @Test
    fun `rejects a host without a dot`() {
        val result = UrlValidator.validate("http://localhost/video.mp4")

        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects a non http scheme`() {
        assertTrue(UrlValidator.validate("ftp://example.com/video.mp4").isFailure)
    }
}
