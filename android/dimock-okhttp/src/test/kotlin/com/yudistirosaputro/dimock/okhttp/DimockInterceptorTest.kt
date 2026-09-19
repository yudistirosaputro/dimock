package com.yudistirosaputro.dimock.okhttp

import com.yudistirosaputro.dimock.core.EngineConfig
import com.yudistirosaputro.dimock.core.HostInfo
import com.yudistirosaputro.dimock.core.DimockEngine
import com.yudistirosaputro.dimock.core.model.Body
import com.yudistirosaputro.dimock.core.model.BodyMatch
import com.yudistirosaputro.dimock.core.model.Fail
import com.yudistirosaputro.dimock.core.model.FailType
import com.yudistirosaputro.dimock.core.model.Match
import com.yudistirosaputro.dimock.core.model.MockRule
import com.yudistirosaputro.dimock.core.model.Respond
import com.yudistirosaputro.dimock.core.model.Step
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** Wave 1 acceptance criteria for the interceptor, against a real OkHttp client and MockWebServer. */
class DimockInterceptorTest {

    private lateinit var upstream: MockWebServer
    private lateinit var engine: DimockEngine
    private val slept = mutableListOf<Long>()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        upstream = MockWebServer().also { it.start() }
        engine = DimockEngine(HostInfo("test.app", "1", "test"), EngineConfig(maxBodyBytes = 1024 * 1024))
        val interceptor = DimockInterceptor({ engine }, sleeper = { slept += it })
        client = OkHttpClient.Builder().addInterceptor(interceptor).readTimeout(250, TimeUnit.MILLISECONDS).build()
    }

    @After
    fun tearDown() = upstream.shutdown()

    private fun url(path: String) = upstream.url(path).toString()
    private fun post(path: String, body: String) = Request.Builder().url(url(path)).post(body.toRequestBody("application/json".toMediaType())).build()
    private fun get(path: String) = Request.Builder().url(url(path)).header("Authorization", "Bearer secret-token").build()

    private fun rule(id: String, path: String = "/v1/auth/login", method: String = "POST", status: Int = 500, times: Int? = null, priority: Int = 0, delay: Long = 0) =
        MockRule(id = id, priority = priority, match = Match(method = method, path = path), times = times, respond = Respond(status = status, body = """{"error":"internal"}""", delayMs = delay))

    @Test
    fun `stored rule short-circuits the network and records a mocked transaction`() {
        engine.rules.upsert(rule("login-error", delay = 800))
        val response = client.newCall(post("/v1/auth/login", """{"u":"a"}""")).execute()
        assertEquals(500, response.code)
        assertEquals("""{"error":"internal"}""", response.body!!.string())
        assertEquals("1", response.header(DimockInterceptor.MOCK_HEADER))
        assertEquals(0, upstream.requestCount)
        assertEquals(listOf(800L), slept)

        val tx = engine.captures.list().single()
        assertTrue(tx.mocked)
        assertEquals("login-error", tx.mockRuleId)
        assertEquals(500, tx.responseCode)
        assertEquals("POST", tx.method)
        assertEquals("/v1/auth/login", tx.path)
        assertEquals(Body.Text("""{"u":"a"}""", "application/json; charset=utf-8"), tx.requestBody)
    }

    @Test
    fun `no matching rule - request reaches the network and is captured with redacted headers`() {
        upstream.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("""{"ok":true}"""))
        val response = client.newCall(get("/v1/portfolio/summary")).execute()
        assertEquals("""{"ok":true}""", response.body!!.string())
        assertEquals(1, upstream.requestCount)

        val tx = engine.captures.list().single()
        assertFalse(tx.mocked)
        assertEquals(200, tx.responseCode)
        assertEquals(listOf("«redacted»"), tx.requestHeaders["Authorization"])
        assertEquals(Body.Text("""{"ok":true}""", "application/json"), tx.responseBody)
        assertFalse(tx.toString().contains("secret-token"))
    }

    @Test
    fun `priority 10 beats 0 and equal priority goes to the most recent`() {
        engine.rules.upsert(rule("low", status = 500, priority = 0))
        engine.rules.upsert(rule("high", status = 503, priority = 10))
        assertEquals(503, client.newCall(post("/v1/auth/login", "{}")).execute().code)
        engine.rules.upsert(rule("newer", status = 418, priority = 10))
        assertEquals(418, client.newCall(post("/v1/auth/login", "{}")).execute().code)
    }

    @Test
    fun `times 1 - first request mocked, second reaches the network, rule is spent`() {
        engine.rules.upsert(rule("once", times = 1))
        upstream.enqueue(MockResponse().setResponseCode(200).setBody("real"))
        assertEquals(500, client.newCall(post("/v1/auth/login", "{}")).execute().code)
        assertEquals(200, client.newCall(post("/v1/auth/login", "{}")).execute().code)
        assertEquals(1, upstream.requestCount)
        val entry = engine.rules.entries().single()
        assertFalse(entry.rule.enabled)
        assertEquals(0, entry.state.remaining)
    }

    @Test
    fun `sequence timeout then 200 - first throws SocketTimeoutException after the read timeout, then 200 twice`() {
        engine.rules.upsert(
            MockRule(
                id = "orders-seq", match = Match(method = "GET", path = "/v1/orders**"),
                sequence = listOf(Step.FailStep(Fail(FailType.TIMEOUT)), Step.RespondStep(Respond(200, body = "[]"))),
            ),
        )
        try {
            client.newCall(get("/v1/orders?status=open")).execute()
            fail("expected SocketTimeoutException")
        } catch (e: SocketTimeoutException) {
            assertTrue(e.message!!.contains("250 ms"))
        }
        assertEquals(listOf(250L), slept)
        assertEquals(200, client.newCall(get("/v1/orders")).execute().code)
        assertEquals(200, client.newCall(get("/v1/orders/42")).execute().code)
        assertEquals(0, upstream.requestCount)

        val failed = engine.captures.list().last()
        assertTrue(failed.mocked)
        assertNull(failed.responseCode)
        assertNotNull(failed.error)
    }

    @Test
    fun `connection_reset throws IOException, malformed_body and empty_body return 200 with the documented bodies`() {
        engine.rules.upsert(MockRule(id = "reset", match = Match(path = "/reset"), fail = Fail(FailType.CONNECTION_RESET)))
        engine.rules.upsert(MockRule(id = "malformed", match = Match(path = "/malformed"), fail = Fail(FailType.MALFORMED_BODY)))
        engine.rules.upsert(MockRule(id = "empty", match = Match(path = "/empty"), fail = Fail(FailType.EMPTY_BODY)))

        try { client.newCall(get("/reset")).execute(); fail("expected IOException") } catch (e: IOException) { assertTrue(e.message!!.contains("connection reset")) }

        val malformed = client.newCall(get("/malformed")).execute()
        assertEquals(200, malformed.code)
        assertEquals(DimockInterceptor.MALFORMED_JSON, malformed.body!!.string())

        val empty = client.newCall(get("/empty")).execute()
        assertEquals(200, empty.code)
        assertEquals("", empty.body!!.string())
        assertEquals(0, upstream.requestCount)
    }

    @Test
    fun `GraphQL body match - different operationName is not mocked`() {
        engine.rules.upsert(
            MockRule(
                id = "gql", match = Match(method = "POST", path = "/graphql", body = listOf(BodyMatch("$.operationName", "GetPortfolio"))),
                respond = Respond(200, body = """{"data":{"portfolio":[]}}"""),
            ),
        )
        upstream.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"orders":[1]}}"""))
        val other = client.newCall(post("/graphql", """{"operationName":"GetOrders"}""")).execute()
        assertEquals("""{"data":{"orders":[1]}}""", other.body!!.string())
        assertEquals(1, upstream.requestCount)

        val mocked = client.newCall(post("/graphql", """{"operationName":"GetPortfolio"}""")).execute()
        assertEquals("""{"data":{"portfolio":[]}}""", mocked.body!!.string())
        assertEquals(1, upstream.requestCount)
    }

    @Test
    fun `2 MB response is stored truncated to the first 1 MB with the real total, and the caller still gets everything`() {
        val big = "x".repeat(2 * 1024 * 1024)
        upstream.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/plain").setBody(big))
        val response = client.newCall(get("/big")).execute()
        assertEquals(big.length, response.body!!.string().length)
        val body = engine.captures.list().single().responseBody as Body.Truncated
        assertEquals(1024 * 1024, body.text.length)
        assertEquals((2 * 1024 * 1024).toLong(), body.totalBytes)
    }

    @Test
    fun `binary response keeps metadata only`() {
        upstream.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(ByteArray(18_000))))
        client.newCall(get("/logo.png")).execute().body!!.bytes()
        assertEquals(Body.Binary(18_000, "image/png"), engine.captures.list().single().responseBody)
    }

    @Test
    fun `upstream IOException is captured as an error and rethrown`() {
        upstream.shutdown()
        try {
            client.newCall(get("/down")).execute()
            fail("expected IOException")
        } catch (_: IOException) {
        }
        val tx = engine.captures.list().single()
        assertFalse(tx.mocked)
        assertNull(tx.responseCode)
        assertNotNull(tx.error)
    }

    @Test
    fun `interceptor without an engine is a pass-through`() {
        val bare = OkHttpClient.Builder().addInterceptor(DimockInterceptor({ null })).build()
        upstream.enqueue(MockResponse().setBody("hi"))
        assertEquals("hi", bare.newCall(get("/x")).execute().body!!.string())
    }
}
