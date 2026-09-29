package com.tushar.videodownloader.core

/**
 * Every failure the app can produce, as a closed set. Each case maps to an edge case
 * in the spec, so the compiler forces the UI to handle all of them.
 */
sealed class DownloadError(val userMessage: String) {

    data object InvalidUrl :
        DownloadError("That doesn't look like a valid link. Please paste a full video URL.")

    data class UnsupportedPlatform(val host: String) :
        DownloadError("Links from $host aren't supported yet.")

    data object NoMediaFound :
        DownloadError("No downloadable video was found at this link.")

    /** The post is public and previewable, but the platform withholds the video itself. */
    data class NoPublicMedia(val platformName: String) :
        DownloadError(
            "$platformName doesn't make this video available to download without " +
                "signing in, so only its preview could be loaded."
        )

    /** Encrypted segments would concatenate into a file that looks fine but won't play. */
    data object EncryptedStream :
        DownloadError("This stream is encrypted and can't be saved as a video file.")

    data object AuthenticationRequired :
        DownloadError("This video is private or requires sign-in, so it can't be downloaded.")

    data object NoNetwork :
        DownloadError("You're offline. Check your connection and try again.")

    data class Interrupted(val bytesDownloaded: Long) :
        DownloadError("The download stopped unexpectedly. Tap retry to resume.")

    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) :
        DownloadError(
            "Not enough space. This video needs ${requiredBytes.toReadableSize()}, " +
                "but only ${availableBytes.toReadableSize()} is free."
        )

    data object Cancelled :
        DownloadError("Download cancelled.")

    data class AlreadyDownloaded(val existingFileName: String) :
        DownloadError("Already saved to your gallery as $existingFileName.")

    data class ServerError(val code: Int) :
        DownloadError("The server refused the request (error $code). Try again later.")

    data object Timeout :
        DownloadError("The request timed out. Please try again.")

    data class Unexpected(val cause: Throwable) :
        DownloadError("Something went wrong. Please try again.")
}
