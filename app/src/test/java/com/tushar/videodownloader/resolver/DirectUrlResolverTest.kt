package com.tushar.videodownloader.resolver

import com.tushar.videodownloader.core.DownloadError
import com.tushar.videodownloader.network.HttpClientProvider
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the one resolver whose target URL comes from the caller, so a mock server can
 * stand in for it. The platform resolvers address their own host by design and are covered
 * at the parser level instead.
 */
class DirectUrlResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var resolver: DirectUrlResolver

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        resolver = DirectUrlResolver(HttpClientProvider())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url(path: String): HttpUrl = server.url(path)

    private fun errorFrom(result: Result<ResolvedMedia>): DownloadError =
        (result.exceptionOrNull() as ResolveException).error

    @Test
    fun `claims media extensions and nothing else`() {
        assertTrue(resolver.canHandle(url("/clip.mp4")))
        assertTrue(resolver.canHandle(url("/clip.WEBM")))
        assertFalse(resolver.canHandle(url("/clip.txt")))
        assertFalse(resolver.canHandle(url("/clip")))
    }

    @Test
    fun `reads the size without pulling the body`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Length", "2048"),
        )

        val media = resolver.resolve(url("/clip.mp4")).getOrThrow()

        assertEquals("clip.mp4", media.title)
        assertEquals(2048L, media.qualities.single().sizeBytes)
        assertEquals(Platform.DIRECT_LINK, media.platform)
        assertEquals("HEAD", server.takeRequest().method)
    }

    @Test
    fun `reports a refused request as needing sign-in, not as a server fault`() = runTest {
        listOf(401, 403).forEach { code ->
            server.enqueue(MockResponse().setResponseCode(code))

            val error = errorFrom(resolver.resolve(url("/clip.mp4")))

            assertEquals(DownloadError.AuthenticationRequired, error)
        }
    }

    @Test
    fun `passes any other failing status through with its code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val error = errorFrom(resolver.resolve(url("/clip.mp4")))

        assertEquals(DownloadError.ServerError(503), error)
    }

    @Test
    fun `refuses a link that answers with a page rather than a video`() = runTest {
        // A .mp4 path is not a promise; hosts serve error pages under one all the time.
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html"))

        val error = errorFrom(resolver.resolve(url("/clip.mp4")))

        assertEquals(DownloadError.NoMediaFound, error)
    }

    @Test
    fun `accepts a server that declares no content type`() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", ""))

        val media = resolver.resolve(url("/clip.mp4")).getOrThrow()

        assertEquals(1, media.qualities.size)
    }

    @Test
    fun `leaves the size null when the server does not declare one`() = runTest {
        // Streamed sources answer without a length; the UI then shows an indeterminate
        // bar rather than a percentage it cannot compute.
        server.enqueue(
            MockResponse().setHeader("Content-Type", "video/mp4").removeHeader("Content-Length"),
        )

        val media = resolver.resolve(url("/clip.mp4")).getOrThrow()

        assertEquals(null, media.qualities.single().sizeBytes)
    }

    @Test
    fun `fails rather than throws when the host cannot be reached`() = runTest {
        val unreachable = url("/clip.mp4")
        server.shutdown()

        val result = resolver.resolve(unreachable)

        assertTrue(result.isFailure)
        assertTrue(errorFrom(result) is DownloadError.Unexpected)
    }
}
