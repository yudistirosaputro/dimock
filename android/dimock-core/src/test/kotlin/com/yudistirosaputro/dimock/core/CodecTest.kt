package com.yudistirosaputro.dimock.core

import com.yudistirosaputro.dimock.core.json.RuleCodec
import com.yudistirosaputro.dimock.core.json.TransactionCodec
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecTest {

    private val full = MockRule(
        id = "login-error",
        name = "Login → 500",
        priority = 10,
        match = Match(
            method = "POST",
            path = "/v1/auth/login",
            host = "api.example.com",
            query = mapOf("grant_type" to "password"),
            headers = mapOf("X-Client" to "android"),
            body = listOf(BodyMatch("$.operationName", "GetPortfolio")),
        ),
        times = 1,
        respond = Respond(status = 500, headers = mapOf("Content-Type" to "application/json"), body = """{"error":"internal"}""", delayMs = 800),
    )

    @Test
    fun `rule round-trips through JSON`() {
        val json = RuleCodec.encode(full)
        assertEquals(full, RuleCodec.decode(json))
    }

    @Test
    fun `sequence and fail round-trip`() {
        val rule = MockRule(
            id = "seq",
            match = Match(path = "/v1/orders**"),
            sequence = listOf(Step.FailStep(Fail(FailType.TIMEOUT, delayMs = 100)), Step.RespondStep(Respond(200, body = "[]"))),
        )
        assertEquals(rule, RuleCodec.decode(RuleCodec.encode(rule)))
        val failRule = MockRule(id = "f", match = Match(), fail = Fail(FailType.CONNECTION_RESET))
        assertEquals(failRule, RuleCodec.decode(RuleCodec.encode(failRule)))
    }

    @Test
    fun `wire format uses the documented field names`() {
        val obj = Json.parseToJsonElement(RuleCodec.encode(full)).jsonObject
        assertEquals("login-error", obj["id"]!!.jsonPrimitive.content)
        assertEquals("POST", obj["match"]!!.jsonObject["method"]!!.jsonPrimitive.content)
        assertEquals("800", obj["respond"]!!.jsonObject["delayMs"]!!.jsonPrimitive.content)
        assertEquals("1", obj["times"]!!.jsonPrimitive.content)
    }

    @Test
    fun `decoding tolerates unknown fields and missing optionals`() {
        val minimal = """{"id":"x","match":{"path":"/a"},"respond":{"status":204},"futureField":{"nested":true}}"""
        val rule = RuleCodec.decode(minimal)
        assertEquals("x", rule.id)
        assertEquals(0, rule.priority)
        assertTrue(rule.enabled)
        assertNull(rule.times)
        assertEquals(204, rule.respond!!.status)
    }

    @Test
    fun `decoding generates an id when absent`() {
        val rule = RuleCodec.decode("""{"match":{"path":"/a"},"respond":{"status":200}}""")
        assertTrue(rule.id.isNotBlank())
    }

    @Test
    fun `decoding a rule list`() {
        val list = RuleCodec.decodeList("""[{"id":"a","match":{},"respond":{"status":200}},{"id":"b","match":{},"fail":{"type":"timeout"}}]""")
        assertEquals(listOf("a", "b"), list.map { it.id })
        assertEquals(FailType.TIMEOUT, list[1].fail!!.type)
    }

    @Test
    fun `rule entry encodes runtime state`() {
        val json = RuleCodec.encodeEntry(full, RuleState(hits = 1, remaining = 0, sequenceIndex = 0, lastHitAt = 123L))
        val obj = Json.parseToJsonElement(json).jsonObject
        val state = obj["state"]!!.jsonObject
        assertEquals("1", state["hits"]!!.jsonPrimitive.content)
        assertEquals("0", state["remaining"]!!.jsonPrimitive.content)
        assertEquals("true", state["spent"]!!.jsonPrimitive.content)
    }

    @Test
    fun `transaction summary omits bodies and full includes them`() {
        val tx = Transaction(
            id = "01J", startedAt = 1L, durationMs = 812, method = "POST", url = "https://api.example.com/v1/auth/login",
            host = "api.example.com", path = "/v1/auth/login",
            requestHeaders = mapOf("Authorization" to listOf("«redacted»")),
            requestBody = Body.Text("{}", "application/json"),
            responseCode = 500, responseHeaders = emptyMap(),
            responseBody = Body.Truncated("{\"a\":", 2_000_000, "application/json"),
            error = null, mocked = true, mockRuleId = "login-error",
        )
        val summary = Json.parseToJsonElement(TransactionCodec.encodeSummary(tx)).jsonObject
        assertNull(summary["requestBody"])
        assertEquals("true", summary["mocked"]!!.jsonPrimitive.content)
        assertEquals("500", summary["responseCode"]!!.jsonPrimitive.content)

        val fullJson = Json.parseToJsonElement(TransactionCodec.encodeFull(tx)).jsonObject
        val body = fullJson["responseBody"]!!.jsonObject
        assertEquals("truncated", body["kind"]!!.jsonPrimitive.content)
        assertEquals("2000000", body["totalBytes"]!!.jsonPrimitive.content)
        assertEquals("«redacted»", fullJson["requestHeaders"]!!.jsonObject["Authorization"]!!.toString().trim('[', ']', '"'))
    }
}
