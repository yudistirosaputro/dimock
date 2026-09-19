package com.yudistirosaputro.dimock.core.engine

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList

/** One server-sent event on `GET /events`. */
data class Event(val type: String, val data: JsonObject, val at: Long) {
    companion object {
        const val TRANSACTION = "transaction"
        const val RULE_HIT = "rule_hit"
        const val RULES_CHANGED = "rules_changed"
        const val CLIENT_CONNECTED = "client_connected"
        const val AGENT_ACTIVITY = "agent_activity"
    }
}

class EventBus(private val clock: () -> Long = System::currentTimeMillis) {
    private val listeners = CopyOnWriteArrayList<(Event) -> Unit>()

    fun emit(type: String, data: JsonObject) {
        val event = Event(type, data, clock())
        for (l in listeners) runCatching { l(event) }
    }

    fun subscribe(listener: (Event) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }
}

/** One line in the Agent tab: what a wire client read or changed. */
data class ActivityEntry(val at: Long, val kind: Kind, val summary: String, val call: String) {
    enum class Kind(val wire: String) { READ("read"), WRITE("write"), CONNECT("connect") }

    fun toJson(): JsonObject = buildJsonObject {
        put("at", at); put("kind", kind.wire); put("summary", summary); put("call", call)
    }
}

class ActivityLog(private val capacity: Int = 200, private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private val entries = ArrayDeque<ActivityEntry>()

    fun add(kind: ActivityEntry.Kind, summary: String, call: String): ActivityEntry {
        val entry = ActivityEntry(clock(), kind, summary, call)
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > capacity) entries.pollFirst()
        }
        return entry
    }

    /** Newest first. */
    fun list(limit: Int = Int.MAX_VALUE): List<ActivityEntry> = synchronized(lock) { entries.descendingIterator().asSequence().take(limit).toList() }

    fun clear() = synchronized(lock) { entries.clear() }
}
