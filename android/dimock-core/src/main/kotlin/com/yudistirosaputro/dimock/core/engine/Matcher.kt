package com.yudistirosaputro.dimock.core.engine

import com.yudistirosaputro.dimock.core.model.BodyMatch
import com.yudistirosaputro.dimock.core.model.Match
import com.yudistirosaputro.dimock.core.model.RequestSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Pure predicate: does a [Match] accept a [RequestSnapshot]? Every present field must match (AND). */
object Matcher {

    fun matches(match: Match, request: RequestSnapshot): Boolean {
        match.method?.let { if (!it.equals(request.method, ignoreCase = true)) return false }
        match.path?.let { if (!pathMatches(it, request.path)) return false }
        match.host?.let { if (!Glob.matches(it, request.host, segmentSeparator = null)) return false }
        for ((k, v) in match.query) {
            val actual = request.query.entries.firstOrNull { it.key == k }?.value ?: return false
            if (v !in actual) return false
        }
        for ((k, v) in match.headers) {
            val actual = request.headers.entries.firstOrNull { it.key.equals(k, ignoreCase = true) }?.value ?: return false
            if (actual.none { it == v }) return false
        }
        if (match.body.isNotEmpty()) {
            val root = request.body?.let { parseJsonOrNull(it) } ?: return false
            for (assertion in match.body) if (!bodyMatches(assertion, root)) return false
        }
        return true
    }

    fun pathMatches(pattern: String, path: String): Boolean =
        if (pattern.startsWith("re:")) {
            Regex(pattern.removePrefix("re:")).matches(path)
        } else {
            Glob.matches(pattern, path, segmentSeparator = '/')
        }

    private fun bodyMatches(assertion: BodyMatch, root: JsonElement): Boolean {
        val leaf = JsonPath.select(root, assertion.path) ?: return false
        return leafToString(leaf) == assertion.equals
    }

    private fun leafToString(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> if (element is JsonNull) "null" else element.content
        else -> null
    }

    private fun parseJsonOrNull(text: String): JsonElement? = try {
        Json.parseToJsonElement(text)
    } catch (_: Exception) {
        null
    }
}

/** Glob with `*` (within one segment when [segmentSeparator] is set) and `**` (across segments). */
internal object Glob {
    fun matches(pattern: String, value: String, segmentSeparator: Char?): Boolean {
        val sb = StringBuilder("^")
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '*' && i + 1 < pattern.length && pattern[i + 1] == '*' -> { sb.append(".*"); i += 2; continue }
                c == '*' -> sb.append(if (segmentSeparator == null) ".*" else "[^${Regex.escape(segmentSeparator.toString())}]*")
                c == '?' -> sb.append('.')
                else -> sb.append(Regex.escape(c.toString()))
            }
            i++
        }
        sb.append("$")
        return Regex(sb.toString()).matches(value)
    }
}

/** JSONPath subset: `$`, `.key`, `['key']`, `[index]`. Returns null when the path does not resolve. */
internal object JsonPath {
    private val token = Regex("""\.([A-Za-z0-9_\-]+)|\[(\d+)]|\['([^']*)']""")

    fun select(root: JsonElement, path: String): JsonElement? {
        val body = path.trim().removePrefix("$")
        var current: JsonElement = root
        var pos = 0
        while (pos < body.length) {
            val m = token.matchAt(body, pos) ?: return null
            pos = m.range.last + 1
            current = when {
                m.groups[1] != null -> (current as? JsonObject)?.get(m.groupValues[1]) ?: return null
                m.groups[2] != null -> (current as? JsonArray)?.getOrNull(m.groupValues[2].toInt()) ?: return null
                m.groups[3] != null -> (current as? JsonObject)?.get(m.groupValues[3]) ?: return null
                else -> return null
            }
        }
        return current
    }
}
