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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yudistirosaputro.dimock.ui.theme.DimockColors
import com.yudistirosaputro.dimock.ui.theme.DimockDimens
import com.yudistirosaputro.dimock.ui.theme.DimockType

/** Shared pieces that make every screen read as one instrument panel. */

@Composable
fun Wordmark(appId: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).background(DimockColors.Accent, RoundedCornerShape(2.dp)))
        Text("dimock", style = DimockType.Wordmark, color = DimockColors.Text)
        if (appId != null) Text(appId, style = DimockType.MonoSmall, color = DimockColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Hairline(color: Color = DimockColors.HairlineSoft) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun MockTag() {
    Text("MOCK", style = DimockType.MockTag, color = DimockColors.Accent)
}

/** Rectangular switch, 44x24, knob 18. Accent when on, control grey when off. */
@Composable
fun DimockSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val track = if (checked) DimockColors.Accent else DimockColors.SurfaceRaised
    Box(
        Modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .background(track)
            .then(if (checked) Modifier else Modifier.border(2.dp, DimockColors.TextDim, RoundedCornerShape(DimockDimens.RADIUS_DP.dp)))
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(18.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .background(if (checked) DimockColors.OnAccent else DimockColors.TextDim, RoundedCornerShape(3.dp)),
        )
    }
}

/** Small bordered text action: `Clear`, `Disable all`, `Reset`. */
@Composable
fun OutlinedAction(text: String, color: Color = DimockColors.Text, enabled: Boolean = true, onClick: () -> Unit) {
    val border = if (color == DimockColors.Text) DimockColors.Control else color
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .border(1.dp, if (enabled) border else DimockColors.HairlineSoft, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DimockType.Label, color = if (enabled) color else DimockColors.TextDim) }
}

/** Text-only action, accent coloured: `Copy`, `Raw`, `Clear all`. */
@Composable
fun TextAction(text: String, color: Color = DimockColors.Accent, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = DimockDimens.TAP_TARGET_DP.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DimockType.Label, color = if (enabled) color else DimockColors.TextDim) }
}

@Composable
fun AccentButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .background(if (enabled) DimockColors.Accent else DimockColors.Control)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DimockType.Label.copy(fontSize = 15.sp), color = DimockColors.OnAccent) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .border(1.dp, DimockColors.Control, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DimockType.Label, color = DimockColors.Text) }
}

/** Filter chip: hairline when idle, accent outline + text when selected. 36 dp tall inside a 44 dp tap area. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .border(1.dp, if (selected) DimockColors.Accent else DimockColors.Control, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .background(if (selected) DimockColors.Accent.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DimockType.MonoSmall.copy(fontSize = 13.sp), color = if (selected) DimockColors.Accent else DimockColors.TextMuted) }
}

/** Single-line filter/search field in the raised surface, hairline border, no M3 decoration. */
@Composable
fun SearchField(value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .heightIn(min = DimockDimens.TAP_TARGET_DP.dp)
            .background(DimockColors.SurfaceRaised, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .border(1.dp, DimockColors.HairlineSoft, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, style = DimockType.MonoRow, color = DimockColors.TextDim)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = DimockType.MonoRow.copy(color = DimockColors.Text),
                cursorBrush = SolidColor(DimockColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) Text("×", style = DimockType.Body.copy(fontSize = 18.sp), color = DimockColors.TextMuted, modifier = Modifier.clickable { onChange("") }.padding(start = 10.dp))
        trailing?.invoke()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = DimockType.SectionLabel, color = DimockColors.TextDim)
}

@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color = DimockColors.Text, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = DimockType.Body, color = DimockColors.TextMuted)
        Text(value, style = DimockType.MonoSmall, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (!last) Hairline()
}

@Composable
fun CommandBox(command: String, onCopy: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(DimockColors.SurfaceRaised, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .border(1.dp, DimockColors.Hairline, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("$ ", style = DimockType.MonoCode, color = DimockColors.TextDim)
        Text(command, style = DimockType.MonoCode, color = DimockColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("Copy", style = DimockType.Label, color = DimockColors.Accent, modifier = Modifier.clickable(onClick = onCopy))
    }
}

/** Two-line empty state, left-aligned like everything else on the panel. */
@Composable
fun EmptyState(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(DimockDimens.GUTTER_DP.dp)) {
        Spacer(Modifier.height(32.dp))
        Text(title, style = DimockType.BodyStrong, color = DimockColors.TextMuted)
        Spacer(Modifier.height(6.dp))
        Text(message, style = DimockType.Body, color = DimockColors.TextDim)
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
            Text(text, style = DimockType.Label, color = DimockColors.Text)
            if (detail != null) Text(detail, style = DimockType.Caption, color = DimockColors.TextMuted)
        }
        if (trailing != null) Text(trailing, style = DimockType.Label, color = color)
    }
}

@Composable
fun MarkerColumn(mocked: Boolean, color: Color = DimockColors.Accent) {
    Box(Modifier.width(DimockDimens.MARKER_WIDTH_DP.dp).fillMaxHeight().background(if (mocked) color else Color.Transparent))
}
