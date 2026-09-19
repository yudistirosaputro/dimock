package com.yudistirosaputro.dimock.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yudistirosaputro.dimock.core.engine.BodyFormat
import com.yudistirosaputro.dimock.core.engine.BodyView
import com.yudistirosaputro.dimock.core.engine.Labels
import com.yudistirosaputro.dimock.ui.DetailUi
import com.yudistirosaputro.dimock.ui.HeaderLine
import com.yudistirosaputro.dimock.ui.SectionUi
import com.yudistirosaputro.dimock.ui.theme.DimockDimens
import com.yudistirosaputro.dimock.ui.theme.DimockTheme
import com.yudistirosaputro.dimock.ui.theme.DimockType

private enum class Side { REQUEST, RESPONSE }

/**
 * Screen 2 (docs/prd.md §7.2): summary hero, mock / failure banner, Request | Response tabs, HEADERS and BODY
 * sections with search, fixed action row (cURL · Mock this). Everything shown is pre-computed in [DetailUi].
 */
@Composable
fun DetailScreen(
    ui: DetailUi,
    onBack: () -> Unit,
    onOpenRule: (String) -> Unit,
    onCopy: (String) -> Unit,
    onCurl: () -> Unit,
    onMockThis: () -> Unit,
) {
    var side by rememberSaveable { mutableStateOf(Side.RESPONSE) }
    var query by rememberSaveable { mutableStateOf("") }
    var raw by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        TopBar(ui.time, onBack)
        Column(Modifier.weight(1f).verticalScroll(scroll)) {
            Hero(ui, onOpenRule)
            Tabs(side, ui) { side = it }
            val section = if (side == Side.REQUEST) ui.request else ui.response
            Section(section, query, raw, ui.truncationNote, onQuery = { query = it }, onRaw = { raw = !raw }, onCopy = onCopy)
            Spacer(Modifier.height(24.dp))
        }
        Actions(onCurl, onMockThis)
    }
}

@Composable
private fun TopBar(time: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(DimockDimens.TAP_TARGET_DP.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Text("←", style = DimockType.Body.copy(fontSize = 20.sp), color = DimockTheme.colors.text)
        }
        Spacer(Modifier.weight(1f))
        Text(time, style = DimockType.MonoSmall, color = DimockTheme.colors.textDim)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(DimockDimens.TAP_TARGET_DP.dp))
    }
}

@Composable
private fun Hero(ui: DetailUi, onOpenRule: (String) -> Unit) {
    Column(Modifier.padding(horizontal = DimockDimens.GUTTER_DP.dp).padding(top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(ui.status, style = DimockType.MonoStatusBig, color = DimockTheme.colors.forTone(ui.statusTone))
            Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(ui.duration, style = DimockType.MonoRow.copy(fontSize = 16.sp), color = DimockTheme.colors.text)
                Text(ui.transport, style = DimockType.Caption, color = DimockTheme.colors.textMuted)
            }
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = DimockTheme.colors.textMuted)) { append(ui.method); append(' ') }
                append(ui.url)
                ui.query?.let { withStyle(SpanStyle(color = DimockTheme.colors.textDim)) { append(it) } }
            },
            style = DimockType.MonoRow.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = DimockTheme.colors.text,
        )
        Text(ui.meta, style = DimockType.MonoSmall, color = DimockTheme.colors.textDim)
        ui.mockRuleName?.let { name ->
            Banner(
                text = "Served by mock rule $name",
                detail = listOfNotNull("never left the device", ui.injectedDelay?.let { "delayed $it" }).joinToString(" · "),
                color = DimockTheme.colors.accentText,
                trailing = "›",
                onClick = ui.mockRuleId?.let { id -> { onOpenRule(id) } },
            )
        }
        ui.failure?.let { Banner(text = it, color = DimockTheme.colors.status5xx, detail = if (ui.mockRuleName == null) "transport failure" else null) }
    }
    Hairline(DimockTheme.colors.hairline)
}

