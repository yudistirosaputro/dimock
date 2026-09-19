package com.yudistirosaputro.dimock.core.engine

import com.yudistirosaputro.dimock.core.model.Body
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder

/** What the detail screen renders for one side of an exchange. Computed once per capture, off the UI thread. */
sealed interface BodyView {
    /** No body was recorded, or it was empty. */
    data object None : BodyView

    /** Binary payload dropped by the interceptor; only metadata survived. */
    data class Binary(val totalBytes: Long, val contentType: String?) : BodyView

    /**
     * Text body. [pretty] is the readable form (indented JSON, decoded form fields, or the raw text);
     * [raw] is exactly what was stored. [truncatedAt] is the stored length when the body was cut.
     */
    data class Text(
        val raw: String,
        val pretty: String,
        val contentType: String?,
        val kind: Kind,
        val truncatedAt: Long? = null,
        val totalBytes: Long = raw.length.toLong(),
    ) : BodyView {
        enum class Kind { JSON, FORM, PLAIN }
    }
}

/** Body rendering shared by the inspector and the notification. Platform-free. */
object BodyFormat {

    fun view(body: Body?): BodyView = when (body) {
        null -> BodyView.None
        is Body.Binary -> BodyView.Binary(body.totalBytes, body.contentType)
        is Body.Text -> if (body.text.isEmpty()) BodyView.None else text(body.text, body.contentType, null, body.text.length.toLong())
        is Body.Truncated -> text(body.text, body.contentType, body.text.length.toLong(), body.totalBytes)
    }

    /** Indented JSON when [text] parses as JSON, null otherwise. Two-space indent, keys in source order. */
    fun prettyJson(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || (trimmed[0] != '{' && trimmed[0] != '[')) return null
        val element = try { Json.parseToJsonElement(trimmed) } catch (_: Exception) { return null }
        return buildString { write(element, 0) }
    }

    /** `a=1&b=hello+world` → `a: 1\nb: hello world`. Keys and values are percent-decoded. */
    fun formLines(text: String): String =
        text.split('&').filter { it.isNotEmpty() }.joinToString("\n") { pair ->
            val key = decode(pair.substringBefore('='))
            if (!pair.contains('=')) key else "$key: ${decode(pair.substringAfter('='))}"
        }

    fun isForm(contentType: String?): Boolean = contentType?.contains("x-www-form-urlencoded", ignoreCase = true) == true

    fun isJson(contentType: String?): Boolean = contentType?.contains("json", ignoreCase = true) == true

    /** Start offsets of every case-insensitive match of [query] in [text]; empty for a blank query. */
    fun search(text: String, query: String): List<Int> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val hits = ArrayList<Int>()
        var from = 0
        while (true) {
            val i = text.indexOf(needle, from, ignoreCase = true)
            if (i < 0) return hits
            hits += i
            from = i + needle.length
        }
    }

    /** The `Content-Type` header value, if any, without parameters (`application/json; charset=utf-8` → `application/json`). */
    fun contentType(headers: Map<String, List<String>>): String? =
        headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value?.firstOrNull()?.substringBefore(';')?.trim()

    private fun text(raw: String, contentType: String?, truncatedAt: Long?, totalBytes: Long): BodyView.Text {
        val json = if (truncatedAt == null) prettyJson(raw) else null
        return when {
            json != null -> BodyView.Text(raw, json, contentType, BodyView.Text.Kind.JSON, truncatedAt, totalBytes)
            isForm(contentType) -> BodyView.Text(raw, formLines(raw), contentType, BodyView.Text.Kind.FORM, truncatedAt, totalBytes)
            else -> BodyView.Text(raw, raw, contentType, BodyView.Text.Kind.PLAIN, truncatedAt, totalBytes)
        }
    }

    private fun decode(s: String): String = try { URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }

    private fun StringBuilder.write(element: JsonElement, depth: Int) {
        when (element) {
            is JsonObject -> {
                if (element.isEmpty()) { append("{}"); return }
                append("{\n")
                element.entries.forEachIndexed { i, (k, v) ->
                    indent(depth + 1); append(JsonPrimitive(k).toString()); append(": "); write(v, depth + 1)
                    if (i < element.size - 1) append(',')
                    append('\n')
                }
                indent(depth); append('}')
            }
            is JsonArray -> {
                if (element.isEmpty()) { append("[]"); return }
                append("[\n")
                element.forEachIndexed { i, v ->
                    indent(depth + 1); write(v, depth + 1)
                    if (i < element.size - 1) append(',')
                    append('\n')
                }
                indent(depth); append(']')
            }
            JsonNull -> append("null")
            is JsonPrimitive -> append(element.toString())
        }
    }

    private fun StringBuilder.indent(depth: Int) { repeat(depth) { append("  ") } }
}
