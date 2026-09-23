package com.yudistirosaputro.dimock.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.yudistirosaputro.dimock.core.engine.LocalPresets
import com.yudistirosaputro.dimock.core.model.Body
import com.yudistirosaputro.dimock.ui.DetailUi
import com.yudistirosaputro.dimock.ui.HeaderLine
import com.yudistirosaputro.dimock.ui.SectionUi
import com.yudistirosaputro.dimock.ui.theme.DimockDimens
import com.yudistirosaputro.dimock.ui.theme.DimockTheme
import com.yudistirosaputro.dimock.ui.theme.DimockType

private enum class Side { REQUEST, RESPONSE }

/**
 * Screen 2 (docs/prd.md §7.2): summary hero, mock / failure banner, Request | Response segmented control,
 * HEADERS and BODY sections with search, fixed action row (cURL · Mock this). Everything shown is pre-computed
 * in [DetailUi].
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
            SegmentedControl(
                options = listOf("Request", "Response"),
                selected = if (side == Side.REQUEST) 0 else 1,
                modifier = Modifier.padding(horizontal = DimockDimens.GUTTER_DP.dp),
                counts = listOf(ui.request.headers.size.toString(), ui.response.headers.size.toString()),
                onSelect = { side = if (it == 0) Side.REQUEST else Side.RESPONSE },
            )
            val section = if (side == Side.REQUEST) ui.request else ui.response
            Section(section, query, raw, ui.truncationNote, onQuery = { query = it }, onRaw = { raw = !raw }, onCopy = onCopy)
            Spacer(Modifier.height(24.dp))
        }
        Actions(onCurl, onMockThis)
    }
}

@Composable
private fun TopBar(time: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(DimockDimens.TAP_TARGET_DP.dp).clip(controlShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DimockTheme.colors.text, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(time, style = DimockType.MonoSmall, color = DimockTheme.colors.textDim)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(DimockDimens.TAP_TARGET_DP.dp))
    }
}

/** The one display moment on the panel. Nothing separates it from the segmented control below but space. */
@Composable
private fun Hero(ui: DetailUi, onOpenRule: (String) -> Unit) {
    val colors = DimockTheme.colors
    Column(
        Modifier.padding(horizontal = DimockDimens.GUTTER_DP.dp).padding(top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(ui.status, style = DimockType.StatusBig, color = colors.forTone(ui.statusTone))
            Column(Modifier.padding(bottom = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(ui.duration, style = DimockType.MonoRow.copy(fontSize = 16.sp), color = colors.text)
                Text(ui.transport, style = DimockType.Caption, color = colors.textMuted)
            }
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.textMuted)) { append(ui.method); append(' ') }
                append(ui.url)
                ui.query?.let { withStyle(SpanStyle(color = colors.textDim)) { append(it) } }
            },
            style = DimockType.MonoRow.copy(fontSize = 14.5.sp, lineHeight = 22.sp),
            color = colors.text,
        )
        Text(ui.meta, style = DimockType.MonoSmall, color = colors.textDim)
        ui.mockRuleName?.let { name ->
            Banner(
                text = "Served by mock rule $name",
                detail = listOfNotNull("never left the device", ui.injectedDelay?.let { "delayed $it" }).joinToString(" · "),
                color = colors.accent,
                trailing = "›",
                onClick = ui.mockRuleId?.let { id -> { onOpenRule(id) } },
            )
        }
        ui.failure?.let { Banner(text = it, color = colors.status5xx, detail = if (ui.mockRuleName == null) "transport failure" else null) }
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
        Spacer(Modifier.height(10.dp))
        BodyBlock(section.body, query, raw, truncationNote, onQuery, onRaw, onCopy)
    }
}

