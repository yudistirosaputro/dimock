package com.yudistirosaputro.dimock.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
            modifier = Modifier.padding(horizontal = DimockDimens.GUTTER_DP.dp).padding(bottom = 16.dp),
        )
        Hairline(DimockTheme.colors.hairline)
        if (state.rules.isEmpty()) {
            EmptyState("No mocks yet", "Open a call and tap Mock this, or push rules from your agent.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.rules, key = { it.id }) { row ->
                    RuleRowItem(row, highlighted = row.id == highlightId, onToggle = { onToggle(row.id, it) }, onReset = { onReset(row.id) }, onRemove = { onRemove(row.id) })
                }
            }
        }
    }
}

@Composable
private fun Header(state: InspectorState, onSetAll: (Boolean) -> Unit, onClearAll: () -> Unit) {
    Column(Modifier.padding(start = DimockDimens.GUTTER_DP.dp, end = 12.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                color = if (state.activeMocks > 0) DimockTheme.colors.accentText else DimockTheme.colors.textMuted,
            )
            Text("rules active", style = DimockType.Body, color = DimockTheme.colors.textMuted, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(0.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RuleRowItem(row: RuleRow, highlighted: Boolean, onToggle: (Boolean) -> Unit, onReset: () -> Unit, onRemove: () -> Unit) {
    val marker = when {
        row.spent -> DimockTheme.colors.status4xx
        row.enabled -> DimockTheme.colors.accentText
        else -> DimockTheme.colors.hairline
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = DimockDimens.ROW_HEIGHT_DP.dp)
            .combinedClickable(onClick = { if (!row.spent) onToggle(!row.enabled) }, onLongClick = onRemove),
    ) {
        MarkerColumn(mocked = true, color = if (highlighted) DimockTheme.colors.text else marker)
        Column(
            Modifier.weight(1f).padding(start = 17.dp, end = DimockDimens.GUTTER_DP.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.title, style = DimockType.BodyStrong, color = if (row.enabled || row.spent) DimockTheme.colors.text else DimockTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = DimockTheme.colors.textDim)) { append(row.method); append(' ') }
                            append(row.path)
                            withStyle(SpanStyle(color = DimockTheme.colors.textDim)) { append("  →  ") }
                            withStyle(SpanStyle(color = DimockTheme.colors.forTone(row.effectTone))) { append(row.effect) }
                        },
                        style = DimockType.MonoSmall.copy(fontSize = DimockType.MonoRow.fontSize),
                        color = DimockTheme.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (row.spent) OutlinedAction("Reset", color = DimockTheme.colors.status4xx, onClick = onReset) else DimockSwitch(row.enabled, onToggle)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.meta.forEach { part ->
                    Text(part, style = DimockType.MonoSmall, color = if (row.spent && part.startsWith("0 of")) DimockTheme.colors.status4xx else DimockTheme.colors.textDim)
                }
            }
        }
    }
    Hairline()
}
