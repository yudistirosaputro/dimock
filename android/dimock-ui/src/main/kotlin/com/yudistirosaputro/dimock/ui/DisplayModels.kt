package com.yudistirosaputro.dimock.ui

import androidx.compose.ui.graphics.Color
import com.yudistirosaputro.dimock.core.engine.BodyFormat
import com.yudistirosaputro.dimock.core.engine.BodyView
import com.yudistirosaputro.dimock.core.engine.CurlFormat
import com.yudistirosaputro.dimock.core.engine.Labels
import com.yudistirosaputro.dimock.core.engine.LocalPresets
import com.yudistirosaputro.dimock.core.engine.Redactor
import com.yudistirosaputro.dimock.core.engine.RuleEntry
import com.yudistirosaputro.dimock.core.model.MockRule
import com.yudistirosaputro.dimock.core.model.Transaction
import com.yudistirosaputro.dimock.ui.theme.DimockColors

/** One fully pre-computed Traffic row: the composable only places text and colour. */
data class TrafficRow(
    val id: String,
    val method: String,
    val methodColor: Color,
    val status: String,
    val statusColor: Color,
    val statusClass: Int?,
    val path: String,
    val secondary: String,
    val secondaryColor: Color,
    val duration: String,
    val mocked: Boolean,
    val failed: Boolean,
) {
    companion object {
        fun from(tx: Transaction, ruleName: String?): TrafficRow {
            val failed = tx.error != null || tx.responseCode == null
            val failure = Labels.failure(tx.error)
            return TrafficRow(
                id = tx.id,
                method = tx.method.uppercase(),
                methodColor = DimockColors.forMethod(tx.method),
                status = Labels.status(tx),
                statusColor = if (failed) DimockColors.Status5xx else DimockColors.forStatus(tx.responseCode),
                statusClass = tx.responseCode?.div(100),
                path = tx.path,
                secondary = when {
                    tx.mocked -> ruleName ?: tx.mockRuleId ?: tx.host
                    failure != null -> failure
                    else -> tx.host
                },
                secondaryColor = when {
                    tx.mocked -> DimockColors.TextMuted
                    failure != null -> DimockColors.Status5xx
                    else -> DimockColors.TextDim
                },
                duration = Labels.duration(tx.durationMs),
                mocked = tx.mocked,
                failed = failed,
            )
        }
    }
}

/** One pre-computed Mocks row. */
data class RuleRow(
    val id: String,
    val title: String,
    val method: String,
    val path: String,
    val effect: String,
    val effectColor: Color,
    val meta: List<String>,
    val enabled: Boolean,
    val spent: Boolean,
    val local: Boolean,
) {
    companion object {
        fun from(entry: RuleEntry, now: Long): RuleRow {
            val rule = entry.rule
            val state = entry.state
            val effect = LocalPresets.describe(rule)
            val meta = ArrayList<String>(5)
            meta += if (LocalPresets.isLocal(rule)) "local" else "agent"
            if (rule.priority != 0) meta += "prio ${rule.priority}"
            if (rule.times != null) meta += if (state.spent) "0 of ${rule.times} left" else "${state.remaining} of ${rule.times} left"
            if (rule.sequence.isNotEmpty()) meta += "step ${(state.sequenceIndex + 1).coerceAtMost(rule.sequence.size)}/${rule.sequence.size} next"
            if (state.hits > 0) meta += Labels.plural(state.hits, "hit")
            state.lastHitAt?.let { meta += "last ${Labels.ago(it, now)}" }
            return RuleRow(
                id = rule.id,
                title = rule.name ?: rule.id,
                method = rule.match.method?.uppercase() ?: "ANY",
                path = rule.match.path ?: (rule.match.host ?: "*"),
                effect = effect,
                effectColor = when {
                    rule.fail != null -> DimockColors.Status5xx
                    (rule.respond?.status ?: 200) >= 500 -> DimockColors.Status5xx
                    (rule.respond?.status ?: 200) >= 400 -> DimockColors.Status4xx
                    else -> DimockColors.Text
                },
                meta = meta,
                enabled = rule.enabled,
                spent = state.spent,
                local = LocalPresets.isLocal(rule),
            )
        }
    }
}

/** One `name value` line of a HEADERS section; redacted values render dim. */
data class HeaderLine(val name: String, val value: String, val redacted: Boolean)

/** Headers plus body for one side of the exchange. */
data class SectionUi(val headers: List<HeaderLine>, val body: BodyView)

/** Everything the Detail screen shows, derived once from a [Transaction]. */
data class DetailUi(
    val tx: Transaction,
    val method: String,
    val url: String,
    val query: String?,
    val status: String,
    val statusColor: Color,
    val transport: String,
    val duration: String,
    val meta: String,
    val time: String,
    val failure: String?,
    val mockRuleName: String?,
    val mockRuleId: String?,
    val injectedDelay: String?,
    val request: SectionUi,
    val response: SectionUi,
    val curl: String,
    val redactedHeaders: List<String>,
    val truncationNote: String?,
) {
    companion object {
        fun from(tx: Transaction, rule: MockRule?, maxBodyBytes: Int): DetailUi {
            val ruleName = rule?.name
            val delay = rule?.respond?.delayMs?.takeIf { it > 0 } ?: rule?.fail?.delayMs?.takeIf { it > 0 }
            val failed = tx.error != null || tx.responseCode == null
            val response = BodyFormat.view(tx.responseBody)
            val contentType = BodyFormat.contentType(tx.responseHeaders)
            val size = Labels.bodyBytes(tx.responseBody)
            val meta = listOfNotNull(contentType, if (size > 0) Labels.bytes(size) else null, tx.host).joinToString(" · ")
            val truncated = (response as? BodyView.Text)?.truncatedAt != null || (BodyFormat.view(tx.requestBody) as? BodyView.Text)?.truncatedAt != null
            return DetailUi(
                tx = tx,
                method = tx.method.uppercase(),
                url = tx.url.substringBefore('?'),
                query = tx.url.substringAfter('?', "").ifEmpty { null }?.let { "?$it" },
                status = Labels.status(tx),
                statusColor = if (failed) DimockColors.Status5xx else DimockColors.forStatus(tx.responseCode),
                transport = Labels.transport(tx.responseCode, tx.error),
                duration = Labels.duration(tx.durationMs),
                meta = meta,
                time = Labels.clock(tx.startedAt),
                failure = Labels.failure(tx.error),
                mockRuleName = if (tx.mocked) ruleName ?: tx.mockRuleId else null,
                mockRuleId = tx.mockRuleId,
                injectedDelay = delay?.let(Labels::duration),
                request = SectionUi(headerLines(tx.requestHeaders), BodyFormat.view(tx.requestBody)),
                response = SectionUi(headerLines(tx.responseHeaders), response),
                curl = CurlFormat.format(tx),
                redactedHeaders = CurlFormat.redactedHeaders(tx),
                truncationNote = if (truncated) "… truncated at ${Labels.bytes(maxBodyBytes.toLong())}" else null,
            )
        }

        private fun headerLines(headers: Map<String, List<String>>): List<HeaderLine> =
            headers.entries.sortedBy { it.key.lowercase() }.flatMap { (name, values) ->
                values.map { HeaderLine(name, it, it == Redactor.MASK) }
            }
    }
}
