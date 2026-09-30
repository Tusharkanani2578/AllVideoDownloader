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
        // Identify the app on every call, including ones that do not set the header
        // themselves. OkHttp's own default agent is rejected outright by hosts that
        // filter non-browser clients, which surfaced as a direct link resolving to
        // "403" while the download of the very same URL succeeded. Requests that do
        // set an agent keep it — the platform resolvers depend on specific ones.
        .addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(
                if (request.header("User-Agent") != null) {
                    request
                } else {
                    request.newBuilder().header("User-Agent", USER_AGENT).build()
                }
            )
        }
        .build()

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (compatible; AllVideoDownloader/1.0)"
    }
}
