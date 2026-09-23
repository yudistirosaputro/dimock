package com.yudistirosaputro.dimock.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yudistirosaputro.dimock.core.engine.Redactor
import com.yudistirosaputro.dimock.core.model.Body
import com.yudistirosaputro.dimock.core.model.Transaction
import com.yudistirosaputro.dimock.ui.theme.DimockDimens
import com.yudistirosaputro.dimock.ui.theme.DimockTheme
import com.yudistirosaputro.dimock.ui.theme.DimockType

/**
 * Shared pieces that make every screen read as one instrument panel.
 *
 * Three radii, layered: [containerShape] for grouped lists and cards, [controlShape] for anything a finger
 * presses or types into, [tagShape] for badges and knobs. Nothing here is a capsule.
 */

val containerShape: Shape get() = RoundedCornerShape(DimockDimens.RADIUS_CONTAINER_DP.dp)
val controlShape: Shape get() = RoundedCornerShape(DimockDimens.RADIUS_CONTROL_DP.dp)
val tagShape: Shape get() = RoundedCornerShape(DimockDimens.RADIUS_TAG_DP.dp)

/** An accent-filled shape. Sand keeps its one meaning — mocked — in both themes, and clears AA as ink too, so no outline. */
@Composable
fun Modifier.accentFill(shape: Shape = controlShape): Modifier = background(DimockTheme.colors.accent, shape)

@Composable
fun Wordmark(appId: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(12.dp).background(DimockTheme.colors.accent, RoundedCornerShape(3.dp)))
        Text("dimock", style = DimockType.Wordmark, color = DimockTheme.colors.text)
        if (appId != null) Text(appId, style = DimockType.MonoSmall.copy(fontSize = 11.5.sp), color = DimockTheme.colors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Hairline(color: Color = DimockTheme.colors.hairlineSoft) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

/** The divider between rows of a grouped list: starts under the path, not at the card edge. */
@Composable
fun InsetDivider(start: Dp = DimockDimens.ROW_DIVIDER_INSET_DP.dp) {
    Box(Modifier.fillMaxWidth().padding(start = start).height(1.dp).background(DimockTheme.colors.hairlineSoft))
}

/**
 * A grouped list or card: raised surface, container radius, clipped so row backgrounds and dividers stop at the
 * corner. Inset [DimockDimens.INSET_DP] from the screen edge by the caller.
 */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(containerShape).background(DimockTheme.colors.surfaceRaised, containerShape)) { content() }
}

/** The MOCK badge: a filled sand tag with dark text. The only place the accent appears on a Traffic row. */
@Composable
fun MockTag() {
    Text(
        "MOCK",
        style = DimockType.MockTag,
        color = DimockTheme.colors.onAccent,
        modifier = Modifier.accentFill(tagShape).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Rounded-rectangle switch, 44x26, knob 20. Accent fill when on, an outlined well when off. Not a pill. */
@Composable
fun DimockSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = DimockTheme.colors
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(shape)
            .then(if (checked) Modifier.accentFill(shape) else Modifier.border(2.dp, colors.control, shape))
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(20.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .background(if (checked) colors.onAccent else colors.control, tagShape),
        )
    }
}

/** Small bordered text action: `Clear`, `Disable all`, `Reset`, `Apply`. 36 dp tall. */
@Composable
fun OutlinedAction(text: String, color: Color = DimockTheme.colors.text, enabled: Boolean = true, onClick: () -> Unit) {
    val border = if (color == DimockTheme.colors.text) DimockTheme.colors.control else color
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(controlShape)
            .border(1.dp, if (enabled) border else DimockTheme.colors.hairline, controlShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DimockType.Label, color = if (enabled) color else DimockTheme.colors.textDim) }
}

/** Text-only action, accent coloured: `Copy`, `Raw`, `Clear all`. */
@Composable
fun TextAction(text: String, color: Color = DimockTheme.colors.accentText, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = DimockDimens.TAP_TARGET_DP.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DimockType.Label, color = if (enabled) color else DimockTheme.colors.textDim) }
}

/** The primary button: sand fill, dark text, 48 dp. */
@Composable
fun AccentButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = DimockTheme.colors
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(controlShape)
            .then(if (enabled) Modifier.accentFill(controlShape) else Modifier.background(colors.control, controlShape))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DimockType.Label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = colors.onAccent) }
}

