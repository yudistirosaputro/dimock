package com.yudistirosaputro.dimock.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yudistirosaputro.dimock.core.engine.LocalPresets
import com.yudistirosaputro.dimock.core.engine.RuleEntry
import com.yudistirosaputro.dimock.core.model.Match
import com.yudistirosaputro.dimock.core.model.MockRule
import com.yudistirosaputro.dimock.core.model.Respond
import com.yudistirosaputro.dimock.core.model.RuleState
import com.yudistirosaputro.dimock.ui.InspectorState
import com.yudistirosaputro.dimock.ui.RuleRow
import com.yudistirosaputro.dimock.ui.theme.DimockDimens
import com.yudistirosaputro.dimock.ui.theme.DimockTheme
import com.yudistirosaputro.dimock.ui.theme.DimockType

/**
 * Screen 5 (docs/prd.md §7.2): every rule on the device — armed here or pushed by the agent — with a switch,
 * a pre-computed effect (`500`, `empty []`, `timeout`, `+5.0 s`), origin and counters. Long-press removes.
 */
@Composable
fun MocksScreen(
    state: InspectorState,
    highlightId: String? = null,
    onToggle: (String, Boolean) -> Unit,
    onReset: (String) -> Unit,
    onRemove: (String) -> Unit,
    onSetAll: (Boolean) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Header(state, onSetAll, onClearAll)
        Text(
            "Rules armed on this device or pushed by your agent. Local rules win. Nothing here leaves the device.",
            style = DimockType.Caption,
            color = DimockTheme.colors.textDim,
            modifier = Modifier.padding(horizontal = DimockDimens.GUTTER_DP.dp).padding(top = 8.dp, bottom = 14.dp),
        )
        if (state.rules.isEmpty()) {
            EmptyState("No mocks yet", "Open a call and tap Mock this, or push rules from your agent.")
        } else {
            RuleList(state.rules, highlightId, onToggle, onReset, onRemove)
            Text(
                "Tap a row to toggle it, long-press to remove it.",
                style = DimockType.Caption,
                color = DimockTheme.colors.textDim,
                modifier = Modifier.padding(horizontal = DimockDimens.GUTTER_DP.dp).padding(top = 8.dp),
            )
            Spacer(Modifier.height(DimockDimens.BAR_CLEARANCE_DP.dp))
        }
    }
}