@Composable
private fun HeaderRow(line: HeaderLine) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(line.name, style = DimockType.MonoSmall, color = DimockTheme.colors.textMuted, modifier = Modifier.width(HEADER_KEY_WIDTH_DP.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(line.value, style = DimockType.MonoSmall, color = if (line.redacted) DimockTheme.colors.textDim else DimockTheme.colors.text, modifier = Modifier.weight(1f))
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
            // TextAction pads 8 dp either side; pull the last one out so "Copy" ends on the gutter edge like the field below.
            Box(Modifier.offset(x = 8.dp)) { TextAction("Copy") { onCopy(body.raw) } }
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
            CodeBlock(text, body.kind == BodyView.Text.Kind.JSON, matches, current, query.trim().length)
            truncationNote?.let { Text(it, style = DimockType.MonoSmall, color = DimockTheme.colors.status4xx) }
        }
    }
}

private fun bodyMeta(body: BodyView.Text): String = listOfNotNull(body.contentType, Labels.bytes(body.totalBytes)).joinToString(" · ")

@Composable
private fun Placeholder(text: String) {
    Text(text, style = DimockType.MonoSmall, color = DimockTheme.colors.textDim, modifier = Modifier.padding(vertical = 8.dp))
}

/**
 * Numbered, horizontally scrollable code well with search highlights; brings the current match into view.
 * Hit backgrounds are drawn behind the text rather than as span backgrounds so they can carry a 3 dp radius.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CodeBlock(text: String, json: Boolean, matches: List<Int>, current: Int, needleLength: Int) {
    val shown = remember(text) { if (text.length > MAX_RENDERED_CHARS) text.take(MAX_RENDERED_CHARS) else text }
    val lineCount = remember(shown) { shown.count { it == '\n' } + 1 }
    val colors = DimockTheme.colors
    val annotated = remember(shown, json, matches, current, needleLength, colors) {
        annotate(shown, json && shown.length <= MAX_SYNTAX_CHARS, matches, current, needleLength, colors.textMuted, colors.accentText, colors.onAccent)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val requester = remember { BringIntoViewRequester() }

    LaunchedEffect(current, matches, layout) {
        val result = layout ?: return@LaunchedEffect
        val hit = matches.getOrNull(current) ?: return@LaunchedEffect
        if (hit >= minOf(shown.length, result.layoutInput.text.length)) return@LaunchedEffect
        requester.bringIntoView(result.getBoundingBox(hit).inflate(MATCH_MARGIN_PX))
    }

    Well(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                Text(
                    (1..lineCount).joinToString("\n"),
                    style = DimockType.MonoCode,
                    color = colors.textDim,
                    modifier = Modifier.padding(end = 14.dp),
                )
                Text(
                    annotated,
                    style = DimockType.MonoCode,
                    color = colors.text,
                    softWrap = false,
                    onTextLayout = { layout = it },
                    modifier = Modifier
                        .bringIntoViewRequester(requester)
                        .drawBehind {
                            val result = layout ?: return@drawBehind
                            if (needleLength == 0) return@drawBehind
                            // A layout from the previous body can outlive a Raw/Pretty flip by a frame; never index past it.
                            val limit = minOf(shown.length, result.layoutInput.text.length)
                            val radius = CornerRadius(HIT_RADIUS_DP.dp.toPx())
                            matches.forEachIndexed { i, start ->
                                val end = (start + needleLength).coerceAtMost(limit)
                                if (start >= end) return@forEachIndexed
                                // The needle never holds a newline and the text never wraps, so a hit sits on one line.
                                val first = result.getBoundingBox(start)
                                val last = result.getBoundingBox(end - 1)
                                drawRoundRect(
                                    color = if (i == current) colors.accent else colors.control,
                                    topLeft = Offset(first.left, first.top),
                                    size = Size(last.right - first.left, first.height),
                                    cornerRadius = radius,
                                )
                            }
                        },
                )
            }
            if (shown.length < text.length) {
                Text("Showing the first ${Labels.bytes(MAX_RENDERED_CHARS.toLong())} · Copy for the full body", style = DimockType.MonoSmall, color = colors.status4xx, modifier = Modifier.padding(start = 14.dp, top = 8.dp))
            }
        }
    }
}

/**
 * Syntax colour first (JSON keys muted, string values accent), then the hits on top: only the current one
 * changes its ink, to `onAccent` over the accent fill drawn behind it. Later spans win where they overlap.
 */
