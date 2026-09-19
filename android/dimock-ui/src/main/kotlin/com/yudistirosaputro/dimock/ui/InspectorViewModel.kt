package com.yudistirosaputro.dimock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yudistirosaputro.dimock.core.DimockEngine
import com.yudistirosaputro.dimock.core.engine.ActivityEntry
import com.yudistirosaputro.dimock.core.engine.Event
import com.yudistirosaputro.dimock.core.engine.LocalPresets
import com.yudistirosaputro.dimock.core.engine.Preset
import com.yudistirosaputro.dimock.core.model.MockRule
import com.yudistirosaputro.dimock.core.model.Transaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Chip selection on Traffic. Empty groups mean "no filter"; groups AND together, members OR. */
data class TrafficFilters(
    val methods: Set<String> = emptySet(),
    val statusClasses: Set<Int> = emptySet(),
    val mockedOnly: Boolean = false,
    val failedOnly: Boolean = false,
) {
    val isEmpty: Boolean get() = methods.isEmpty() && statusClasses.isEmpty() && !mockedOnly && !failedOnly

    fun toggleMethod(m: String) = copy(methods = if (m in methods) methods - m else methods + m)
    fun toggleStatusClass(c: Int) = copy(statusClasses = if (c in statusClasses) statusClasses - c else statusClasses + c)

    fun matches(row: TrafficRow): Boolean {
        val methodOk = methods.isEmpty() || row.method in methods
        val statusOk = statusClasses.isEmpty() || (row.statusClass != null && row.statusClass in statusClasses)
        val mockedOk = !mockedOnly || row.mocked
        val failedOk = !failedOnly || row.failed
        return methodOk && statusOk && mockedOk && failedOk
    }
}

data class InspectorState(
    val app: String = "",
    val rows: List<TrafficRow> = emptyList(),
    val totalCalls: Int = 0,
    val recording: Boolean = true,
    val filters: TrafficFilters = TrafficFilters(),
    val search: String = "",
    val rules: List<RuleRow> = emptyList(),
    val activeMocks: Int = 0,
    val enabledMocks: Int = 0,
    val activity: List<ActivityEntry> = emptyList(),
    val agentConnected: Boolean = false,
    val agentName: String? = null,
    val agentWriteEnabled: Boolean = true,
    val port: Int = DimockEngine.DEFAULT_PORT,
    val snackbar: String? = null,
)

/** Bridges the engine's listeners into one StateFlow the Compose screens observe; every row is pre-computed. */
class InspectorViewModel(private val engine: DimockEngine, private val port: Int) : ViewModel() {

    private val _state = MutableStateFlow(InspectorState(app = engine.host.app, port = port))
    val state: StateFlow<InspectorState> = _state.asStateFlow()

    /** While paused the list is frozen on this snapshot; captures keep landing in the engine. */
    private var frozen: List<Transaction>? = null

    private val onTransaction: (Transaction) -> Unit = { refresh() }
    private val onRules: () -> Unit = { refresh() }
    private val unsubscribeEvents = engine.events.subscribe { event ->
        if (event.type == Event.AGENT_ACTIVITY || event.type == Event.CLIENT_CONNECTED) refresh()
        if (event.type == Event.RULES_CHANGED) {
            val latest = engine.activity.list(1).firstOrNull()
            if (latest != null && latest.kind == ActivityEntry.Kind.WRITE && latest.call.startsWith("P")) showSnackbar("Agent: ${latest.summary}")
        }
    }

