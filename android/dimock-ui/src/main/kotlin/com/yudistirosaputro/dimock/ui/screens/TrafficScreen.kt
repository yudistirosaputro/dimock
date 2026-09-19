package com.yudistirosaputro.dimock.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yudistirosaputro.dimock.core.engine.Labels
import com.yudistirosaputro.dimock.ui.InspectorState
import com.yudistirosaputro.dimock.ui.TrafficFilters
import com.yudistirosaputro.dimock.ui.TrafficRow
import com.yudistirosaputro.dimock.ui.theme.DimockColors
import com.yudistirosaputro.dimock.ui.theme.DimockDimens
import com.yudistirosaputro.dimock.ui.theme.DimockType

/**
 * Screen 1 (docs/prd.md §7.2): status bar (Recording/Paused · N calls · Clear), path filter, filter chips,
 * live list of pre-computed rows, two empty states.
 */
@Composable
fun TrafficScreen(
    state: InspectorState,
    onRecording: (Boolean) -> Unit,
    onClear: () -> Unit,
    onSearch: (String) -> Unit,
    onFilters: (TrafficFilters) -> Unit,
    onOpen: (TrafficRow) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = DimockDimens.GUTTER_DP.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Wordmark(state.app)
        }
        StatusBar(state.recording, state.totalCalls, onRecording, onClear)
        Column(Modifier.padding(horizontal = DimockDimens.GUTTER_DP.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchField(state.search, hint = "Filter by path", modifier = Modifier.fillMaxWidth(), onChange = onSearch)
            FilterChips(state.filters, onFilters)
        }
        Spacer(Modifier.height(12.dp))
        Hairline(DimockColors.Hairline)
        when {
            state.rows.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.rows, key = { it.id }) { row -> TrafficRowItem(row) { onOpen(row) } }
            }
            state.totalCalls == 0 -> EmptyState("No traffic yet", "Use the app; every request shows up here live.")
            else -> EmptyState("No matching calls", "Clear the search or the filters to see everything captured.")
        }
    }
}

@Composable
private fun StatusBar(recording: Boolean, calls: Int, onRecording: (Boolean) -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = DimockDimens.GUTTER_DP.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.heightIn(min = DimockDimens.TAP_TARGET_DP.dp).clickable { onRecording(!recording) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecordingDot(recording)
            Text(if (recording) "Recording" else "Paused", style = DimockType.Label, color = if (recording) DimockColors.Text else DimockColors.Status4xx)
        }
        Spacer(Modifier.width(14.dp))
        Text(Labels.plural(calls, "call"), style = DimockType.MonoSmall, color = DimockColors.TextMuted, modifier = Modifier.weight(1f))
        OutlinedAction("Clear", enabled = calls > 0, onClick = onClear)
    }
}

@Composable
private fun RecordingDot(recording: Boolean) {
    val pulse = rememberInfiniteTransition(label = "rec")
    val alpha by pulse.animateFloat(1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "recAlpha")
    Box(
        Modifier
            .size(8.dp)
            .alpha(if (recording) alpha else 1f)
            .background(if (recording) DimockColors.Accent else DimockColors.TextDim, RoundedCornerShape(2.dp)),
    )
}

@Composable
private fun FilterChips(filters: TrafficFilters, onFilters: (TrafficFilters) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("All", filters.isEmpty) { onFilters(TrafficFilters()) }
        Chip("GET", "GET" in filters.methods) { onFilters(filters.toggleMethod("GET")) }
        Chip("POST", "POST" in filters.methods) { onFilters(filters.toggleMethod("POST")) }
        Chip("2xx", 2 in filters.statusClasses) { onFilters(filters.toggleStatusClass(2)) }
        Chip("4xx·5xx", 4 in filters.statusClasses && 5 in filters.statusClasses) {
            val both = 4 in filters.statusClasses && 5 in filters.statusClasses
            onFilters(filters.copy(statusClasses = if (both) filters.statusClasses - setOf(4, 5) else filters.statusClasses + setOf(4, 5)))
        }
        Chip("Mocked", filters.mockedOnly) { onFilters(filters.copy(mockedOnly = !filters.mockedOnly)) }
        Chip("Failed", filters.failedOnly) { onFilters(filters.copy(failedOnly = !filters.failedOnly)) }
    }
}

/** 68 dp row: accent edge when mocked · method · status · path + second line · duration. */
@Composable
private fun TrafficRowItem(row: TrafficRow, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = DimockDimens.ROW_HEIGHT_DP.dp).clickable(onClick = onClick)) {
        MarkerColumn(row.mocked)
        Row(
            Modifier.weight(1f).padding(start = 17.dp, end = DimockDimens.GUTTER_DP.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.width(46.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.status, style = DimockType.MonoRow.copy(fontWeight = FontWeight.SemiBold), color = row.statusColor)
                Text(row.method, style = DimockType.MockTag, color = row.methodColor)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.path, style = DimockType.MonoRow, color = DimockColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row.mocked) MockTag()
                    Text(row.secondary, style = DimockType.Caption, color = row.secondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(row.duration, style = DimockType.MonoSmall, color = DimockColors.TextMuted)
        }
    }
    Hairline()
}
