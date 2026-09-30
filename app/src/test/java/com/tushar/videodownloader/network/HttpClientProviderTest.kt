package com.tushar.videodownloader.network

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HttpClientProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: HttpClientProvider

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        provider = HttpClientProvider()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `identifies the app on a request that sets no agent`() {
        server.enqueue(MockResponse().setBody("ok"))

        provider.client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        assertEquals(
            HttpClientProvider.USER_AGENT,
            server.takeRequest().getHeader("User-Agent"),
        )
    }

    @Test
    fun `leaves an agent the caller chose alone`() {
        // The platform resolvers depend on specific agents; the default must not win.
        server.enqueue(MockResponse().setBody("ok"))
        val request = Request.Builder()
            .url(server.url("/"))
            .header("User-Agent", "Something/1.0")
            .build()

        provider.client.newCall(request).execute().close()

        assertEquals("Something/1.0", server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun `applies to a redirect the client follows on its own`() {
        server.enqueue(
            MockResponse().setResponseCode(302).setHeader("Location", server.url("/final")),
        )
        server.enqueue(MockResponse().setBody("ok"))

        provider.client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        assertEquals(HttpClientProvider.USER_AGENT, server.takeRequest().getHeader("User-Agent"))
        assertEquals(HttpClientProvider.USER_AGENT, server.takeRequest().getHeader("User-Agent"))
    }
}