    init {
        engine.captures.addListener(onTransaction)
        engine.rules.addListener(onRules)
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(5_000) // connection state and "ago" labels depend on time passing, not on an event
                refresh()
            }
        }
    }

    // ---- Traffic --------------------------------------------------------------------------------------------

    fun setFilters(filters: TrafficFilters) { _state.update { it.copy(filters = filters) }; refresh() }
    fun setSearch(text: String) { _state.update { it.copy(search = text) }; refresh() }

    fun setRecording(on: Boolean) {
        frozen = if (on) null else engine.captures.list()
        _state.update { it.copy(recording = on) }
        refresh()
    }

    fun clearCaptures() {
        engine.captures.clear()
        if (frozen != null) frozen = emptyList()
        refresh()
    }

    fun transaction(id: String): Transaction? = engine.captures.get(id)

    fun detail(id: String): DetailUi? = engine.captures.get(id)?.let { tx ->
        DetailUi.from(tx, engine.rules.get(tx.mockRuleId ?: "")?.rule, engine.config.maxBodyBytes)
    }

    // ---- Mocks ----------------------------------------------------------------------------------------------

    fun setRuleEnabled(id: String, enabled: Boolean) { engine.rules.setEnabled(id, enabled) }
    fun resetRule(id: String) { engine.rules.reset(id) }
    fun setAllEnabled(enabled: Boolean) { engine.rules.entries().forEach { engine.rules.setEnabled(it.rule.id, enabled) } }

    fun removeRule(id: String) {
        val name = engine.rules.get(id)?.rule?.let { it.name ?: it.id } ?: return
        engine.rules.remove(id)
        engine.logActivity(ActivityEntry.Kind.WRITE, "Removed rule $name", "local")
        showSnackbar("Removed $name")
    }

    fun clearRules() {
        engine.rules.clear()
        engine.logActivity(ActivityEntry.Kind.WRITE, "Cleared all rules", "local")
        showSnackbar("All mocks removed")
    }

    /**
     * The "Force a state" sheet. Method and path come from the capture and are not editable; one rule per
     * endpoint, so applying a second preset to the same call replaces the first.
     */
    fun forceState(tx: Transaction, preset: Preset, status: Int = 500, delayMs: Long = 5_000, body: String = ""): MockRule {
        val rule = when (preset) {
            Preset.STATUS -> LocalPresets.status(tx, status)
            Preset.TIMEOUT -> LocalPresets.timeout(tx)
            Preset.RESET -> LocalPresets.reset(tx)
            Preset.SLOW -> LocalPresets.slow(tx, delayMs)
            Preset.CUSTOM -> LocalPresets.custom(tx, status, body)
        }
        engine.rules.upsert(rule)
        engine.logActivity(ActivityEntry.Kind.WRITE, "Forced ${LocalPresets.describe(rule)} on ${tx.method} ${tx.path}", "local")
        showSnackbar("Mocked · ${LocalPresets.describe(rule)}")
        return rule
    }

    // ---- Agent ----------------------------------------------------------------------------------------------

    fun setAgentWriteEnabled(enabled: Boolean) { engine.agentWriteEnabled = enabled; refresh() }
    fun clearActivity() { engine.activity.clear(); refresh() }

    fun showSnackbar(text: String) = _state.update { it.copy(snackbar = text) }
    fun snackbarShown() = _state.update { it.copy(snackbar = null) }

    private fun refresh() {
        val current = _state.value
        val all = frozen ?: engine.captures.list()
        val needle = current.search.trim()
        val rows = all.asSequence()
            .filter { needle.isEmpty() || it.path.contains(needle, ignoreCase = true) }
            .map { TrafficRow.from(it, engine.rules.get(it.mockRuleId ?: "")?.rule?.name) }
            .filter { current.filters.matches(it) }
            .toList()
        val now = engine.config.clock()
        val entries = engine.rules.entries()
        _state.update {
            it.copy(
                rows = rows,
                totalCalls = engine.captures.count(),
                rules = entries.map { e -> RuleRow.from(e, now) },
                activeMocks = engine.rules.activeCount(),
                enabledMocks = entries.count { e -> e.rule.enabled },
                activity = engine.activity.list(50),
                agentConnected = engine.clientConnected,
                agentName = engine.connectedClientName,
                agentWriteEnabled = engine.agentWriteEnabled,
            )
        }
    }

    override fun onCleared() {
        engine.captures.removeListener(onTransaction)
        engine.rules.removeListener(onRules)
        unsubscribeEvents()
    }

    class Factory(private val engine: DimockEngine, private val port: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = InspectorViewModel(engine, port) as T
    }
}
