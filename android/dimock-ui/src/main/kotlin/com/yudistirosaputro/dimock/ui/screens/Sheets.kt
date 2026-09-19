package com.yudistirosaputro.dimock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.yudistirosaputro.dimock.core.engine.Labels
import com.yudistirosaputro.dimock.core.engine.LocalPresets
import com.yudistirosaputro.dimock.core.engine.Preset
import com.yudistirosaputro.dimock.ui.DetailUi
import com.yudistirosaputro.dimock.ui.theme.DimockColors
import com.yudistirosaputro.dimock.ui.theme.DimockDimens
import com.yudistirosaputro.dimock.ui.theme.DimockType

/** What the sheet hands back: the preset plus the parameters it may carry. */
data class ForceState(val preset: Preset, val status: Int = 500, val delayMs: Long = 5_000, val body: String = "")

/**
 * Screen 3 (docs/prd.md §7.2): one-tap presets on a locked method + path, plus a Custom response editor whose
 * status and body are pre-filled from the capture. Path, method and headers are not editable here by design;
 * glob paths, `times` and sequences are authored by the agent or the CLI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForceStateSheet(ui: DetailUi, onApply: (ForceState) -> Unit, onDismiss: () -> Unit) {
    var status by remember { mutableStateOf(500) }
    var delay by remember { mutableStateOf(LocalPresets.SLOW_DELAYS_MS[1]) }
    var customOpen by remember { mutableStateOf(false) }
    var customStatus by remember { mutableStateOf((ui.tx.responseCode ?: 200).toString()) }
    var customBody by remember { mutableStateOf(LocalPresets.capturedBody(ui.tx)) }
    val customStatusValid = customStatus.toIntOrNull()?.let { it in 100..599 } == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DimockColors.SurfaceRaised,
        contentColor = DimockColors.Text,
        shape = RoundedCornerShape(topStart = DimockDimens.RADIUS_SHEET_DP.dp, topEnd = DimockDimens.RADIUS_SHEET_DP.dp),
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = DimockDimens.GUTTER_DP.dp).navigationBarsPadding()) {
            Text("Force a state", style = DimockType.SheetTitle, color = DimockColors.Text)
            Spacer(Modifier.height(6.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = DimockColors.Text)) { append(ui.method); append(' '); append(ui.tx.path) }
                    withStyle(SpanStyle(color = DimockColors.TextDim)) { append(" · exact path, same method") }
                },
                style = DimockType.MonoSmall,
                color = DimockColors.TextMuted,
            )
            Spacer(Modifier.height(16.dp))
            Hairline(DimockColors.Hairline)

            PresetRow(Preset.STATUS, onApply = { onApply(ForceState(Preset.STATUS, status = status)) }) {
                ChipRow(LocalPresets.STATUS_CHIPS.map { it.toString() }, selected = status.toString()) { status = it.toInt() }
            }
            PresetRow(Preset.TIMEOUT, onApply = { onApply(ForceState(Preset.TIMEOUT)) })
            PresetRow(Preset.RESET, onApply = { onApply(ForceState(Preset.RESET)) })
            PresetRow(Preset.SLOW, onApply = { onApply(ForceState(Preset.SLOW, delayMs = delay)) }) {
                ChipRow(LocalPresets.SLOW_DELAYS_MS.map(Labels::duration), selected = Labels.duration(delay)) { s -> delay = LocalPresets.SLOW_DELAYS_MS.first { Labels.duration(it) == s } }
            }
            CustomRow(
                open = customOpen,
                status = customStatus,
                body = customBody,
                statusValid = customStatusValid,
                onToggle = { customOpen = !customOpen },
                onStatus = { customStatus = it.filter(Char::isDigit).take(3) },
                onBody = { customBody = it },
                onReset = { customStatus = (ui.tx.responseCode ?: 200).toString(); customBody = LocalPresets.capturedBody(ui.tx) },
                onApply = { onApply(ForceState(Preset.CUSTOM, status = customStatus.toInt(), body = customBody)) },
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "One rule per endpoint: applying another option here replaces this one. Glob paths, header matches, times and sequences are authored by your agent or the CLI.",
                style = DimockType.Caption,
                color = DimockColors.TextDim,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PresetRow(preset: Preset, onApply: () -> Unit, last: Boolean = false, chips: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(preset.title, style = DimockType.BodyStrong, color = DimockColors.Text)
            Text(preset.description, style = DimockType.Caption, color = DimockColors.TextMuted)
            if (chips != null) {
                Spacer(Modifier.height(6.dp))
                chips()
            }
        }
        OutlinedAction("Apply", color = DimockColors.Accent, onClick = onApply)
    }
    if (!last) Hairline()
}

/** The one editable option: response status + body, pre-filled from the capture. Collapsed until tapped. */
@Composable
private fun CustomRow(
    open: Boolean,
    status: String,
    body: String,
    statusValid: Boolean,
    onToggle: () -> Unit,
    onStatus: (String) -> Unit,
    onBody: (String) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Preset.CUSTOM.title, style = DimockType.BodyStrong, color = DimockColors.Text)
                Text(Preset.CUSTOM.description, style = DimockType.Caption, color = DimockColors.TextMuted)
            }
            if (open) OutlinedAction("Apply", color = DimockColors.Accent, enabled = statusValid, onClick = onApply)
            else OutlinedAction("Edit", onClick = onToggle)
        }
        if (!open) return@Column
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("STATUS")
            Box(
                Modifier
                    .width(88.dp)
                    .height(40.dp)
                    .background(DimockColors.Surface, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
                    .border(1.dp, if (statusValid) DimockColors.Hairline else DimockColors.Status5xx, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = status,
                    onValueChange = onStatus,
                    singleLine = true,
                    textStyle = DimockType.MonoRow.copy(color = DimockColors.forStatus(status.toIntOrNull())),
                    cursorBrush = SolidColor(DimockColors.Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Text(Labels.transport(status.toIntOrNull(), null).takeIf { statusValid } ?: "100–599", style = DimockType.Caption, color = DimockColors.TextDim)
            Spacer(Modifier.weight(1f))
            TextAction("Reset", color = DimockColors.TextMuted, onClick = onReset)
        }
        SectionLabel("BODY")
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 280.dp)
                .background(DimockColors.Surface, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
                .border(1.dp, DimockColors.Hairline, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
                .padding(12.dp),
        ) {
            if (body.isEmpty()) Text("(empty body)", style = DimockType.MonoCode, color = DimockColors.TextDim)
            BasicTextField(
                value = body,
                onValueChange = onBody,
                textStyle = DimockType.MonoCode.copy(color = DimockColors.Text),
                cursorBrush = SolidColor(DimockColors.Accent),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            )
        }
        Text("Headers and content type stay as captured. Body is sent verbatim — JSON is not validated.", style = DimockType.Caption, color = DimockColors.TextDim)
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { Chip(it, it == selected) { onSelect(it) } }
    }
}

/**
 * Screen 4 (docs/prd.md §7.2): the call as a `curl` command. Redacted headers are exported as `«redacted»` —
 * the raw token was never stored, so nothing here can leak it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurlSheet(ui: DetailUi, onCopy: (String) -> Unit, onShare: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DimockColors.SurfaceRaised,
        contentColor = DimockColors.Text,
        shape = RoundedCornerShape(topStart = DimockDimens.RADIUS_SHEET_DP.dp, topEnd = DimockDimens.RADIUS_SHEET_DP.dp),
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.padding(horizontal = DimockDimens.GUTTER_DP.dp).navigationBarsPadding()) {
            Text("Share as cURL", style = DimockType.SheetTitle, color = DimockColors.Text)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(DimockColors.Surface, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
                    .border(1.dp, DimockColors.Hairline, RoundedCornerShape(DimockDimens.RADIUS_DP.dp))
                    .padding(14.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                Text(ui.curl, style = DimockType.MonoCode, color = DimockColors.Text, softWrap = false)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (ui.redactedHeaders.isEmpty()) {
                    "Replays straight into a terminal or Postman. No credentials were on this call."
                } else {
                    "${ui.redactedHeaders.joinToString(", ")} ${if (ui.redactedHeaders.size == 1) "is" else "are"} exported as «redacted» — dimock never stores the raw value. Paste your own before replaying."
                },
                style = DimockType.Caption,
                color = DimockColors.TextMuted,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Copy", Modifier.weight(1f)) { onCopy(ui.curl) }
                AccentButton("Share", Modifier.weight(1f)) { onShare(ui.curl) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 36.dp, height = 3.dp).background(DimockColors.Control, RoundedCornerShape(2.dp)))
    }
}