@Composable
private fun Tabs(side: Side, ui: DetailUi, onSide: (Side) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = DimockDimens.GUTTER_DP.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        TabLabel("Request", ui.request.headers.size, side == Side.REQUEST) { onSide(Side.REQUEST) }
        TabLabel("Response", ui.response.headers.size, side == Side.RESPONSE) { onSide(Side.RESPONSE) }
    }
    Hairline(DimockTheme.colors.hairline)
}

@Composable
private fun TabLabel(text: String, headers: Int, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.height(DimockDimens.TAP_TARGET_DP.dp).clickable(onClick = onClick)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, style = if (selected) DimockType.Label else DimockType.Body, color = if (selected) DimockTheme.colors.text else DimockTheme.colors.textMuted)
            Text("$headers", style = DimockType.SectionLabel, color = DimockTheme.colors.textDim)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) DimockTheme.colors.accentText else DimockTheme.colors.surface))
    }
}

@Composable
private fun Section(
    section: SectionUi,
    query: String,
    raw: Boolean,
    truncationNote: String?,
    onQuery: (String) -> Unit,
    onRaw: () -> Unit,
    onCopy: (String) -> Unit,
) {
    Column(Modifier.padding(horizontal = DimockDimens.GUTTER_DP.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("HEADERS")
        if (section.headers.isEmpty()) Text("(no headers)", style = DimockType.MonoSmall, color = DimockTheme.colors.textDim)
        section.headers.forEach { HeaderRow(it) }
        Spacer(Modifier.height(12.dp))
        BodyBlock(section.body, query, raw, truncationNote, onQuery, onRaw, onCopy)
    }
}

@Composable
private fun HeaderRow(line: HeaderLine) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(line.name, style = DimockType.MonoSmall, color = DimockTheme.colors.textMuted, modifier = Modifier.width(120.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(line.value, style = DimockType.MonoSmall, color = if (line.redacted) DimockTheme.colors.textDim else DimockTheme.colors.text)
    }
}

@Composable
private fun BodyBlock(
    body: BodyView,
    query: String,
    raw: Boolean,
    truncationNote: String?,
    onQuery: (String) -> Unit,
    onRaw: () -> Unit,
    onCopy: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("BODY")
        Spacer(Modifier.weight(1f))
        if (body is BodyView.Text) {
            Text(bodyMeta(body), style = DimockType.MonoSmall, color = DimockTheme.colors.textDim)
            Spacer(Modifier.width(8.dp))
            TextAction(if (raw) "Pretty" else "Raw", enabled = body.kind != BodyView.Text.Kind.PLAIN, onClick = onRaw)
            TextAction("Copy") { onCopy(body.raw) }
        }
    }
    when (body) {
        BodyView.None -> Placeholder("(no body)")
        is BodyView.Binary -> Placeholder("(binary body omitted · ${body.contentType ?: "unknown type"} · ${Labels.bytes(body.totalBytes)})")
        is BodyView.Text -> {
            val text = if (raw) body.raw else body.pretty
            val matches = remember(text, query) { BodyFormat.search(text, query) }
            var current by remember(matches) { mutableIntStateOf(0) }
            SearchField(query, hint = "Search in body", modifier = Modifier.fillMaxWidth(), onChange = onQuery) {
                if (query.isNotBlank()) {
                    Text(
                        if (matches.isEmpty()) "No matches" else "${current + 1}/${matches.size}",
                        style = DimockType.MonoSmall,
                        color = if (matches.isEmpty()) DimockTheme.colors.textDim else DimockTheme.colors.accentText,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                    Text("↑", style = DimockType.Body, color = DimockTheme.colors.textMuted, modifier = Modifier.clickable(enabled = matches.size > 1) { current = (current - 1 + matches.size) % matches.size }.padding(horizontal = 8.dp))
                    Text("↓", style = DimockType.Body, color = DimockTheme.colors.textMuted, modifier = Modifier.clickable(enabled = matches.size > 1) { current = (current + 1) % matches.size }.padding(start = 4.dp))
                }
            }
            CodeBlock(text, matches, current, query.trim().length)
            truncationNote?.let { Text(it, style = DimockType.MonoSmall, color = DimockTheme.colors.status4xx) }
        }
    }
}

private fun bodyMeta(body: BodyView.Text): String = listOfNotNull(body.contentType, Labels.bytes(body.totalBytes)).joinToString(" · ")

@Composable
private fun Placeholder(text: String) {
    Text(text, style = DimockType.MonoSmall, color = DimockTheme.colors.textDim, modifier = Modifier.padding(vertical = 8.dp))
}

/** Numbered, horizontally scrollable code box with search highlights; brings the current match into view. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CodeBlock(text: String, matches: List<Int>, current: Int, needleLength: Int) {
    val shown = remember(text) { if (text.length > MAX_RENDERED_CHARS) text.take(MAX_RENDERED_CHARS) else text }
    val lineCount = remember(shown) { shown.count { it == '\n' } + 1 }
    val colors = DimockTheme.colors
    val annotated = remember(shown, matches, current, needleLength, colors) {
        highlight(shown, matches, current, needleLength, colors.accent, colors.onAccent, colors.control)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val requester = remember { BringIntoViewRequester() }

    LaunchedEffect(current, matches, layout) {
        val result = layout ?: return@LaunchedEffect
        val offset = matches.getOrNull(current) ?: return@LaunchedEffect
        if (offset >= shown.length) return@LaunchedEffect
        requester.bringIntoView(result.getBoundingBox(offset).inflate(MATCH_MARGIN_PX))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(DimockTheme.colors.surfaceRaised, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .border(1.dp, DimockTheme.colors.hairlineSoft, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .padding(vertical = 12.dp),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Text(
                (1..lineCount).joinToString("\n"),
                style = DimockType.MonoCode,
                color = DimockTheme.colors.textDim,
                modifier = Modifier.padding(end = 14.dp),
            )
            Text(
                annotated,
                style = DimockType.MonoCode,
                color = DimockTheme.colors.text,
                softWrap = false,
                onTextLayout = { layout = it },
                modifier = Modifier.bringIntoViewRequester(requester),
            )
        }
        if (shown.length < text.length) {
            Text("Showing the first ${Labels.bytes(MAX_RENDERED_CHARS.toLong())} · Copy for the full body", style = DimockType.MonoSmall, color = DimockTheme.colors.status4xx, modifier = Modifier.padding(start = 14.dp, top = 8.dp))
        }
    }
}

/** The current match is the one accent fill on this screen, so it carries `onAccent` ink; the rest sit on `control`. */
private fun highlight(
    text: String,
    matches: List<Int>,
    current: Int,
    needleLength: Int,
    accent: Color,
    onAccent: Color,
    control: Color,
): AnnotatedString {
    if (matches.isEmpty() || needleLength == 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        matches.forEachIndexed { i, start ->
            val end = (start + needleLength).coerceAtMost(text.length)
            if (start >= text.length) return@forEachIndexed
            val style = if (i == current) SpanStyle(background = accent, color = onAccent, fontWeight = FontWeight.SemiBold) else SpanStyle(background = control)
            addStyle(style, start, end)
        }
    }
}

@Composable
private fun Actions(onCurl: () -> Unit, onMockThis: () -> Unit) {
    Column(Modifier.background(DimockTheme.colors.surface).navigationBarsPadding()) {
        Hairline(DimockTheme.colors.hairline)
        Row(Modifier.fillMaxWidth().padding(horizontal = DimockDimens.GUTTER_DP.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("cURL", Modifier.weight(1f), onClick = onCurl)
            AccentButton("Mock this", Modifier.weight(1f), onClick = onMockThis)
        }
    }
}

private const val MAX_RENDERED_CHARS = 200 * 1024
private const val MATCH_MARGIN_PX = 160f
