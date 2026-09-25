package com.yudistirosaputro.dimock.core

import com.yudistirosaputro.dimock.core.engine.BodyFormat
import com.yudistirosaputro.dimock.core.engine.BodyView
import com.yudistirosaputro.dimock.core.engine.CurlFormat
import com.yudistirosaputro.dimock.core.engine.Labels
import com.yudistirosaputro.dimock.core.engine.LocalPresets
import com.yudistirosaputro.dimock.core.engine.Redactor
import com.yudistirosaputro.dimock.core.model.Body
import com.yudistirosaputro.dimock.core.model.Fail
import com.yudistirosaputro.dimock.core.model.FailType
import com.yudistirosaputro.dimock.core.model.Match
import com.yudistirosaputro.dimock.core.model.MockRule
import com.yudistirosaputro.dimock.core.model.Respond
import com.yudistirosaputro.dimock.core.model.Step
import com.yudistirosaputro.dimock.core.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun tx(
    method: String = "GET",
    path: String = "/posts",
    code: Int? = 200,
    error: String? = null,
    duration: Long? = 241,
    mocked: Boolean = false,
    responseBody: Body? = Body.Text("""[{"id":1}]""", "application/json"),
    requestBody: Body? = null,
    requestHeaders: Map<String, List<String>> = mapOf("Accept" to listOf("application/json")),
) = Transaction(
    id = "t1", startedAt = 1_700_000_000_000, durationMs = duration, method = method,
    url = "https://jsonplaceholder.typicode.com$path", host = "jsonplaceholder.typicode.com", path = path,
    requestHeaders = requestHeaders, requestBody = requestBody,
    responseCode = code, responseHeaders = mapOf("Content-Type" to listOf("application/json; charset=utf-8")),
    responseBody = responseBody, error = error, mocked = mocked, mockRuleId = if (mocked) "r" else null,
)

class LocalPresetsTest {

    @Test
    fun `one rule id per endpoint, local-prefixed, so re-applying replaces`() {
        assertEquals("local:get-posts", LocalPresets.ruleId(tx()))
        assertEquals("local:post-v1-auth-login", LocalPresets.ruleId(tx(method = "POST", path = "/v1/auth/login")))
        assertEquals("local:get-root", LocalPresets.ruleId(tx(path = "/")))
        assertEquals(LocalPresets.ruleId(tx()), LocalPresets.status(tx(), 500).id)
        assertEquals(LocalPresets.ruleId(tx()), LocalPresets.slow(tx(), 5_000).id)
        assertEquals(LocalPresets.ruleId(tx()), LocalPresets.custom(tx(), 200, "{}").id)
    }

    @Test
    fun `status preset changes the HTTP status only and sends an empty body`() {
        val rule = LocalPresets.status(tx(), 500)
        assertEquals(Match(method = "GET", path = "/posts"), rule.match)
        assertEquals(500, rule.respond?.status)
        assertEquals("", rule.respond?.body)
        assertEquals("application/json; charset=utf-8", rule.respond?.headers?.get("Content-Type"))
        assertEquals(LocalPresets.PRIORITY, rule.priority)
        assertTrue(rule.enabled)
        assertTrue(LocalPresets.isLocal(rule))
    }

    @Test
    fun `custom preset keeps whatever the tester typed and the sheet pre-fills from the capture`() {
        assertEquals("""[{"id":1}]""", LocalPresets.capturedBody(tx()))
        assertEquals("", LocalPresets.capturedBody(tx(responseBody = Body.Binary(10, "image/png"))))
        val rule = LocalPresets.custom(tx(), 201, """{"id":99,"title":"typed"}""")
        assertEquals(201, rule.respond?.status)
        assertEquals("""{"id":99,"title":"typed"}""", rule.respond?.body)
        assertEquals(Match(method = "GET", path = "/posts"), rule.match)
        assertEquals("GET /posts → custom", rule.name)
    }

    @Test
    fun `failure presets map to fail types and slow keeps the captured status and body`() {
        assertEquals(Fail(FailType.TIMEOUT), LocalPresets.timeout(tx()).fail)
        assertEquals(Fail(FailType.CONNECTION_RESET), LocalPresets.reset(tx()).fail)
        val slow = LocalPresets.slow(tx(code = 201), 5_000)
        assertEquals(201, slow.respond?.status)
        assertEquals("""[{"id":1}]""", slow.respond?.body)
        assertEquals(5_000L, slow.respond?.delayMs)
    }

