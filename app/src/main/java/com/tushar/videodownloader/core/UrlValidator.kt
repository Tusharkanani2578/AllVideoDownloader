package com.tushar.videodownloader.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Validates pasted text. Clipboard content usually arrives wrapped in share text
 * ("Check this out! https://... via Instagram"), so the first URL is extracted
 * rather than rejecting the whole string.
 */
object UrlValidator {

    private val URL_IN_TEXT = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    private const val TRAILING_JUNK = ".,;:!?)]}\"'>"

    fun validate(rawInput: String): Result<HttpUrl> {
        val candidate = URL_IN_TEXT.find(rawInput.trim())?.value
            ?: return Result.failure(ValidationException(DownloadError.InvalidUrl))

        val parsed = candidate.trimEnd { it in TRAILING_JUNK }.toHttpUrlOrNull()
            ?: return Result.failure(ValidationException(DownloadError.InvalidUrl))

        if (!parsed.host.contains('.')) {
            return Result.failure(ValidationException(DownloadError.InvalidUrl))
        }
        return Result.success(parsed)
    }
}

class ValidationException(val error: DownloadError) : Exception(error.userMessage)
