package com.tushar.videodownloader.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun `formats zero and negative sizes safely`() {
        assertEquals("0 B", 0L.toReadableSize())
        assertEquals("0 B", (-1L).toReadableSize())
    }

    @Test
    fun `formats bytes without a decimal place`() {
        assertEquals("512 B", 512L.toReadableSize())
    }

    @Test
    fun `formats kilobytes and megabytes with one decimal place`() {
        assertEquals("1.0 KB", 1024L.toReadableSize())
        assertEquals("1.5 MB", (1024L * 1024 * 3 / 2).toReadableSize())
    }

    @Test
    fun `formats gigabytes`() {
        assertEquals("2.0 GB", (2L * 1024 * 1024 * 1024).toReadableSize())
    }

    @Test
    fun `renders a speed with a per second suffix`() {
        assertEquals("1.0 MB/s", (1024L * 1024).toReadableSpeed())
    }
}