@Composable
private fun Header(state: InspectorState, onSetAll: (Boolean) -> Unit, onClearAll: () -> Unit) {
    Column(Modifier.padding(start = DimockDimens.GUTTER_DP.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Wordmark()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val anyEnabled = state.enabledMocks > 0
                OutlinedAction(if (anyEnabled) "Disable all" else "Enable all", enabled = state.rules.isNotEmpty()) { onSetAll(!anyEnabled) }
                OutlinedAction("Clear all", color = DimockTheme.colors.status5xx, enabled = state.rules.isNotEmpty(), onClick = onClearAll)
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                buildAnnotatedString {
                    append("${state.activeMocks}")
                    withStyle(SpanStyle(color = DimockTheme.colors.control)) { append("/${state.rules.size}") }
                },
                style = DimockType.HeaderCount,
                // Sand means "mocked": a count of zero has nothing mocked to point at, so it reads muted.
                color = if (state.activeMocks > 0) DimockTheme.colors.accentText else DimockTheme.colors.textMuted,
            )
            Text("rules active", style = DimockType.Body, color = DimockTheme.colors.textMuted, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

/**
 * The rules in one grouped card. It wraps its rows and stops at the space left above the caption, so a long list
 * scrolls inside the card while the caption and the bar clearance stay put below it.
 */
@Composable
private fun ColumnScope.RuleList(rows: List<RuleRow>, highlightId: String?, onToggle: (String, Boolean) -> Unit, onReset: (String) -> Unit, onRemove: (String) -> Unit) {
    GroupCard(Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = DimockDimens.INSET_DP.dp)) {
        LazyColumn {
            itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                if (index > 0) InsetDivider(start = 16.dp)
                RuleRowItem(row, highlighted = row.id == highlightId, onToggle = { onToggle(row.id, it) }, onReset = { onReset(row.id) }, onRemove = { onRemove(row.id) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RuleRowItem(row: RuleRow, highlighted: Boolean, onToggle: (Boolean) -> Unit, onReset: () -> Unit, onRemove: () -> Unit) {
    val colors = DimockTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = DimockDimens.ROW_HEIGHT_DP.dp)
            .then(if (highlighted) Modifier.background(colors.accentSoft) else Modifier)
            .combinedClickable(onClick = { if (!row.spent) onToggle(!row.enabled) }, onLongClick = onRemove)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(row.title, style = DimockType.BodyStrong, color = if (row.enabled || row.spent) colors.text else colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.textDim)) { append(row.method); append(' ') }
                    append(row.path)
                    withStyle(SpanStyle(color = colors.textDim)) { append(" → ") }
                    withStyle(SpanStyle(color = colors.forTone(row.effectTone))) { append(row.effect) }
                },
                style = DimockType.MonoSmall.copy(fontSize = 13.5.sp),
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildAnnotatedString {
                    row.meta.forEachIndexed { index, part ->
                        if (index > 0) append(" · ")
                        if (row.spent && part.startsWith("0 of")) withStyle(SpanStyle(color = colors.status4xx)) { append(part) } else append(part)
                    }
                },
                style = DimockType.MonoSmall.copy(fontSize = 11.5.sp),
                color = colors.textDim,
            )
        }
        if (row.spent) OutlinedAction("Reset", color = colors.status4xx, onClick = onReset) else DimockSwitch(row.enabled, onToggle)
    }
}

// ---- previews ---------------------------------------------------------------------------------------------

/** "Now" for the meta line: six seconds after the last capture, so `Labels.ago` reads the same every render. */
private const val PREVIEW_NOW = PREVIEW_T0 + 6_000

/**
 * All three origins at once — two rules armed from the sheet, one pushed by the agent with a `times` budget
 * left, one spent and offering Reset. That is the row set that proves the switch, the Reset action and the
 * counters still read correctly side by side.
 */
private val previewRuleRows = listOf(
    RuleRow.from(
        RuleEntry(
            LocalPresets.custom(
                previewTx("tx-05", PREVIEW_T0 + 4_300, 3, "GET", "/comments", query = "?postId=41", code = 200, responseBody = previewJson("[]")),
                status = 200,
                body = "[]",
            ),
            RuleState(hits = 1, lastHitAt = PREVIEW_T0 + 4_300),
            1,
        ),
        PREVIEW_NOW,
    ),
    RuleRow.from(
        RuleEntry(
            LocalPresets.slow(
                previewTx("tx-07", PREVIEW_T0 + 5_200, 5_004, "GET", "/posts/41", code = 200, responseBody = previewJson("""{"id":41}""")),
                5_000,
            ),
            RuleState(hits = 1, lastHitAt = PREVIEW_T0 + 5_200),
            2,
        ),
        PREVIEW_NOW,
    ),
    // A glob path and a `times` budget: things the in-app sheet deliberately cannot author.
    RuleRow.from(
        RuleEntry(
            MockRule(
                id = "agent:session-401",
                name = "Session token expired",
                priority = 10,
                match = Match(method = "POST", path = "/auth/session"),
                times = 3,
                respond = Respond(status = 401, headers = mapOf("Content-Type" to "application/json"), body = """{"error":"token_expired"}"""),
            ),
            RuleState(hits = 1, remaining = 2, lastHitAt = PREVIEW_T0 - 240_000),
            3,
        ),
        PREVIEW_NOW,
    ),
    RuleRow.from(
        RuleEntry(
            MockRule(
                id = "agent:albums-503-once",
                name = "Albums 503, once",
                enabled = false,
                priority = 10,
                match = Match(method = "GET", path = "/users/*/albums"),
                times = 1,
                respond = Respond(status = 503, body = """{"error":"upstream_unavailable"}"""),
            ),
            RuleState(hits = 1, remaining = 0, lastHitAt = PREVIEW_T0 - 900_000),
            4,
        ),
        PREVIEW_NOW,
    ),
)

@InspectorPreviews
@Composable
private fun MocksPopulatedPreview() = PreviewPanel {
    MocksScreen(
        state = InspectorState(app = PREVIEW_APP, rules = previewRuleRows, activeMocks = 3, enabledMocks = 3),
        onToggle = { _, _ -> },
        onReset = {},
        onRemove = {},
        onSetAll = {},
        onClearAll = {},
    )
}

/** No rules on the device: the count reads 0/0 and both bulk actions are disabled. */
@InspectorPreviews
@Composable
private fun MocksEmptyPreview() = PreviewPanel {
    MocksScreen(
        state = InspectorState(app = PREVIEW_APP),
        onToggle = { _, _ -> },
        onReset = {},
        onRemove = {},
        onSetAll = {},
        onClearAll = {},
    )
}
