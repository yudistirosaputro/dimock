package com.yudistirosaputro.dimock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yudistirosaputro.dimock.ui.theme.DimockDimens
import com.yudistirosaputro.dimock.ui.theme.DimockTheme
import com.yudistirosaputro.dimock.ui.theme.DimockType

/** Shared pieces that make every screen read as one instrument panel. */

/**
 * An accent-filled shape. The lime keeps its one meaning — mocked — in both themes, but it is a fill and never
 * ink: against the near-white light surface it measures 1.18:1, so there the shape also gets a 1 px `accentText`
 * outline. Without it the chip floats with no edge at all.
 */
@Composable
fun Modifier.accentFill(shape: Shape = RoundedCornerShape(DimockDimens.RADIUS_DP.dp)): Modifier {
    val colors = DimockTheme.colors
    val needsOutline = colors.accent != colors.accentText
    return background(colors.accent, shape)
        .then(if (needsOutline) Modifier.border(DimockDimens.ACCENT_OUTLINE_DP.dp, colors.accentText, shape) else Modifier)
}

@Composable
fun Wordmark(appId: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).background(DimockTheme.colors.accentText, RoundedCornerShape(2.dp)))
        Text("dimock", style = DimockType.Wordmark, color = DimockTheme.colors.text)
        if (appId != null) Text(appId, style = DimockType.MonoSmall, color = DimockTheme.colors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Hairline(color: Color = DimockTheme.colors.hairlineSoft) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun MockTag() {
    Text(
        "MOCK",
        style = DimockType.MockTag,
        color = DimockTheme.colors.onAccent,
        modifier = Modifier.accentFill(RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** Rectangular switch, 44x24, knob 18. Accent fill when on, an outlined well when off. */
@Composable
fun DimockSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = DimockTheme.colors
    val shape = RoundedCornerShape(DimockDimens.RADIUS_DP.dp)
    Box(
        Modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(shape)
            .then(if (checked) Modifier.accentFill(shape) else Modifier.background(colors.surfaceRaised).border(2.dp, colors.textDim, shape))
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(18.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .background(if (checked) colors.onAccent else colors.textDim, RoundedCornerShape(3.dp)),
        )
    }
}

/** Small bordered text action: `Clear`, `Disable all`, `Reset`. */
@Composable
fun OutlinedAction(text: String, color: Color = DimockTheme.colors.text, enabled: Boolean = true, onClick: () -> Unit) {
    val border = if (color == DimockTheme.colors.text) DimockTheme.colors.control else color
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .border(1.dp, if (enabled) border else DimockTheme.colors.hairlineSoft, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
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

@Composable
fun AccentButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = DimockTheme.colors
    val shape = RoundedCornerShape(DimockDimens.RADIUS_DP.dp)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(if (enabled) Modifier.accentFill(shape) else Modifier.background(colors.control, shape))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DimockType.Label.copy(fontSize = 15.sp), color = colors.onAccent) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .border(1.dp, DimockTheme.colors.control, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DimockType.Label, color = DimockTheme.colors.text) }
}

/** Filter chip: hairline when idle, accent outline + text when selected. 36 dp tall inside a 44 dp tap area. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .border(1.dp, if (selected) DimockTheme.colors.accentText else DimockTheme.colors.control, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .background(if (selected) DimockTheme.colors.accentText.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DimockType.MonoSmall.copy(fontSize = 13.sp), color = if (selected) DimockTheme.colors.accentText else DimockTheme.colors.textMuted) }
}

/** Single-line filter/search field in the raised surface, hairline border, no M3 decoration. */
@Composable
fun SearchField(value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .heightIn(min = DimockDimens.TAP_TARGET_DP.dp)
            .background(DimockTheme.colors.surfaceRaised, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .border(1.dp, DimockTheme.colors.hairlineSoft, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
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
    Row(
        Modifier
            .fillMaxWidth()
            .background(DimockTheme.colors.surfaceRaised, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .border(1.dp, DimockTheme.colors.hairline, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("$ ", style = DimockType.MonoCode, color = DimockTheme.colors.textDim)
        Text(command, style = DimockType.MonoCode, color = DimockTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("Copy", style = DimockType.Label, color = DimockTheme.colors.accentText, modifier = Modifier.clickable(onClick = onCopy))
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

/** Outlined notice: accent for "mocked", 5xx red for a failure, amber for a warning. */
@Composable
fun Banner(text: String, color: Color, detail: String? = null, trailing: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, color, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f)) {
            Text(text, style = DimockType.Label, color = DimockTheme.colors.text)
            if (detail != null) Text(detail, style = DimockType.Caption, color = DimockTheme.colors.textMuted)
        }
        if (trailing != null) Text(trailing, style = DimockType.Label, color = color)
    }
}

@Composable
fun MarkerColumn(mocked: Boolean, color: Color = DimockTheme.colors.accentText) {
    Box(Modifier.width(DimockDimens.MARKER_WIDTH_DP.dp).fillMaxHeight().background(if (mocked) color else Color.Transparent))
}
