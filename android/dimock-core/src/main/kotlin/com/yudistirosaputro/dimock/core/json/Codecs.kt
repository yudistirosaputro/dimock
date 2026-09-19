package com.yudistirosaputro.dimock.core.json

import com.yudistirosaputro.dimock.core.model.Body
import com.yudistirosaputro.dimock.core.model.BodyMatch
import com.yudistirosaputro.dimock.core.model.Fail
import com.yudistirosaputro.dimock.core.model.FailType
import com.yudistirosaputro.dimock.core.model.Match
import com.yudistirosaputro.dimock.core.model.MockRule
import com.yudistirosaputro.dimock.core.model.Respond
import com.yudistirosaputro.dimock.core.model.RuleState
import com.yudistirosaputro.dimock.core.model.Step
import com.yudistirosaputro.dimock.core.model.Transaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.security.SecureRandom

/**
 * Hand-mapped codecs on the kotlinx-serialization JsonElement tree. Explicit mapping keeps the wire format the
 * single source of truth (docs/wire-protocol.md), ignores unknown fields for forward compatibility, and needs no
 * compiler plugin, so `dimock-core` builds anywhere the runtime jar is present.
 */
internal val json: Json = Json { encodeDefaults = false; ignoreUnknownKeys = true; prettyPrint = false }

class WireFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

object RuleCodec {

    fun encode(rule: MockRule): String = toJson(rule).toString()
    fun encodeList(rules: List<MockRule>): String = JsonArray(rules.map { toJson(it) }).toString()
    fun encodeEntry(rule: MockRule, state: RuleState): String = entryJson(rule, state).toString()
    fun encodeEntries(entries: List<Pair<MockRule, RuleState>>): String = JsonArray(entries.map { (r, s) -> entryJson(r, s) }).toString()

    fun decode(text: String): MockRule = wrap { fromJson(parse(text).asObject("rule")) }
    fun decodeList(text: String): List<MockRule> = wrap { parse(text).asArray("rules").map { fromJson(it.asObject("rule")) } }

    fun toJson(rule: MockRule): JsonObject = buildJsonObject {
        put("id", rule.id)
        rule.name?.let { put("name", it) }
        put("enabled", rule.enabled)
        put("priority", rule.priority)
        putJsonObject("match") {
            rule.match.method?.let { put("method", it) }
            rule.match.path?.let { put("path", it) }
            rule.match.host?.let { put("host", it) }
            if (rule.match.query.isNotEmpty()) putJsonObject("query") { rule.match.query.forEach { (k, v) -> put(k, v) } }
            if (rule.match.headers.isNotEmpty()) putJsonObject("headers") { rule.match.headers.forEach { (k, v) -> put(k, v) } }
            if (rule.match.body.isNotEmpty()) putJsonArray("body") {
                rule.match.body.forEach { add(buildJsonObject { put("path", it.path); put("equals", it.equals) }) }
            }
        }
        rule.times?.let { put("times", it) }
        if (rule.sequence.isNotEmpty()) putJsonArray("sequence") {
            rule.sequence.forEach { step ->
                add(
                    when (step) {
                        is Step.RespondStep -> buildJsonObject { put("respond", respondJson(step.respond)) }
                        is Step.FailStep -> buildJsonObject { put("fail", failJson(step.fail)) }
                    },
                )
            }
        }
        rule.respond?.let { put("respond", respondJson(it)) }
        rule.fail?.let { put("fail", failJson(it)) }
    }

    fun fromJson(o: JsonObject): MockRule {
        val matchObj = o["match"]?.takeUnless { it is JsonNull }?.asObject("match") ?: JsonObject(emptyMap())
        val match = Match(
            method = matchObj.str("method"),
            path = matchObj.str("path"),
            host = matchObj.str("host"),
            query = matchObj["query"]?.asObject("match.query")?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
            headers = matchObj["headers"]?.asObject("match.headers")?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
            body = matchObj["body"]?.asArray("match.body")?.map {
                val b = it.asObject("match.body[]")
                BodyMatch(b.str("path") ?: throw WireFormatException("match.body[].path is required"), b.str("equals") ?: throw WireFormatException("match.body[].equals is required"))
            } ?: emptyList(),
        )
        val sequence = o["sequence"]?.asArray("sequence")?.map { stepFromJson(it.asObject("sequence[]")) } ?: emptyList()
        return MockRule(
            id = o.str("id") ?: generateId(),
            name = o.str("name"),
            enabled = o.bool("enabled") ?: true,
            priority = o.int("priority") ?: 0,
            match = match,
            times = o.int("times"),
            sequence = sequence,
            respond = o["respond"]?.takeUnless { it is JsonNull }?.let { respondFromJson(it.asObject("respond")) },
            fail = o["fail"]?.takeUnless { it is JsonNull }?.let { failFromJson(it.asObject("fail")) },
        )
    }

    private fun entryJson(rule: MockRule, state: RuleState): JsonObject = JsonObject(
        toJson(rule) + ("state" to buildJsonObject {
            put("hits", state.hits)
            state.remaining?.let { put("remaining", it) }
            put("sequenceIndex", state.sequenceIndex)
            put("spent", state.spent)
            state.lastHitAt?.let { put("lastHitAt", it) }
        }),
    )

