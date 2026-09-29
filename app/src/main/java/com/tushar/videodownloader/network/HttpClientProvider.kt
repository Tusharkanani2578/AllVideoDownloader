package com.tushar.videodownloader.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Single shared [OkHttpClient] — each instance owns connection and thread pools, so
 * one per app, not one per request.
 */
class HttpClientProvider {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Downloads legitimately run for minutes; the read timeout guards stalls.
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (compatible; AllVideoDownloader/1.0)"
    }
}
