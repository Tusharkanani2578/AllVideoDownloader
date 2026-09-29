package com.tushar.videodownloader.resolver

import com.tushar.videodownloader.core.DownloadError
import okhttp3.HttpUrl

/**
 * Picks the resolver for a link. Order matters: platform resolvers are tried before
 * [DirectUrlResolver], which would otherwise claim any URL ending in `.mp4`.
 */
class ResolverRegistry(
    private val resolvers: List<MediaResolver>,
) {

    suspend fun resolve(url: HttpUrl): Result<ResolvedMedia> {
        val resolver = resolvers.firstOrNull { it.canHandle(url) }
            ?: return Result.failure(
                ResolveException(DownloadError.UnsupportedPlatform(url.host))
            )
        return resolver.resolve(url)
    }

    fun supportedPlatforms(): List<String> =
        resolvers.map { it.platform.displayName }.distinct()
}
