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
import com.yudistirosaputro.dimock.ui.theme.DimockDimens
import com.yudistirosaputro.dimock.ui.theme.DimockTheme
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
        Hairline(DimockTheme.colors.hairline)
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
            Text(if (recording) "Recording" else "Paused", style = DimockType.Label, color = if (recording) DimockTheme.colors.text else DimockTheme.colors.status4xx)
        }
        Spacer(Modifier.width(14.dp))
        Text(Labels.plural(calls, "call"), style = DimockType.MonoSmall, color = DimockTheme.colors.textMuted, modifier = Modifier.weight(1f))
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
            .background(if (recording) DimockTheme.colors.accentText else DimockTheme.colors.textDim, RoundedCornerShape(2.dp)),
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
    val colors = DimockTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = DimockDimens.ROW_HEIGHT_DP.dp).clickable(onClick = onClick)) {
        MarkerColumn(row.mocked)
        Row(
            Modifier.weight(1f).padding(start = 17.dp, end = DimockDimens.GUTTER_DP.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.width(46.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.status, style = DimockType.MonoRow.copy(fontWeight = FontWeight.SemiBold), color = colors.forTone(row.statusTone))
                Text(row.method, style = DimockType.MockTag, color = colors.forTone(row.methodTone))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.path, style = DimockType.MonoRow, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row.mocked) MockTag()
                    Text(row.secondary, style = DimockType.Caption, color = colors.forTone(row.secondaryTone), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(row.duration, style = DimockType.MonoSmall, color = colors.textMuted)
        }
    }
    Hairline()
}

// ---- previews ---------------------------------------------------------------------------------------------

/**
 * One afternoon of traffic from a small reading app, frozen so a preview never shifts between renders: a plain
 * read, a write, a 404, a struggling upstream, a call answered on the device in 3 ms, and a socket that died.
 * Newest first, the way the capture store hands them over.
 */
private val previewTrafficRows = listOf(
    TrafficRow.from(
        previewTx(
            "tx-06", PREVIEW_T0 + 4_900, 8_412, "GET", "/photos/8912/thumbnail.png",
            code = null,
            responseHeaders = emptyMap(),
            error = "java.net.SocketException: Connection reset by peer",
        ),
        null,
    ),
    TrafficRow.from(
        previewTx(
            "tx-05", PREVIEW_T0 + 4_300, 3, "GET", "/comments", query = "?postId=41",
            code = 200,
            responseBody = previewJson("[]"),
            mocked = true,
            mockRuleId = "local:get-comments",
        ),
        "GET /comments → empty []",
    ),
    TrafficRow.from(
        previewTx(
            "tx-04", PREVIEW_T0 + 2_400, 1_842, "GET", "/users/7/albums",
            code = 500,
            responseBody = previewJson("""{"error":"upstream_unavailable","requestId":"b4f1c0a2","retryAfter":30}"""),
        ),
        null,
    ),
    TrafficRow.from(
        previewTx(
            "tx-03", PREVIEW_T0 + 2_050, 112, "GET", "/posts/9001",
            code = 404,
            responseBody = previewJson("""{"error":"not_found","resource":"post","id":9001}"""),
        ),
        null,
    ),
    TrafficRow.from(
        previewTx(
            "tx-02", PREVIEW_T0 + 1_100, 388, "POST", "/comments",
            code = 201,
            requestBody = previewJson("""{"postId":41,"body":"Does the 07:40 still call at Lerwick?"}"""),
            responseBody = previewJson("""{"id":501,"userId":3,"postId":41,"createdAt":"2026-09-19T09:41:07Z"}"""),
        ),
        null,
    ),
    TrafficRow.from(
        previewTx(
            "tx-01", PREVIEW_T0, 241, "GET", "/posts",
            code = 200,
            responseBody = previewJson("""[{"userId":3,"id":41,"title":"Winter ferry timetable changes"},{"userId":3,"id":42,"title":"Reading room closed for rewiring"}]"""),
        ),
        null,
    ),
)

private val previewTrafficState = InspectorState(app = PREVIEW_APP, rows = previewTrafficRows, totalCalls = previewTrafficRows.size)

/**
 * The callbacks are empty on purpose — a preview shows a state, it does not drive one. What the state looks
 * like in the other theme comes from the multipreview, never from a palette named here.
 */
@InspectorPreviews
@Composable
private fun TrafficPopulatedPreview() = PreviewPanel {
    TrafficScreen(state = previewTrafficState, onRecording = {}, onClear = {}, onSearch = {}, onFilters = {}, onOpen = {})
}

/** Nothing captured yet: "No traffic yet", and Clear is disabled because there is nothing to clear. */
@InspectorPreviews
@Composable
private fun TrafficEmptyPreview() = PreviewPanel {
    TrafficScreen(
        state = InspectorState(app = PREVIEW_APP),
        onRecording = {},
        onClear = {},
        onSearch = {},
        onFilters = {},
        onOpen = {},
    )
}

/** Captures exist but the path filter and the POST chip exclude every one: "No matching calls". */
@InspectorPreviews
@Composable
private fun TrafficNoMatchesPreview() = PreviewPanel {
    TrafficScreen(
        state = previewTrafficState.copy(
            rows = emptyList(),
            search = "/checkout",
            filters = TrafficFilters(methods = setOf("POST")),
        ),
        onRecording = {},
        onClear = {},
        onSearch = {},
        onFilters = {},
        onOpen = {},
    )
}

/** Paused: the dot stops pulsing, the label turns amber, and the frozen list lags the call counter. */
@InspectorPreviews
@Composable
private fun TrafficPausedPreview() = PreviewPanel {
    TrafficScreen(
        state = previewTrafficState.copy(rows = previewTrafficRows.drop(2), recording = false),
        onRecording = {},
        onClear = {},
        onSearch = {},
        onFilters = {},
        onOpen = {},
    )
}