    private fun stepFromJson(o: JsonObject): Step = when {
        o["respond"] != null -> Step.RespondStep(respondFromJson(o["respond"]!!.asObject("sequence[].respond")))
        o["fail"] != null -> Step.FailStep(failFromJson(o["fail"]!!.asObject("sequence[].fail")))
        else -> throw WireFormatException("sequence step needs respond or fail")
    }

    private fun respondJson(r: Respond): JsonObject = buildJsonObject {
        put("status", r.status)
        if (r.headers.isNotEmpty()) putJsonObject("headers") { r.headers.forEach { (k, v) -> put(k, v) } }
        r.body?.let { put("body", it) }
        if (r.delayMs != 0L) put("delayMs", r.delayMs)
    }

    private fun respondFromJson(o: JsonObject) = Respond(
        status = o.int("status") ?: 200,
        headers = o["headers"]?.asObject("respond.headers")?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
        body = o.str("body"),
        delayMs = o.long("delayMs") ?: 0,
    )

    private fun failJson(f: Fail): JsonObject = buildJsonObject {
        put("type", f.type.wire)
        if (f.delayMs != 0L) put("delayMs", f.delayMs)
    }

    private fun failFromJson(o: JsonObject) = Fail(
        type = FailType.fromWire(o.str("type") ?: throw WireFormatException("fail.type is required")),
        delayMs = o.long("delayMs") ?: 0,
    )

    private val random = SecureRandom()
    private const val ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz"

    fun generateId(): String = buildString(14) {
        append("r-")
        repeat(12) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
}

object TransactionCodec {

    fun encodeSummary(tx: Transaction): String = summaryJson(tx).toString()
    fun encodeSummaries(list: List<Transaction>): String = JsonArray(list.map { summaryJson(it) }).toString()
    fun encodeFull(tx: Transaction): String = fullJson(tx).toString()

    private fun summaryJson(tx: Transaction): JsonObject = buildJsonObject {
        put("id", tx.id)
        put("startedAt", tx.startedAt)
        tx.durationMs?.let { put("durationMs", it) }
        put("method", tx.method)
        put("url", tx.url)
        put("host", tx.host)
        put("path", tx.path)
        tx.responseCode?.let { put("responseCode", it) }
        tx.error?.let { put("error", it) }
        put("mocked", tx.mocked)
        tx.mockRuleId?.let { put("mockRuleId", it) }
        tx.tag?.let { put("tag", it) }
        put("requestBytes", tx.requestBody?.total() ?: 0)
        put("responseBytes", tx.responseBody?.total() ?: 0)
        tx.responseBody?.contentType()?.let { put("responseContentType", it) }
    }

    private fun fullJson(tx: Transaction): JsonObject = JsonObject(
        summaryJson(tx) + mapOf(
            "requestHeaders" to headersJson(tx.requestHeaders),
            "requestBody" to bodyJson(tx.requestBody),
            "responseHeaders" to headersJson(tx.responseHeaders),
            "responseBody" to bodyJson(tx.responseBody),
        ),
    )

    private fun headersJson(h: Map<String, List<String>>): JsonObject =
        JsonObject(h.mapValues { (_, v) -> JsonArray(v.map { JsonPrimitive(it) }) })

    private fun bodyJson(body: Body?): JsonElement = when (body) {
        null -> JsonNull
        is Body.Text -> buildJsonObject { put("kind", "text"); put("text", body.text); body.contentType?.let { put("contentType", it) }; put("totalBytes", body.text.length) }
        is Body.Truncated -> buildJsonObject { put("kind", "truncated"); put("text", body.text); body.contentType?.let { put("contentType", it) }; put("totalBytes", body.totalBytes) }
        is Body.Binary -> buildJsonObject { put("kind", "binary"); body.contentType?.let { put("contentType", it) }; put("totalBytes", body.totalBytes) }
    }

    private fun Body.total(): Long = when (this) {
        is Body.Text -> text.length.toLong()
        is Body.Truncated -> totalBytes
        is Body.Binary -> totalBytes
    }

    private fun Body.contentType(): String? = when (this) {
        is Body.Text -> contentType
        is Body.Truncated -> contentType
        is Body.Binary -> contentType
    }
}

// ---- small JsonElement helpers -------------------------------------------------------------------------------

internal fun parse(text: String): JsonElement = try {
    json.parseToJsonElement(text)
} catch (e: Exception) {
    throw WireFormatException("invalid JSON: ${e.message}", e)
}

internal inline fun <T> wrap(block: () -> T): T = try {
    block()
} catch (e: WireFormatException) {
    throw e
} catch (e: Exception) {
    throw WireFormatException(e.message ?: "invalid wire payload", e)
}

internal fun JsonElement.asObject(what: String): JsonObject = this as? JsonObject ?: throw WireFormatException("$what must be an object")
internal fun JsonElement.asArray(what: String): JsonArray = this as? JsonArray ?: throw WireFormatException("$what must be an array")
internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