/** The secondary button beside an [AccentButton]: control-coloured outline, 48 dp. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(controlShape)
            .border(1.dp, DimockTheme.colors.control, controlShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DimockType.Label.copy(fontSize = 14.sp), color = DimockTheme.colors.text) }
}

/** Filter chip, 34 dp: control outline and muted mono text when idle; a solid sand fill with dark text when selected. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = DimockTheme.colors
    Box(
        Modifier
            .heightIn(min = 34.dp)
            .clip(controlShape)
            .then(if (selected) Modifier.accentFill(controlShape) else Modifier.border(1.dp, colors.control, controlShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DimockType.MonoSmall.copy(fontSize = 12.5.sp), color = if (selected) colors.onAccent else colors.textMuted) }
}

/** Single-line filter/search field: a sunken well with a hairline edge, 44 dp, no M3 decoration. */
@Composable
fun SearchField(value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .heightIn(min = DimockDimens.TAP_TARGET_DP.dp)
            .background(DimockTheme.colors.surfaceSunken, controlShape)
            .border(1.dp, DimockTheme.colors.hairline, controlShape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, style = DimockType.MonoRow, color = DimockTheme.colors.textDim)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = DimockType.MonoRow.copy(color = DimockTheme.colors.text),
                cursorBrush = SolidColor(DimockTheme.colors.accentText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) Text("×", style = DimockType.Body.copy(fontSize = 18.sp), color = DimockTheme.colors.textMuted, modifier = Modifier.clickable { onChange("") }.padding(start = 10.dp))
        trailing?.invoke()
    }
}

/**
 * A sunken well for a text editor or a code block: sunken surface, hairline edge, control radius. When
 * [focused], the edge turns sand and a soft ring sits outside it, so the editor being typed into is unmistakable.
 */
@Composable
fun Well(modifier: Modifier = Modifier, focused: Boolean = false, content: @Composable () -> Unit) {
    val colors = DimockTheme.colors
    Box(
        modifier
            // The ring is drawn outside the bounds, so focusing never resizes the well or re-wraps its text.
            .then(
                if (focused) Modifier.drawBehind {
                    val ring = 3.dp.toPx()
                    drawRoundRect(
                        color = colors.accentSoft,
                        topLeft = Offset(-ring, -ring),
                        size = Size(size.width + 2 * ring, size.height + 2 * ring),
                        cornerRadius = CornerRadius(DimockDimens.RADIUS_CONTROL_DP.dp.toPx() + ring),
                        style = Stroke(ring),
                    )
                } else Modifier,
            )
            .background(colors.surfaceSunken, controlShape)
            .border(1.dp, if (focused) colors.accent else colors.hairline, controlShape),
    ) { content() }
}

/**
 * Two or more labels in one sunken track; the selected one sits on a raised, shadowed pane. Replaces the
 * underline tabs of the old panel on Detail (Request | Response).
 */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, modifier: Modifier = Modifier, counts: List<String?>? = null, onSelect: (Int) -> Unit) {
    val colors = DimockTheme.colors
    val inner = RoundedCornerShape(DimockDimens.RADIUS_CONTROL_DP.dp - 3.dp)
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceSunken, controlShape)
            .border(1.dp, colors.hairline, controlShape)
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Row(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(inner)
                    .then(if (on) Modifier.shadow(1.dp, inner).background(colors.surfaceRaised, inner) else Modifier)
                    .clickable { onSelect(index) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = DimockType.Label.copy(fontSize = 13.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = if (on) colors.text else colors.textMuted)
                counts?.getOrNull(index)?.let { Text("  $it", style = DimockType.SectionLabel, color = colors.textDim) }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = DimockType.SectionLabel, color = DimockTheme.colors.textDim)
}

@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color = DimockTheme.colors.text, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = DimockType.Body, color = DimockTheme.colors.textMuted)
        Text(value, style = DimockType.MonoSmall, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (!last) Hairline()
}

@Composable
fun CommandBox(command: String, onCopy: () -> Unit) {
    Well(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("$ ", style = DimockType.MonoCode, color = DimockTheme.colors.textDim)
            Text(command, style = DimockType.MonoCode, color = DimockTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("Copy", style = DimockType.Label, color = DimockTheme.colors.accentText, modifier = Modifier.clickable(onClick = onCopy))
        }
    }
}

/** Two-line empty state, left-aligned like everything else on the panel. */
@Composable
fun EmptyState(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(DimockDimens.GUTTER_DP.dp)) {
        Spacer(Modifier.height(32.dp))
        Text(title, style = DimockType.BodyStrong, color = DimockTheme.colors.textMuted)
        Spacer(Modifier.height(6.dp))
        Text(message, style = DimockType.Body, color = DimockTheme.colors.textDim)
    }
}

