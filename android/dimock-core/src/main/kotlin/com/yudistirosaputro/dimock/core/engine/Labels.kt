package com.yudistirosaputro.dimock.core.engine

import com.yudistirosaputro.dimock.core.model.Body
import com.yudistirosaputro.dimock.core.model.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Every display string the notification and the inspector share, computed once here so a row, a
 * notification line and a detail header never disagree. Platform-free; no `Context`.
 */
object Labels {

    const val ERROR_STATUS = "ERR"
    const val MOCK_TAG = "MOCK"
    const val WAITING = "Waiting for traffic…"

    /** `241 ms`, `1.2 s`, `10.0 s`; `—` when unknown. */
    fun duration(ms: Long?): String = when {
        ms == null -> "—"
        ms < 1_000 -> "$ms ms"
        else -> String.format(Locale.US, "%.1f s", ms / 1_000.0)
    }

    /** `0.1 KB`, `4.2 KB`, `1.3 MB`. Sub-100-byte bodies still read as `0.1 KB` so the column stays aligned. */
    fun bytes(n: Long): String = when {
        n >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", n / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.1f KB", maxOf(n, 100L) / 1024.0)
    }

    fun bodyBytes(body: Body?): Long = when (body) {
        null -> 0
        is Body.Text -> body.text.length.toLong()
        is Body.Truncated -> body.totalBytes
        is Body.Binary -> body.totalBytes
    }

    /** What the transport itself says next to the big status code. */
    fun transport(code: Int?, error: String?): String = when {
        error != null || code == null -> "no response"
        code < 200 -> "informational"
        code < 300 -> "OK"
        code < 400 -> "redirect"
        code < 500 -> "client error"
        else -> "server error"
    }

    /** Row status column: the code, or `ERR` for a failed round-trip. */
    fun status(tx: Transaction): String = if (tx.error != null || tx.responseCode == null) ERROR_STATUS else tx.responseCode.toString()

    /**
     * Human wording for a transport failure. The interceptor's own messages (`dimock: timed out after 10000 ms
     * (rule x)`, `dimock: connection reset (rule x)`) are rewritten; anything else is shown as recorded.
     */
    fun failure(error: String?): String? {
        if (error == null) return null
        timedOut.find(error)?.let { return "Timed out after ${groupThousands(it.groupValues[1])} ms" }
        if (error.contains("connection reset", ignoreCase = true)) return "Connection reset"
        return error.removePrefix("dimock: ").replaceFirstChar { it.uppercase() }
    }

    /** Short failure word for a list row or a notification line: `timeout`, `reset`, or the first words of the error. */
    fun failureShort(error: String?): String? {
        if (error == null) return null
        if (timedOut.containsMatchIn(error) || error.contains("timeout", ignoreCase = true)) return "timeout"
        if (error.contains("connection reset", ignoreCase = true)) return "reset"
        return error.removePrefix("dimock: ").substringBefore(" (").take(24)
    }

    /** `GET /posts · 200 · 241 ms`, `POST /posts · 500 · MOCK`, `GET /posts/1 · timeout`. */
    fun trafficLine(tx: Transaction): String = buildString {
        append(tx.method).append(' ').append(tx.path)
        val failure = failureShort(tx.error)
        if (failure != null) {
            append(" · ").append(failure)
        } else {
            append(" · ").append(tx.responseCode ?: ERROR_STATUS)
            if (tx.durationMs != null) append(" · ").append(duration(tx.durationMs))
        }
        if (tx.mocked) append(" · ").append(MOCK_TAG)
    }

    /** `dimock · 3 calls`, `dimock · 3 calls · 1 mock`. */
    fun notificationTitle(calls: Int, activeMocks: Int): String = buildString {
        append("dimock · ").append(plural(calls, "call"))
        if (activeMocks > 0) append(" · ").append(plural(activeMocks, "mock"))
    }

    fun plural(n: Int, noun: String): String = "$n $noun" + if (n == 1) "" else "s"

    /** `14:32:07.418` in the device's local zone. */
    fun clock(epochMs: Long): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(epochMs))

    /** `2 min ago`, `just now`, `3 h ago`. */
    fun ago(epochMs: Long, now: Long): String {
        val s = (now - epochMs) / 1_000
        return when {
            s < 5 -> "just now"
            s < 60 -> "$s s ago"
            s < 3_600 -> "${s / 60} min ago"
            s < 86_400 -> "${s / 3_600} h ago"
            else -> "${s / 86_400} d ago"
        }
    }

    private val timedOut = Regex("timed out after (\\d+) ms")

    private fun groupThousands(digits: String): String =
        digits.reversed().chunked(3).joinToString(" ").reversed()
}
