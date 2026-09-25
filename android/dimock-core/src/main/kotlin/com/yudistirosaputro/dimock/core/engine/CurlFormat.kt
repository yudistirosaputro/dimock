package com.yudistirosaputro.dimock.core.engine

import com.yudistirosaputro.dimock.core.model.Body
import com.yudistirosaputro.dimock.core.model.Transaction

/**
 * A capture as a `curl` command that replays in a terminal or Postman. Same shape as the CLI's `asCurl`.
 *
 * Redacted header values are exported as the stored placeholder ([Redactor.MASK]) — dimock never keeps the
 * raw secret, so nothing here can leak it; the person pastes their own token before replaying.
 */
object CurlFormat {

    private val skip = setOf("host", "content-length", "accept-encoding", "connection", "user-agent")

    fun format(tx: Transaction): String = buildString {
        append("curl -X ").append(tx.method.uppercase()).append(' ').append(quote(tx.url))
        tx.requestHeaders.forEach { (name, values) ->
            if (name.lowercase() in skip) return@forEach
            values.forEach { append(" \\\n  -H ").append(quote("$name: $it")) }
        }
        requestBody(tx)?.takeIf { it.isNotEmpty() }?.let { append(" \\\n  --data-raw ").append(quote(it)) }
    }

    /** True when at least one request header was redacted before storage. */
    fun hasRedacted(tx: Transaction): Boolean = tx.requestHeaders.values.any { values -> values.any { it == Redactor.MASK } }

    /** Names of the redacted request headers, for the sheet's note. */
    fun redactedHeaders(tx: Transaction): List<String> =
        tx.requestHeaders.entries.filter { (_, v) -> v.any { it == Redactor.MASK } }.map { it.key }

    private fun requestBody(tx: Transaction): String? = when (val b = tx.requestBody) {
        is Body.Text -> b.text
        is Body.Truncated -> b.text
        else -> null
    }

    /** Characters no shell treats specially; `?`, `*` (glob) and `&` (background) force quoting. */
    private val bare = Regex("^[A-Za-z0-9_\\-./:=@%+,]+$")

    private fun quote(s: String) = if (bare.matches(s)) s else "'" + s.replace("'", "'\\''") + "'"
}
