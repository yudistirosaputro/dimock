package com.yudistirosaputro.dimock.core.engine

import com.yudistirosaputro.dimock.core.model.Body

/** Strips secrets before anything is stored. Runs on the interceptor thread, so it stays allocation-light. */
class Redactor(
    headers: Collection<String> = DEFAULT_HEADERS,
    private val bodyPatterns: List<Regex> = emptyList(),
) {
    private val headerNames = headers.map { it.lowercase() }.toHashSet()

    fun headers(input: Map<String, List<String>>): Map<String, List<String>> {
        if (headerNames.isEmpty()) return input
        val out = LinkedHashMap<String, List<String>>(input.size)
        for ((name, values) in input) {
            out[name] = if (name.lowercase() in headerNames) values.map { MASK } else values
        }
        return out
    }

    fun body(text: String): String {
        var out = text
        for (p in bodyPatterns) out = p.replace(out, MASK)
        return out
    }

    fun body(body: Body?): Body? = when (body) {
        is Body.Text -> if (bodyPatterns.isEmpty()) body else body.copy(text = body(body.text))
        is Body.Truncated -> if (bodyPatterns.isEmpty()) body else body.copy(text = body(body.text))
        else -> body
    }

    companion object {
        const val MASK = "«redacted»"
        val DEFAULT_HEADERS: Set<String> = setOf("Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie", "X-Api-Key")
    }
}

/** Applies the body size limit and decides text vs binary. */
class BodyLimiter(private val maxBodyBytes: Int) {

    fun text(text: String, contentType: String?): Body =
        if (text.length <= maxBodyBytes) Body.Text(text, contentType)
        else Body.Truncated(text.substring(0, maxBodyBytes), text.length.toLong(), contentType)

    fun binary(totalBytes: Long, contentType: String?): Body = Body.Binary(totalBytes, contentType)

    fun isBinary(contentType: String?): Boolean {
        val ct = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return false
        if (ct.startsWith("text/")) return false
        if (ct.endsWith("json") || ct.endsWith("xml") || ct.endsWith("+json") || ct.endsWith("+xml")) return false
        if (ct in TEXT_TYPES) return false
        return true
    }

    private companion object {
        val TEXT_TYPES = setOf(
            "application/json", "application/xml", "application/x-www-form-urlencoded", "application/javascript",
            "application/graphql", "application/ld+json", "application/x-ndjson",
        )
    }
}
