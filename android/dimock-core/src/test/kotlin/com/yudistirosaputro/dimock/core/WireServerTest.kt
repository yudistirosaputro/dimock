package com.yudistirosaputro.dimock.core

import com.yudistirosaputro.dimock.core.model.Body
import com.yudistirosaputro.dimock.core.model.Outcome
import com.yudistirosaputro.dimock.core.model.RequestSnapshot
import com.yudistirosaputro.dimock.core.model.Transaction
import com.yudistirosaputro.dimock.core.server.WireServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WireServerTest {

    private lateinit var engine: DimockEngine
    private lateinit var server: WireServer
    private var port = 0
    private var now = 1_000_000L

    private val login = RequestSnapshot("POST", "https://api.example.com/v1/auth/login", "api.example.com", "/v1/auth/login", emptyMap(), emptyMap(), null)

    @Before
    fun start() {
        engine = DimockEngine(HostInfo("com.yudistirosaputro.dimock.sample", "0.1.0", "0.1.0-test"), EngineConfig(clock = { now }))
        server = WireServer(engine, port = 0)
        port = server.start()
    }

    @After
    fun stop() = server.stop()

    private data class Res(val code: Int, val body: String, val headers: Map<String, List<String>>)

    /** Minimal HTTP/1.1 client over a socket: HttpURLConnection cannot send PATCH. */
    private fun call(method: String, path: String, body: String? = null, headers: Map<String, String> = emptyMap()): Res {
        Socket("127.0.0.1", port).use { s ->
            val bytes = body?.toByteArray() ?: ByteArray(0)
            val head = StringBuilder("$method $path HTTP/1.1\r\nHost: 127.0.0.1\r\n")
            headers.forEach { (k, v) -> head.append("$k: $v\r\n") }
            if (body != null) head.append("Content-Type: application/json\r\n")
            head.append("Content-Length: ${bytes.size}\r\n\r\n")
            s.getOutputStream().write(head.toString().toByteArray())
            s.getOutputStream().write(bytes)
            s.getOutputStream().flush()
            val input = s.getInputStream().bufferedReader()
            val status = input.readLine().split(' ')[1].toInt()
            val resHeaders = HashMap<String, MutableList<String>>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val (k, v) = line.split(":", limit = 2)
                resHeaders.getOrPut(k.trim()) { mutableListOf() } += v.trim()
            }
            val len = resHeaders["Content-Length"]?.first()?.toInt() ?: 0
            val buf = CharArray(len)
            var read = 0
            while (read < len) { val n = input.read(buf, read, len - read); if (n < 0) break; read += n }
            return Res(status, String(buf, 0, read), resHeaders)
        }
    }

    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject
    private fun arr(text: String) = Json.parseToJsonElement(text).jsonArray

    private val ruleJson = """{"id":"login-error","priority":10,"match":{"method":"POST","path":"/v1/auth/login"},"respond":{"status":500,"body":"{\"error\":\"internal\"}"}}"""

    @Test
    fun `health answers with protocol header and identity`() {
        val r = call("GET", "/health")
        assertEquals(200, r.code)
        assertEquals(listOf("1"), r.headers["X-Dimock-Protocol"])
        val o = obj(r.body)
        assertEquals("com.yudistirosaputro.dimock.sample", o["app"]!!.jsonPrimitive.content)
        assertEquals("1", o["protocol"]!!.jsonPrimitive.content)
        assertEquals("0", o["activeMocks"]!!.jsonPrimitive.content)
    }

    @Test
    fun `health marks the client connected and logs it once`() {
        call("GET", "/health", headers = mapOf("X-Dimock-Client" to "claude-code"))
        call("GET", "/health")
        assertTrue(engine.clientConnected)
        assertEquals("claude-code", engine.connectedClientName)
        val log = arr(call("GET", "/agent/activity").body)
        assertEquals(1, log.count { it.jsonObject["kind"]!!.jsonPrimitive.content == "connect" })
    }

    @Test
    fun `PUT rules replaces, GET rules shows runtime state, interceptor sees it`() {
        val put = call("PUT", "/rules", "[$ruleJson]")
        assertEquals(200, put.code)
        val rules = arr(call("GET", "/rules").body)
        assertEquals(1, rules.size)
        assertEquals("login-error", rules[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("0", rules[0].jsonObject["state"]!!.jsonObject["hits"]!!.jsonPrimitive.content)
        assertTrue(engine.resolve(login) is Outcome.Mock)
        assertEquals("1", arr(call("GET", "/rules").body)[0].jsonObject["state"]!!.jsonObject["hits"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST upserts, PATCH toggles and resets, DELETE removes`() {
        assertEquals(201, call("POST", "/rules", ruleJson).code)
        assertEquals(200, call("PATCH", "/rules/login-error", """{"enabled":false}""").code)
        assertEquals(Outcome.PassThrough, engine.resolve(login))
        assertEquals(200, call("PATCH", "/rules/login-error", """{"enabled":true,"reset":true}""").code)
        assertTrue(engine.resolve(login) is Outcome.Mock)
        assertEquals(204, call("DELETE", "/rules/login-error").code)
        assertEquals(404, call("DELETE", "/rules/login-error").code)
        assertEquals(204, call("DELETE", "/rules").code)
    }

    @Test
    fun `invalid rule payload is a 400 with a message`() {
        val r = call("PUT", "/rules", """[{"id":"x","match":{}}]""")
        assertEquals(400, r.code)
        assertTrue(obj(r.body)["error"]!!.jsonPrimitive.content.contains("respond"))
        assertEquals(400, call("PUT", "/rules", "not json").code)
    }

    @Test
    fun `write gating returns 403 agent_write_disabled and leaves reads working`() {
        engine.agentWriteEnabled = false
        val r = call("PUT", "/rules", "[$ruleJson]")
        assertEquals(403, r.code)
        assertEquals("agent_write_disabled", obj(r.body)["error"]!!.jsonPrimitive.content)
        assertEquals(403, call("POST", "/rules", ruleJson).code)
        assertEquals(403, call("DELETE", "/rules").code)
        assertEquals(200, call("GET", "/rules").code)
        assertEquals(200, call("GET", "/transactions").code)
        assertEquals(0, arr(call("GET", "/rules").body).size)
    }

    @Test
    fun `transactions list, filter, get and clear`() {
        engine.record(tx("a", "/v1/portfolio/summary", mocked = false, startedAt = 10))
        engine.record(tx("b", "/v1/auth/login", mocked = true, startedAt = 20))
        val all = arr(call("GET", "/transactions").body)
        assertEquals(listOf("b", "a"), all.map { it.jsonObject["id"]!!.jsonPrimitive.content })
        assertEquals(1, arr(call("GET", "/transactions?mocked=true").body).size)
        assertEquals(1, arr(call("GET", "/transactions?path=/v1/auth/*").body).size)
        assertEquals(1, arr(call("GET", "/transactions?since=10").body).size)
        assertEquals(1, arr(call("GET", "/transactions?limit=1").body).size)

        val full = obj(call("GET", "/transactions/b").body)
        assertEquals("text", full["responseBody"]!!.jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals(listOf("«redacted»"), full["requestHeaders"]!!.jsonObject["Authorization"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(404, call("GET", "/transactions/nope").code)

        assertEquals(204, call("DELETE", "/transactions").code)
        assertEquals(0, arr(call("GET", "/transactions").body).size)
    }

    @Test
    fun `agent activity records writes and reads and can be cleared`() {
        call("PUT", "/rules", "[$ruleJson]")
        call("GET", "/transactions")
        val log = arr(call("GET", "/agent/activity").body)
        val summaries = log.map { it.jsonObject["summary"]!!.jsonPrimitive.content }
        assertTrue(summaries.any { it.contains("1 rule") })
        assertTrue(log.any { it.jsonObject["call"]!!.jsonPrimitive.content == "PUT /rules" })
        assertEquals(204, call("DELETE", "/agent/activity").code)
        assertEquals(0, arr(call("GET", "/agent/activity").body).size)
    }

    @Test
    fun `events stream delivers rules_changed and transaction as SSE`() {
        val latch = CountDownLatch(4)
        val seen = mutableListOf<String>()
        val reader = Thread {
            Socket("127.0.0.1", port).use { s ->
                s.getOutputStream().write("GET /events HTTP/1.1\r\nHost: x\r\nAccept: text/event-stream\r\n\r\n".toByteArray())
                s.getOutputStream().flush()
                val br = s.getInputStream().bufferedReader()
                while (latch.count > 0) {
                    val line = br.readLine() ?: break
                    if (line.startsWith("event: ")) { synchronized(seen) { seen += line.removePrefix("event: ") }; latch.countDown() }
                }
            }
        }
        reader.isDaemon = true
        reader.start()
        Thread.sleep(150)
        call("POST", "/rules", ruleJson)
        engine.record(tx("z", "/v1/x", mocked = false, startedAt = 5))
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline && !(synchronized(seen) { "rules_changed" in seen && "transaction" in seen })) Thread.sleep(20)
        assertTrue("saw $seen", synchronized(seen) { "rules_changed" in seen && "transaction" in seen })
    }

    @Test
    fun `unknown routes are 404 JSON and the server binds loopback only`() {
        val r = call("GET", "/nope")
        assertEquals(404, r.code)
        assertNotNull(obj(r.body)["error"])
        assertEquals("127.0.0.1", server.boundAddress)
    }

    @Test
    fun `stop frees the port`() {
        server.stop()
        ServerSocket(port).close()
    }

    private fun tx(id: String, path: String, mocked: Boolean, startedAt: Long) = Transaction(
        id = id, startedAt = startedAt, durationMs = 5, method = "GET", url = "https://api.example.com$path", host = "api.example.com", path = path,
        requestHeaders = mapOf("Authorization" to listOf("Bearer secret"), "Accept" to listOf("*/*")), requestBody = null,
        responseCode = 200, responseHeaders = mapOf("Content-Type" to listOf("application/json")),
        responseBody = Body.Text("{}", "application/json"), error = null, mocked = mocked, mockRuleId = if (mocked) "r" else null,
    )
}