    @Test
    fun `describe gives the effect the app will see`() {
        assertEquals("500", LocalPresets.describe(LocalPresets.status(tx(), 500)))
        assertEquals("+5.0 s", LocalPresets.describe(LocalPresets.slow(tx(), 5_000)))
        assertEquals("timeout", LocalPresets.describe(LocalPresets.timeout(tx())))
        assertEquals("reset", LocalPresets.describe(LocalPresets.reset(tx())))
        assertEquals("201 · empty {}", LocalPresets.describe(LocalPresets.custom(tx(), 201, "{}")))
        assertEquals("201", LocalPresets.describe(LocalPresets.custom(tx(), 201, """{"id":1}""")))
        val agentRule = MockRule(id = "a", match = Match(path = "/x"), respond = Respond(status = 500, body = """{"error":1}""", delayMs = 2_000))
        assertEquals("500 · +2.0 s", LocalPresets.describe(agentRule))
        val agentEmpty = MockRule(id = "e", match = Match(path = "/x"), respond = Respond(body = "[]"))
        assertEquals("empty []", LocalPresets.describe(agentEmpty))
        val seq = MockRule(id = "s", match = Match(path = "/x"), sequence = listOf(Step.FailStep(Fail(FailType.TIMEOUT)), Step.RespondStep(Respond())))
        assertEquals("sequence · 2 steps", LocalPresets.describe(seq))
        assertEquals("200", LocalPresets.describe(MockRule(id = "b", match = Match(path = "/x"), respond = Respond(body = "{\"ok\":true}"))))
        assertEquals("malformed", LocalPresets.describe(MockRule(id = "m", match = Match(path = "/x"), fail = Fail(FailType.MALFORMED_BODY))))
    }
}

class LabelsTest {

    @Test
    fun `durations and sizes`() {
        assertEquals("241 ms", Labels.duration(241))
        assertEquals("1.2 s", Labels.duration(1_234))
        assertEquals("10.0 s", Labels.duration(10_000))
        assertEquals("—", Labels.duration(null))
        assertEquals("0.1 KB", Labels.bytes(12))
        assertEquals("4.2 KB", Labels.bytes(4_300))
        assertEquals("1.3 MB", Labels.bytes(1_363_149))
    }

    @Test
    fun `transport wording follows the status class and failures`() {
        assertEquals("OK", Labels.transport(200, null))
        assertEquals("redirect", Labels.transport(302, null))
        assertEquals("client error", Labels.transport(404, null))
        assertEquals("server error", Labels.transport(503, null))
        assertEquals("no response", Labels.transport(null, "dimock: timed out after 10000 ms (rule x)"))
        assertEquals("ERR", Labels.status(tx(code = null, error = "boom")))
        assertEquals("200", Labels.status(tx()))
    }

    @Test
    fun `interceptor failure messages are rewritten for humans`() {
        assertEquals("Timed out after 10 000 ms", Labels.failure("dimock: timed out after 10000 ms (rule local:get-posts)"))
        assertEquals("Connection reset", Labels.failure("dimock: connection reset (rule r)"))
        assertEquals("Unable to resolve host", Labels.failure("unable to resolve host"))
        assertNull(Labels.failure(null))
        assertEquals("timeout", Labels.failureShort("dimock: timed out after 10000 ms (rule r)"))
        assertEquals("reset", Labels.failureShort("dimock: connection reset (rule r)"))
    }

    @Test
    fun `traffic lines are what the notification and rows show`() {
        assertEquals("GET /posts · 200 · 241 ms", Labels.trafficLine(tx()))
        assertEquals("POST /posts · 500 · 12 ms · MOCK", Labels.trafficLine(tx(method = "POST", code = 500, duration = 12, mocked = true)))
        assertEquals("GET /posts/1 · timeout · MOCK", Labels.trafficLine(tx(path = "/posts/1", code = null, error = "dimock: timed out after 10000 ms (rule r)", mocked = true)))
        assertEquals("dimock · 1 call", Labels.notificationTitle(1, 0))
        assertEquals("dimock · 3 calls · 1 mock", Labels.notificationTitle(3, 1))
        assertEquals("dimock · 0 calls · 2 mocks", Labels.notificationTitle(0, 2))
    }

    @Test
    fun `relative time`() {
        assertEquals("just now", Labels.ago(1_000, 3_000))
        assertEquals("2 min ago", Labels.ago(0, 150_000))
        assertEquals("3 h ago", Labels.ago(0, 3 * 3_600_000L + 5))
    }
}

