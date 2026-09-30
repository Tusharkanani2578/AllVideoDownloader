package com.tushar.videodownloader.resolver

/**
 * Decodes the HTML entities a page escapes attribute values with.
 *
 * Needed wherever a value is read out of markup rather than out of inline JSON: titles
 * arrive with non-Latin text written entirely as numeric entities, and URLs arrive with
 * `&amp;` between their parameters — which a signed CDN rejects if passed through.
 */
internal fun String.unescapeHtmlEntities(): String {
    val named = replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")

    return NUMERIC_ENTITY.replace(named) { match ->
        val (prefix, digits) = match.destructured
        val codePoint = digits.toIntOrNull(if (prefix == "x") 16 else 10)
        if (codePoint != null && codePoint in 1..0x10FFFF) {
            String(Character.toChars(codePoint))
        } else {
            match.value
        }
    }
}

private val NUMERIC_ENTITY = Regex("""&#(x?)([0-9a-fA-F]+);""")
