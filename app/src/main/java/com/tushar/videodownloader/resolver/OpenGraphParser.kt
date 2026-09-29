package com.tushar.videodownloader.resolver

/**
 * Minimal Open Graph meta-tag reader. A full HTML parser would add ~400 KB for a
 * single-tag lookup; the regex tolerates both attribute orders and quote styles.
 */
internal object OpenGraphParser {

    fun findContent(html: String, property: String): String? {
        val escaped = Regex.escape(property)

        val propertyFirst = Regex(
            """<meta[^>]+(?:property|name)=["']$escaped["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )
        val contentFirst = Regex(
            """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']$escaped["']""",
            RegexOption.IGNORE_CASE,
        )

        val raw = propertyFirst.find(html)?.groupValues?.get(1)
            ?: contentFirst.find(html)?.groupValues?.get(1)
            ?: return null

        return raw.unescapeHtmlEntities().takeIf { it.isNotBlank() }
    }

    private val NUMERIC_ENTITY = Regex("""&#(x?)([0-9a-fA-F]+);""")

    // Non-Latin titles (Hindi, emoji) arrive entirely as numeric entities.
    private fun String.unescapeHtmlEntities(): String {
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
}