private fun annotate(
    text: String,
    json: Boolean,
    matches: List<Int>,
    current: Int,
    needleLength: Int,
    key: Color,
    string: Color,
    onAccent: Color,
): AnnotatedString {
    if (!json && (matches.isEmpty() || needleLength == 0)) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        if (json) styleJsonStrings(text, key, string)
        if (needleLength == 0) return@buildAnnotatedString
        val start = matches.getOrNull(current) ?: return@buildAnnotatedString
        val end = (start + needleLength).coerceAtMost(text.length)
        if (start < end) addStyle(SpanStyle(color = onAccent, fontWeight = FontWeight.SemiBold), start, end)
    }
}

/** Scans for `"..."` literals, honouring escapes: one followed by `:` is a key, any other is a string value. */
private fun AnnotatedString.Builder.styleJsonStrings(text: String, key: Color, string: Color) {
    val n = text.length
    var i = 0
    while (i < n) {
        if (text[i] != '"') { i++; continue }
        val start = i
        i++
        while (i < n && text[i] != '"') {
            if (text[i] == '\\') i++
            i++
        }
        val end = (i + 1).coerceAtMost(n)
        i = end
        var j = end
        while (j < n && (text[j] == ' ' || text[j] == '\t')) j++
        addStyle(SpanStyle(color = if (j < n && text[j] == ':') key else string), start, end)
    }
}

@Composable
private fun Actions(onCurl: () -> Unit, onMockThis: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(DimockTheme.colors.surface)
            .navigationBarsPadding()
            .padding(start = DimockDimens.GUTTER_DP.dp, end = DimockDimens.GUTTER_DP.dp, top = 12.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GhostButton("cURL", Modifier.weight(1f), onClick = onCurl)
        AccentButton("Mock this", Modifier.weight(1f), onClick = onMockThis)
    }
}

private const val HEADER_KEY_WIDTH_DP = 124
private const val HIT_RADIUS_DP = 3
private const val MAX_RENDERED_CHARS = 200 * 1024
/** Past this, a JSON body gets thousands of colour spans and the layout pass starts to drag; hits still work. */
private const val MAX_SYNTAX_CHARS = 64 * 1024
private const val MATCH_MARGIN_PX = 160f

// ---- previews ---------------------------------------------------------------------------------------------

/** The capture limit the engine ships with; what [DetailUi] measures a truncated body against. */
private const val PREVIEW_MAX_BODY_BYTES = 1024 * 1024

/** A 2.3 MB catalogue export the device cut at the body limit, mid-token, exactly as the ring buffer does. */
private val previewTruncatedBody = Body.Truncated(
    text = buildString {
        append("""{"generatedAt":"2026-09-19T09:38:00Z","total":18422,"charts":[""")
        repeat(6) { i ->
            append("""{"id":${9000 + i},"sheet":"NC-${412 + i}","title":"North coast approaches, sheet ${412 + i}","scale":75000},""")
        }
        append("""{"id":9006,"sheet":"NC-418","title":"North coast approaches, sh""")
    },
    totalBytes = 2_412_889,
    contentType = "application/json",
)

/** 200 · the plain read every other fixture is measured against. Carries a masked Authorization header. */
private val previewDetailOk = DetailUi.from(
    previewTx(
        "tx-01", PREVIEW_T0, 241, "GET", "/posts",
        code = 200,
        responseBody = previewJson("""{"userId":3,"id":41,"title":"Winter ferry timetable changes","body":"The 06:15 sailing is withdrawn from 2 November; the 07:40 runs daily.","tags":["transport","schedule"]}"""),
    ),
    null,
    PREVIEW_MAX_BODY_BYTES,
)

