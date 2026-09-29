package com.tushar.videodownloader

import android.content.Context
import com.tushar.videodownloader.download.Downloader
import com.tushar.videodownloader.download.MediaStoreSaver
import com.tushar.videodownloader.network.HttpClientProvider
import com.tushar.videodownloader.network.NetworkMonitor
import com.tushar.videodownloader.resolver.DirectUrlResolver
import com.tushar.videodownloader.resolver.FacebookResolver
import com.tushar.videodownloader.resolver.HlsResolver
import com.tushar.videodownloader.resolver.InstagramResolver
import com.tushar.videodownloader.resolver.ResolverRegistry
import java.io.File

/**
 * Manual dependency wiring — the graph is four objects, so a DI framework would add
 * setup without benefit. Everything stays constructor-injected and testable.
 */
object ServiceLocator {

    private val httpClientProvider by lazy { HttpClientProvider() }

    // Order matters: platform resolvers before DirectUrlResolver, which would
    // otherwise claim any URL ending in .mp4.
    val resolverRegistry: ResolverRegistry by lazy {
        ResolverRegistry(
            listOf(
                InstagramResolver(httpClientProvider),
                FacebookResolver(httpClientProvider),
                HlsResolver(httpClientProvider),
                DirectUrlResolver(httpClientProvider),
            )
        )
    }

    fun downloader(context: Context): Downloader {
        val appContext = context.applicationContext
        return Downloader(
            httpClient = httpClientProvider,
            networkMonitor = NetworkMonitor(appContext),
            mediaStoreSaver = MediaStoreSaver(appContext),
            tempDir = partialsDir(appContext),
        )
    }

    fun networkMonitor(context: Context) = NetworkMonitor(context.applicationContext)

    /** Partials live in cache so the OS can reclaim them under storage pressure. */
    private fun partialsDir(context: Context): File =
        File(context.cacheDir, "partials").apply { mkdirs() }
}
