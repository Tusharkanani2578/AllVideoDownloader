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

}
