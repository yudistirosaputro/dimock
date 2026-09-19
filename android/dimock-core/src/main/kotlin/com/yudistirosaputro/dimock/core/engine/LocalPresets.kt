package com.yudistirosaputro.dimock.core.engine

import com.yudistirosaputro.dimock.core.model.Body
import com.yudistirosaputro.dimock.core.model.Fail
import com.yudistirosaputro.dimock.core.model.FailType
import com.yudistirosaputro.dimock.core.model.Match
import com.yudistirosaputro.dimock.core.model.MockRule
import com.yudistirosaputro.dimock.core.model.Respond
import com.yudistirosaputro.dimock.core.model.Transaction

/** The options offered by the in-app "Force a state" sheet (docs/prd.md §7.2, screen 3). */
enum class Preset(val id: String, val title: String, val description: String) {
    STATUS("status", "Status", "HTTP status only, empty body"),
    TIMEOUT("timeout", "Timeout", "No response · SocketTimeoutException"),
    RESET("reset", "Connection reset", "Socket closed mid-flight · IOException"),
    SLOW("slow", "Slow", "Same response, after a delay · loading state"),
    CUSTOM("custom", "Custom response", "Edit the response body and status · pre-filled from this call"),
}

/**
 * Builds the rules the in-app sheet arms, and labels every rule back for the Mocks list.
 *
 * Method and path are copied from the capture and are not editable in-app; one rule per endpoint
 * (`local:<method>-<path-slug>`), so re-applying a preset replaces the previous one and a tester never
 * stacks two mocks on the same call. Local rules carry [PRIORITY] so they win over agent-pushed rules.
 */
object LocalPresets {

    const val PRIORITY = 100
    const val ID_PREFIX = "local:"

    val STATUS_CHIPS: List<Int> = listOf(400, 401, 403, 404, 500, 503)
    val SLOW_DELAYS_MS: List<Long> = listOf(2_000L, 5_000L, 10_000L)

    fun ruleId(tx: Transaction): String = ID_PREFIX + tx.method.lowercase() + "-" + slug(tx.path)

    fun isLocal(rule: MockRule): Boolean = rule.id.startsWith(ID_PREFIX)

    /** The HTTP status alone, empty body: the transport-level failure the app must survive. Nothing about the body is assumed. */
    fun status(tx: Transaction, code: Int): MockRule =
        rule(tx, "$code", respond = Respond(status = code, headers = contentTypeHeader(tx), body = ""))

    fun timeout(tx: Transaction): MockRule = rule(tx, "timeout", fail = Fail(FailType.TIMEOUT))

    fun reset(tx: Transaction): MockRule = rule(tx, "reset", fail = Fail(FailType.CONNECTION_RESET))

    /** The captured response, delayed. Loading states stay on screen long enough to inspect. */
    fun slow(tx: Transaction, delayMs: Long): MockRule =
        rule(tx, "slow", respond = Respond(status = tx.responseCode ?: 200, headers = contentTypeHeader(tx), body = capturedBody(tx), delayMs = delayMs))

    /**
     * A response written by hand on the device: status and body are whatever the tester typed (the sheet
     * pre-fills them from the capture); everything else about the call stays locked.
     */
    fun custom(tx: Transaction, status: Int, body: String): MockRule =
        rule(tx, "custom", respond = Respond(status = status, headers = contentTypeHeader(tx), body = body))

    /** What the sheet pre-fills the Custom editor with: the captured text body, or an empty string. */
    fun capturedBody(tx: Transaction): String = when (val b = tx.responseBody) {
        is Body.Text -> b.text
        is Body.Truncated -> b.text
        else -> ""
    }

    /**
     * Effect label for a rule row: what the app will actually see. `500`, `empty []`, `+5.0 s`, `500 · +2.0 s`,
     * `timeout`, `reset`, `malformed`, `empty body`, `sequence · 3 steps`.
     */
    fun describe(rule: MockRule): String {
        if (rule.sequence.isNotEmpty()) return "sequence · ${rule.sequence.size} steps"
        rule.fail?.let { return failLabel(it.type) }
        val respond = rule.respond ?: return "—"
        val body = respond.body?.trim().orEmpty()
        val emptyShape = body == "[]" || body == "{}" || body == "null"
        val parts = ArrayList<String>(3)
        if (respond.status != 200 || (!emptyShape && respond.delayMs == 0L)) parts += respond.status.toString()
        if (emptyShape) parts += "empty $body"
        if (respond.delayMs > 0) parts += "+" + Labels.duration(respond.delayMs)
        return parts.joinToString(" · ")
    }

    fun failLabel(type: FailType): String = when (type) {
        FailType.TIMEOUT -> "timeout"
        FailType.CONNECTION_RESET -> "reset"
        FailType.MALFORMED_BODY -> "malformed"
        FailType.EMPTY_BODY -> "empty body"
    }

    private fun rule(tx: Transaction, effect: String, respond: Respond? = null, fail: Fail? = null): MockRule =
        MockRule(
            id = ruleId(tx),
            name = "${tx.method} ${tx.path} → $effect",
            enabled = true,
            priority = PRIORITY,
            match = Match(method = tx.method, path = tx.path),
            respond = respond,
            fail = fail,
        )

    private fun contentTypeHeader(tx: Transaction): Map<String, String> {
        val type = tx.responseHeaders.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value?.firstOrNull()
        return mapOf("Content-Type" to (type ?: "application/json"))
    }

    private val nonSlug = Regex("[^a-z0-9]+")

    private fun slug(path: String): String = path.lowercase().replace(nonSlug, "-").trim('-').ifEmpty { "root" }
}