class BodyFormatTest {

    @Test
    fun `json is pretty printed with two spaces and source key order`() {
        val pretty = BodyFormat.prettyJson("""{"b":[1,2,{"c":null}],"a":"x","e":{},"f":[]}""")
        assertEquals(
            """
            |{
            |  "b": [
            |    1,
            |    2,
            |    {
            |      "c": null
            |    }
            |  ],
            |  "a": "x",
            |  "e": {},
            |  "f": []
            |}
            """.trimMargin(),
            pretty,
        )
        assertNull(BodyFormat.prettyJson("hello"))
        assertNull(BodyFormat.prettyJson("{not json"))
    }

    @Test
    fun `form bodies decode to key colon value lines`() {
        assertEquals("grant_type: password\nfilters: {\"a\":1}\nflag", BodyFormat.formLines("grant_type=password&filters=%7B%22a%22%3A1%7D&flag"))
        assertTrue(BodyFormat.isForm("application/x-www-form-urlencoded; charset=UTF-8"))
        val view = BodyFormat.view(Body.Text("q=hello+world", "application/x-www-form-urlencoded")) as BodyView.Text
        assertEquals(BodyView.Text.Kind.FORM, view.kind)
        assertEquals("q: hello world", view.pretty)
    }

    @Test
    fun `body views for none, binary, text and truncated`() {
        assertEquals(BodyView.None, BodyFormat.view(null))
        assertEquals(BodyView.None, BodyFormat.view(Body.Text("", "text/plain")))
        assertEquals(BodyView.Binary(18_000, "image/png"), BodyFormat.view(Body.Binary(18_000, "image/png")))
        val json = BodyFormat.view(Body.Text("""{"a":1}""", "application/json")) as BodyView.Text
        assertEquals(BodyView.Text.Kind.JSON, json.kind)
        assertEquals("{\n  \"a\": 1\n}", json.pretty)
        val cut = BodyFormat.view(Body.Truncated("""{"a":1,"b":""", 2_000_000, "application/json")) as BodyView.Text
        assertEquals(BodyView.Text.Kind.PLAIN, cut.kind) // truncated JSON never parses; shown raw
        assertEquals(11L, cut.truncatedAt)
        assertEquals(2_000_000L, cut.totalBytes)
    }

    @Test
    fun `search is case-insensitive and returns every offset`() {
        assertEquals(listOf(0, 10), BodyFormat.search("Error and error", "error"))
        assertEquals(emptyList<Int>(), BodyFormat.search("abc", ""))
        assertEquals(emptyList<Int>(), BodyFormat.search("abc", "zz"))
        assertEquals("application/json", BodyFormat.contentType(mapOf("content-type" to listOf("application/json; charset=utf-8"))))
    }
}

class CurlFormatTest {

    @Test
    fun `curl exports redacted placeholders never raw secrets`() {
        val t = tx(
            method = "POST",
            requestHeaders = mapOf("Authorization" to listOf(Redactor.MASK), "Content-Type" to listOf("application/json"), "Host" to listOf("x")),
            requestBody = Body.Text("""{"title":"it's"}""", "application/json"),
        )
        val curl = CurlFormat.format(t)
        assertEquals(
            "curl -X POST https://jsonplaceholder.typicode.com/posts \\\n  -H 'Authorization: «redacted»' \\\n  -H 'Content-Type: application/json' \\\n  --data-raw '{\"title\":\"it'\\''s\"}'",
            curl,
        )
        assertFalse(curl.contains("Host:"))
        assertTrue(CurlFormat.hasRedacted(t))
        assertEquals(listOf("Authorization"), CurlFormat.redactedHeaders(t))
        assertFalse(CurlFormat.hasRedacted(tx()))
    }

    @Test
    fun `curl quotes URLs with query strings and globs so they paste into a shell`() {
        val query = tx(path = "/discover/movie?language=en-US&with_genres=%2C12%2C16&page=1", requestHeaders = emptyMap())
        assertEquals("curl -X GET 'https://jsonplaceholder.typicode.com/discover/movie?language=en-US&with_genres=%2C12%2C16&page=1'", CurlFormat.format(query))
        assertEquals("curl -X GET 'https://jsonplaceholder.typicode.com/files/*'", CurlFormat.format(tx(path = "/files/*", requestHeaders = emptyMap())))
        assertEquals("curl -X GET https://jsonplaceholder.typicode.com/posts", CurlFormat.format(tx(requestHeaders = emptyMap())))
    }
}
