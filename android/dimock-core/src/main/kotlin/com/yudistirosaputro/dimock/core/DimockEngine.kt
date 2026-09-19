package com.yudistirosaputro.dimock.core

import com.yudistirosaputro.dimock.core.engine.ActivityEntry
import com.yudistirosaputro.dimock.core.engine.ActivityLog
import com.yudistirosaputro.dimock.core.engine.BodyLimiter
import com.yudistirosaputro.dimock.core.engine.CaptureStore
import com.yudistirosaputro.dimock.core.engine.Event
import com.yudistirosaputro.dimock.core.engine.EventBus
import com.yudistirosaputro.dimock.core.engine.NoStorage
import com.yudistirosaputro.dimock.core.engine.Redactor
import com.yudistirosaputro.dimock.core.engine.RuleStorage
import com.yudistirosaputro.dimock.core.engine.RuleStore
import com.yudistirosaputro.dimock.core.json.RuleCodec
import com.yudistirosaputro.dimock.core.json.TransactionCodec
import com.yudistirosaputro.dimock.core.model.Outcome
import com.yudistirosaputro.dimock.core.model.RequestSnapshot
import com.yudistirosaputro.dimock.core.model.Transaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Identity reported on `GET /health`. */
data class HostInfo(val app: String, val appVersion: String?, val libraryVersion: String)

/** Everything the interceptor and the wire server need, wired together. Platform-free. */
class EngineConfig(
    val maxTransactions: Int = 500,
    val maxStoreBytes: Long = 50L * 1024 * 1024,
    val maxBodyBytes: Int = 1024 * 1024,
    val redactHeaders: Set<String> = Redactor.DEFAULT_HEADERS,
    val redactBodyPatterns: List<Regex> = emptyList(),
    val ruleStorage: RuleStorage = NoStorage,
    val agentWriteEnabled: Boolean = true,
    /** After this long without a `/health` call the client counts as disconnected. */
    val clientTimeoutMs: Long = 30_000,
    val clock: () -> Long = System::currentTimeMillis,
)

class DimockEngine(val host: HostInfo, val config: EngineConfig = EngineConfig()) {

    val rules = RuleStore(config.ruleStorage, config.clock)
    val captures = CaptureStore(config.maxTransactions, config.maxStoreBytes)
    val redactor = Redactor(config.redactHeaders, config.redactBodyPatterns)
    val limiter = BodyLimiter(config.maxBodyBytes)
    val activity = ActivityLog(clock = config.clock)
    val events = EventBus(config.clock)

    private val agentWrite = AtomicBoolean(config.agentWriteEnabled)
    private val lastClientSeen = AtomicLong(0)
    private val clientName = java.util.concurrent.atomic.AtomicReference<String?>(null)

    init {
        rules.addListener { events.emit(Event.RULES_CHANGED, buildJsonObject { put("active", rules.activeCount()); put("total", rules.entries().size) }) }
        captures.addListener { tx -> events.emit(Event.TRANSACTION, Json.parseToJsonElement(TransactionCodec.encodeSummary(tx)).jsonObject) }
    }

    // ---- interceptor side ----------------------------------------------------------------------------------

    /** Decide for one request. Emits `rule_hit` when a rule wins. */
    fun resolve(request: RequestSnapshot): Outcome {
        val outcome = rules.resolve(request)
        val rule = when (outcome) {
            is Outcome.Mock -> outcome.rule
            is Outcome.Failure -> outcome.rule
            Outcome.PassThrough -> null
        }
        if (rule != null) events.emit(Event.RULE_HIT, buildJsonObject { put("ruleId", rule.id); rule.name?.let { put("name", it) }; put("method", request.method); put("path", request.path) })
        return outcome
    }

    /** Store a finished exchange. Headers/bodies are redacted here, before storage. */
    fun record(tx: Transaction) {
        captures.add(
            tx.copy(
                requestHeaders = redactor.headers(tx.requestHeaders),
                responseHeaders = redactor.headers(tx.responseHeaders),
                requestBody = redactor.body(tx.requestBody),
                responseBody = redactor.body(tx.responseBody),
            ),
        )
    }

    // ---- agent side -----------------------------------------------------------------------------------------

    var agentWriteEnabled: Boolean
        get() = agentWrite.get()
        set(value) {
            if (agentWrite.getAndSet(value) != value) {
                activity.add(ActivityEntry.Kind.WRITE, if (value) "Agent writes enabled" else "Agent writes disabled", "local")
            }
        }

    val clientConnected: Boolean get() = config.clock() - lastClientSeen.get() < config.clientTimeoutMs
    val connectedClientName: String? get() = if (clientConnected) clientName.get() else null

    /** Called on every `/health`. Logs "Connected" when a client (re)appears after the timeout. */
    fun clientSeen(name: String?) {
        val now = config.clock()
        val wasConnected = clientConnected
        lastClientSeen.set(now)
        name?.let { clientName.set(it) }
        if (!wasConnected) {
            logActivity(ActivityEntry.Kind.CONNECT, "Connected", "GET /health")
            events.emit(Event.CLIENT_CONNECTED, buildJsonObject { name?.let { put("client", it) }; put("at", now) })
        }
    }

    fun logActivity(kind: ActivityEntry.Kind, summary: String, call: String) {
        val entry = activity.add(kind, summary, call)
        events.emit(Event.AGENT_ACTIVITY, entry.toJson())
    }

    fun healthJson(): String = buildJsonObject {
        put("app", host.app)
        host.appVersion?.let { put("appVersion", it) }
        put("version", host.libraryVersion)
        put("protocol", PROTOCOL_VERSION)
        put("activeMocks", rules.activeCount())
        put("transactions", captures.count())
        put("agentWriteEnabled", agentWriteEnabled)
    }.toString()

    fun rulesJson(): String = RuleCodec.encodeEntries(rules.entries().map { it.rule to it.state })

    companion object {
        const val PROTOCOL_VERSION = 1
        const val DEFAULT_PORT = 6767
    }
}
