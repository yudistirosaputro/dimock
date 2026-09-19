package com.yudistirosaputro.dimock.core.engine

import com.yudistirosaputro.dimock.core.json.RuleCodec
import com.yudistirosaputro.dimock.core.model.MockRule
import com.yudistirosaputro.dimock.core.model.Outcome
import com.yudistirosaputro.dimock.core.model.RequestSnapshot
import com.yudistirosaputro.dimock.core.model.RuleState
import com.yudistirosaputro.dimock.core.model.Step
import java.util.concurrent.CopyOnWriteArrayList

/** Where the rule set is persisted. On device this is a file under `filesDir/dimock/`; tests use memory. */
interface RuleStorage {
    fun read(): String?
    fun write(json: String)
}

object NoStorage : RuleStorage {
    override fun read(): String? = null
    override fun write(json: String) = Unit
}

data class RuleEntry(val rule: MockRule, val state: RuleState, val addedSeq: Long)

/**
 * The on-device source of truth for mock rules and their runtime counters.
 * Thread-safe; every mutation persists through [storage] and notifies listeners.
 */
class RuleStore(private val storage: RuleStorage = NoStorage, private val clock: () -> Long = System::currentTimeMillis) {

    private val lock = Any()
    private val entries = LinkedHashMap<String, RuleEntry>()
    private var seq = 0L
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    init {
        storage.read()?.let { json ->
            runCatching { RuleCodec.decodeList(json) }.getOrNull()?.forEach { entries[it.id] = RuleEntry(it, initialState(it), ++seq) }
        }
    }

    fun entries(): List<RuleEntry> = synchronized(lock) { entries.values.toList() }

    fun get(id: String): RuleEntry? = synchronized(lock) { entries[id] }

    fun activeCount(): Int = synchronized(lock) { entries.values.count { it.rule.enabled && !it.state.spent } }

    fun addListener(listener: () -> Unit) { listeners += listener }
    fun removeListener(listener: () -> Unit) { listeners -= listener }

    /** `PUT /rules`: replace the whole set. Counters survive for rules whose definition is byte-identical. */
    fun replaceAll(rules: List<MockRule>) = mutate {
        val previous = entries.toMap()
        entries.clear()
        for (rule in rules) {
            val old = previous[rule.id]
            val state = if (old != null && old.rule == rule) old.state else initialState(rule)
            entries[rule.id] = RuleEntry(rule, state, ++seq)
        }
    }

    /** `POST /rules`: add, or replace by id (counters reset). */
    fun upsert(rule: MockRule) = mutate { entries[rule.id] = RuleEntry(rule, initialState(rule), ++seq) }

    fun remove(id: String): Boolean = mutate { entries.remove(id) != null }

    fun clear() = mutate { entries.clear() }

    fun setEnabled(id: String, enabled: Boolean): Boolean = mutate {
        val e = entries[id] ?: return@mutate false
        entries[id] = e.copy(rule = e.rule.copy(enabled = enabled))
        true
    }

    /** Resets hits, remaining and sequence position and re-enables the rule. */
    fun reset(id: String): Boolean = mutate {
        val e = entries[id] ?: return@mutate false
        entries[id] = e.copy(rule = e.rule.copy(enabled = true), state = initialState(e.rule))
        true
    }

    /** Decide what happens to one request and consume a hit on the winning rule. */
    fun resolve(request: RequestSnapshot): Outcome {
        val outcome: Outcome
        synchronized(lock) {
            val winner = entries.values
                .filter { it.rule.enabled && !it.state.spent && Matcher.matches(it.rule.match, request) }
                .maxWithOrNull(compareBy<RuleEntry> { it.rule.priority }.thenBy { it.addedSeq })
                ?: return Outcome.PassThrough

            val rule = winner.rule
            val state = winner.state
            outcome = when {
                rule.sequence.isNotEmpty() -> when (val step = rule.sequence[state.sequenceIndex.coerceIn(0, rule.sequence.lastIndex)]) {
                    is Step.RespondStep -> Outcome.Mock(rule, step.respond)
                    is Step.FailStep -> Outcome.Failure(rule, step.fail)
                }
                rule.respond != null -> Outcome.Mock(rule, rule.respond)
                else -> Outcome.Failure(rule, rule.fail!!)
            }

            val remaining = state.remaining?.let { it - 1 }
            val newState = state.copy(
                hits = state.hits + 1,
                remaining = remaining,
                sequenceIndex = (state.sequenceIndex + 1).coerceAtMost(rule.sequence.lastIndex.coerceAtLeast(0)),
                lastHitAt = clock(),
            )
            val spentNow = remaining != null && remaining <= 0
            entries[rule.id] = winner.copy(rule = if (spentNow) rule.copy(enabled = false) else rule, state = newState)
            persist()
        }
        notifyListeners()
        return outcome
    }

    private fun initialState(rule: MockRule) = RuleState(remaining = rule.times)

    private inline fun <T> mutate(block: () -> T): T {
        val result = synchronized(lock) {
            val r = block()
            persist()
            r
        }
        notifyListeners()
        return result
    }

    private fun persist() {
        storage.write(RuleCodec.encodeList(entries.values.map { it.rule }))
    }

    private fun notifyListeners() {
        for (l in listeners) l()
    }
}