/** Served by a local rule, five seconds late: accent banner, the rule name, and the delay it injected. */
private val previewDetailMocked = run {
    val tx = previewTx(
        "tx-07", PREVIEW_T0 + 5_200, 5_004, "GET", "/posts/41",
        code = 200,
        responseBody = previewJson("""{"userId":3,"id":41,"title":"Winter ferry timetable changes","updatedAt":"2026-09-18T16:22:04Z"}"""),
        mocked = true,
        mockRuleId = "local:get-posts-41",
    )
    DetailUi.from(tx, LocalPresets.slow(tx, 5_000), PREVIEW_MAX_BODY_BYTES)
}

/** A real transport failure — nothing dimock did. No response at all, so the hero reads ERR in 5xx red. */
private val previewDetailFailed = DetailUi.from(
    previewTx(
        "tx-06", PREVIEW_T0 + 4_900, 8_412, "GET", "/photos/8912/thumbnail.png",
        code = null,
        requestHeaders = mapOf(
            "Accept" to listOf("image/webp,image/png,*/*"),
            "User-Agent" to listOf("Northwind/2.4.1 (Android 15; Pixel 8)"),
        ),
        responseHeaders = emptyMap(),
        error = "java.net.SocketException: Connection reset by peer",
    ),
    null,
    PREVIEW_MAX_BODY_BYTES,
)

/** Body cut at the capture limit: the note under the code block says where it stopped. */
private val previewDetailTruncated = DetailUi.from(
    previewTx("tx-08", PREVIEW_T0 + 5_600, 2_310, "GET", "/charts/export", code = 200, responseBody = previewTruncatedBody),
    null,
    PREVIEW_MAX_BODY_BYTES,
)

/** Binary body: metadata only, and no Raw/Pretty pair because there is nothing to format. */
private val previewDetailBinary = DetailUi.from(
    previewTx(
        "tx-09", PREVIEW_T0 + 5_800, 96, "GET", "/photos/8913/thumbnail.png",
        code = 200,
        requestHeaders = mapOf("Accept" to listOf("image/webp,image/png,*/*")),
        responseHeaders = mapOf("Content-Type" to listOf("image/png"), "ETag" to listOf("\"7c1f-59a2\"")),
        responseBody = Body.Binary(totalBytes = 1_248_204, contentType = "image/png"),
    ),
    null,
    PREVIEW_MAX_BODY_BYTES,
)

@InspectorPreviews
@Composable
private fun DetailOkPreview() = PreviewPanel {
    DetailScreen(ui = previewDetailOk, onBack = {}, onOpenRule = {}, onCopy = {}, onCurl = {}, onMockThis = {})
}

@InspectorPreviews
@Composable
private fun DetailMockedPreview() = PreviewPanel {
    DetailScreen(ui = previewDetailMocked, onBack = {}, onOpenRule = {}, onCopy = {}, onCurl = {}, onMockThis = {})
}

@InspectorPreviews
@Composable
private fun DetailTransportFailurePreview() = PreviewPanel {
    DetailScreen(ui = previewDetailFailed, onBack = {}, onOpenRule = {}, onCopy = {}, onCurl = {}, onMockThis = {})
}

@InspectorPreviews
@Composable
private fun DetailTruncatedBodyPreview() = PreviewPanel {
    DetailScreen(ui = previewDetailTruncated, onBack = {}, onOpenRule = {}, onCopy = {}, onCurl = {}, onMockThis = {})
}

@InspectorPreviews
@Composable
private fun DetailBinaryBodyPreview() = PreviewPanel {
    DetailScreen(ui = previewDetailBinary, onBack = {}, onOpenRule = {}, onCopy = {}, onCurl = {}, onMockThis = {})
}
