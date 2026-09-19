package com.yudistirosaputro.dimock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yudistirosaputro.dimock.core.engine.ActivityEntry
import com.yudistirosaputro.dimock.ui.InspectorState
import com.yudistirosaputro.dimock.ui.theme.DimockDimens
import com.yudistirosaputro.dimock.ui.theme.DimockTheme
import com.yudistirosaputro.dimock.ui.theme.DimockType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AgentScreen(
    state: InspectorState,
    onAgentWrite: (Boolean) -> Unit,
    onClearActivity: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val connectCommand = "npx dimock connect --app ${state.app}"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = DimockDimens.GUTTER_DP.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Wordmark()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.agentConnected) Box(Modifier.size(10.dp).background(DimockTheme.colors.accentText, CircleShape))
                    else Box(Modifier.size(10.dp).border(2.dp, DimockTheme.colors.textDim, CircleShape))
                    Text(if (state.agentConnected) "Agent connected" else "No agent connected", style = DimockType.ScreenTitle, color = DimockTheme.colors.text)
                }
                Text(
                    if (state.agentConnected) "${state.agentName ?: "client"} · adb forward tcp:${state.port}"
                    else "listening on 127.0.0.1:${state.port} · rules kept on device",
                    style = DimockType.MonoCode, color = DimockTheme.colors.textMuted,
                )
            }
        }

        if (state.agentConnected || state.activity.isNotEmpty()) {
            ConnectedBody(state, connectCommand, onAgentWrite, onClearActivity, onCopy)
        } else {
            OnboardingBody(state, connectCommand, onCopy)
        }
    }
}

@Composable
private fun ConnectedBody(state: InspectorState, connectCommand: String, onAgentWrite: (Boolean) -> Unit, onClearActivity: () -> Unit, onCopy: (String) -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        Column(Modifier.padding(horizontal = DimockDimens.GUTTER_DP.dp)) {
            Hairline(DimockTheme.colors.hairline)
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Agent may change mock rules", style = DimockType.BodyStrong.copy(fontSize = DimockType.Body.fontSize), color = DimockTheme.colors.text)
                    Text("Off = read-only: captures and logs only", style = DimockType.Caption, color = DimockTheme.colors.textMuted)
                }
                DimockSwitch(checked = state.agentWriteEnabled, onCheckedChange = onAgentWrite)
            }
            Hairline(DimockTheme.colors.hairline)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = DimockDimens.GUTTER_DP.dp).padding(top = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Agent activity", style = DimockType.BodyStrong.copy(fontSize = DimockType.Body.fontSize), color = DimockTheme.colors.text)
            Text("Clear", style = DimockType.Label, color = DimockTheme.colors.accentText, modifier = Modifier.clickable(onClick = onClearActivity).padding(8.dp))
        }
        if (state.activity.isEmpty()) {
            Text("Nothing yet. Every wire call that reads or changes state lands here.", style = DimockType.Body, color = DimockTheme.colors.textDim, modifier = Modifier.padding(horizontal = DimockDimens.GUTTER_DP.dp, vertical = 10.dp))
        }
        state.activity.forEach { entry -> ActivityRow(entry) }
        Hairline()

        Column(Modifier.padding(DimockDimens.GUTTER_DP.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Connect another agent from your machine", style = DimockType.Label.copy(fontWeight = FontWeight.Normal), color = DimockTheme.colors.textMuted)
            CommandBox(connectCommand) { onCopy(connectCommand) }
        }

        Column(Modifier.padding(horizontal = DimockDimens.GUTTER_DP.dp).padding(bottom = 24.dp)) {
            Hairline()
            KeyValueRow("Redaction", "defaults + config", valueColor = DimockTheme.colors.textMuted)
            KeyValueRow("Capture retention", "ring buffer", valueColor = DimockTheme.colors.textMuted)
            KeyValueRow("Wire protocol", "v${com.yudistirosaputro.dimock.core.DimockEngine.PROTOCOL_VERSION} · loopback only", valueColor = DimockTheme.colors.textMuted, last = true)
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    Column {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(horizontal = DimockDimens.GUTTER_DP.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(SimpleDateFormat("HH:mm", Locale.US).format(Date(entry.at)), style = DimockType.MonoSmall, color = DimockTheme.colors.textDim, modifier = Modifier.width(44.dp).padding(top = 2.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.summary, style = DimockType.Body, color = DimockTheme.colors.text)
                    if (entry.kind == ActivityEntry.Kind.WRITE && entry.summary.startsWith("Pushed")) MockTag()
                }
                Text(entry.call, style = DimockType.MonoSmall, color = DimockTheme.colors.textMuted)
            }
        }
    }
}

@Composable
private fun OnboardingBody(state: InspectorState, connectCommand: String, onCopy: (String) -> Unit) {
    val install = "claude plugin marketplace add yudistirosaputro/dimock"
    Column(Modifier.padding(horizontal = DimockDimens.GUTTER_DP.dp).padding(top = 28.dp, bottom = 24.dp)) {
        Step("01", "Install the plugin in Claude Code") {
            CommandBox(install) { onCopy(install) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cursor or Codex:", style = DimockType.Caption, color = DimockTheme.colors.textMuted)
                Text("npx dimock init", style = DimockType.MonoSmall, color = DimockTheme.colors.textMuted)
            }
        }
        Step("02", "Plug in the device and connect") {
            CommandBox(connectCommand) { onCopy(connectCommand) }
        }
        Step("03", "Ask the agent to mock a screen", last = true) {
            Text(
                "“Capture the posts call, then show me the empty and error states.” This tab turns into the connection log once it answers.",
                style = DimockType.Label.copy(fontWeight = FontWeight.Normal), color = DimockTheme.colors.textMuted,
            )
        }
        Text(
            "Nothing here leaves the device. The server binds to localhost and is reachable only through adb forward. Release builds ship the no-op artifact.",
            style = DimockType.Caption, color = DimockTheme.colors.textDim, modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
private fun Step(number: String, title: String, last: Boolean = false, content: @Composable () -> Unit) {
    Hairline(DimockTheme.colors.hairline)
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(number, style = DimockType.MonoCode, color = DimockTheme.colors.accentText, modifier = Modifier.width(24.dp).padding(top = 3.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = DimockType.BodyStrong, color = DimockTheme.colors.text)
            content()
        }
    }
    if (last) Hairline(DimockTheme.colors.hairline)
}