/**
 * A soft notice: the colour at low alpha as the fill, an 8 dp dot of it at the start, no border. Accent for
 * "mocked", 5xx red for a failure, 4xx for a warning.
 */
@Composable
fun Banner(text: String, color: Color, detail: String? = null, trailing: String? = null, onClick: (() -> Unit)? = null) {
    val fill = if (color == DimockTheme.colors.accent || color == DimockTheme.colors.accentText) DimockTheme.colors.accentSoft else color.copy(alpha = 0.12f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(controlShape)
            .background(fill, controlShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(3.dp)))
        Column(Modifier.weight(1f)) {
            Text(text, style = DimockType.Label, color = DimockTheme.colors.text)
            if (detail != null) Text(detail, style = DimockType.Caption, color = DimockTheme.colors.textMuted)
        }
        if (trailing != null) Text(trailing, style = DimockType.Label, color = color)
    }
}

// ---- previews ---------------------------------------------------------------------------------------------
//
// Each screen keeps its own previews and its own fake data at the bottom of its own file. Only the pieces three
// or more of those files need live here, beside the atoms they already share. Everything below is `internal`:
// it compiles into the AAR, but none of it widens the library's public API.

/**
 * Every inspector preview renders twice, once per theme.
 *
 * The pair works because the chain is real, not decorative: the annotation sets `Configuration.uiMode`,
 * `isSystemInDarkTheme()` reads `LocalConfiguration` and tests `uiMode and UI_MODE_NIGHT_MASK ==
 * UI_MODE_NIGHT_YES`, and that is exactly the default of `DimockTheme(darkTheme = ...)`. Wrapping a preview
 * body in [PreviewPanel] is therefore enough — no preview ever names a palette.
 */
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
internal annotation class InspectorPreviews

/**
 * The theme plus the surface the Scaffold would otherwise paint. Nothing here belongs to a screen: a preview
 * needing more setup than this would be showing off its fixture rather than the screen.
 */
@Composable
internal fun PreviewPanel(content: @Composable () -> Unit) {
    DimockTheme {
        Box(Modifier.fillMaxSize().background(DimockTheme.colors.surface)) { content() }
    }
}

/** The app every preview pretends to be inspecting: a small reading app against a public JSON API. */
internal const val PREVIEW_APP = "com.northwind.reader"

/** 2026-09-19 09:41:07.418 UTC, frozen: `Labels.clock` and `Labels.ago` must not drift between renders. */
internal const val PREVIEW_T0 = 1_789_810_867_418L

private const val PREVIEW_HOST = "jsonplaceholder.typicode.com"

/** A call that carried a bearer token. The interceptor masked it before the capture was ever stored. */
private val PREVIEW_AUTHED_HEADERS = mapOf(
    "Accept" to listOf("application/json"),
    "Accept-Language" to listOf("en-GB"),
    "Authorization" to listOf(Redactor.MASK),
    "X-Request-Id" to listOf("b4f1c0a2-7d3e-4a19-9c55-2f8e0d61a774"),
)

private val PREVIEW_JSON_HEADERS = mapOf(
    "Content-Type" to listOf("application/json; charset=utf-8"),
    "Cache-Control" to listOf("max-age=43200"),
    "X-Powered-By" to listOf("Express"),
)

/** One capture, shaped the way the interceptor would have left it. Traffic, Detail and both sheets build on it. */
internal fun previewTx(
    id: String,
    at: Long,
    durationMs: Long?,
    method: String,
    path: String,
    query: String = "",
    code: Int?,
    requestHeaders: Map<String, List<String>> = PREVIEW_AUTHED_HEADERS,
    requestBody: Body? = null,
    responseHeaders: Map<String, List<String>> = PREVIEW_JSON_HEADERS,
    responseBody: Body? = null,
    error: String? = null,
    mocked: Boolean = false,
    mockRuleId: String? = null,
): Transaction = Transaction(
    id = id,
    startedAt = at,
    durationMs = durationMs,
    method = method,
    url = "https://$PREVIEW_HOST$path$query",
    host = PREVIEW_HOST,
    path = path,
    requestHeaders = requestHeaders,
    requestBody = requestBody,
    responseCode = code,
    responseHeaders = responseHeaders,
    responseBody = responseBody,
    error = error,
    mocked = mocked,
    mockRuleId = mockRuleId,
)

/** The body shape almost every fixture uses. */
internal fun previewJson(text: String): Body = Body.Text(text, "application/json")
