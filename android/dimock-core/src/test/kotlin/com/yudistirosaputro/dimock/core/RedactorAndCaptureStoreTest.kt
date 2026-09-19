package com.yudistirosaputro.dimock.core

import com.yudistirosaputro.dimock.core.engine.BodyLimiter
import com.yudistirosaputro.dimock.core.engine.CaptureStore
import com.yudistirosaputro.dimock.core.engine.Redactor
import com.yudistirosaputro.dimock.core.model.Body
import com.yudistirosaputro.dimock.core.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {
    private val redactor = Redactor(headers = Redactor.DEFAULT_HEADERS + "X-Session", bodyPatterns = listOf(Regex("\"accountNumber\":\"\\d+\"")))

    @Test
    fun `default sensitive headers are redacted case-insensitively and the value never survives`() {
        val out = redactor.headers(mapOf("authorization" to listOf("Bearer secret-token"), "Cookie" to listOf("sid=1"), "Accept" to listOf("*/*")))
        assertEquals(listOf(Redactor.MASK), out["authorization"])
        assertEquals(listOf(Redactor.MASK), out["Cookie"])
        assertEquals(listOf("*/*"), out["Accept"])
        assertFalse(out.toString().contains("secret-token"))
    }

    @Test
    fun `custom header list is honoured`() {
        assertEquals(listOf(Redactor.MASK), redactor.headers(mapOf("X-Session" to listOf("abc")))["X-Session"])
    }

    @Test
    fun `body patterns are replaced`() {
        val out = redactor.body("""{"accountNumber":"1234567890","name":"Y"}""")
        assertEquals("""{${Redactor.MASK},"name":"Y"}""", out)
    }
}

class BodyLimiterTest {
    private val limiter = BodyLimiter(maxBodyBytes = 10)

    @Test
    fun `text within limit is kept whole`() {
        assertEquals(Body.Text("short", "application/json"), limiter.text("short", "application/json"))
    }

    @Test
    fun `text over limit is truncated with total size`() {
        val body = limiter.text("0123456789ABCDEF", "text/plain")
        assertEquals(Body.Truncated("0123456789", 16, "text/plain"), body)
    }

    @Test
    fun `binary content types keep metadata only`() {
        assertTrue(limiter.isBinary("image/png"))
        assertTrue(limiter.isBinary("application/x-protobuf"))
        assertTrue(limiter.isBinary("application/octet-stream"))
        assertFalse(limiter.isBinary("application/json; charset=utf-8"))
        assertFalse(limiter.isBinary("text/html"))
        assertFalse(limiter.isBinary("application/graphql-response+json"))
        assertEquals(Body.Binary(18_000, "image/png"), limiter.binary(18_000, "image/png"))
    }
}

class CaptureStoreTest {
    private fun tx(i: Int, bodySize: Int = 0) = Transaction(
        id = "tx-$i", startedAt = i.toLong(), durationMs = 1, method = "GET", url = "https://h/p$i", host = "h", path = "/p$i",
        requestHeaders = emptyMap(), requestBody = null, responseCode = 200, responseHeaders = emptyMap(),
        responseBody = if (bodySize > 0) Body.Text("x".repeat(bodySize), "text/plain") else null,
        error = null, mocked = i % 2 == 0, mockRuleId = null,
    )

    @Test
    fun `newest first and eviction by count`() {
        val store = CaptureStore(maxTransactions = 500, maxStoreBytes = Long.MAX_VALUE)
        repeat(501) { store.add(tx(it)) }
        val all = store.list()
        assertEquals(500, all.size)
        assertEquals("tx-500", all.first().id)
        assertEquals("tx-1", all.last().id)
    }

    @Test
    fun `eviction by bytes`() {
        val store = CaptureStore(maxTransactions = 1000, maxStoreBytes = 3_000)
        repeat(10) { store.add(tx(it, bodySize = 1_000)) }
        assertTrue(store.list().size < 10)
        assertTrue(store.sizeBytes() <= 3_000)
        assertEquals("tx-9", store.list().first().id)
    }

    @Test
    fun `filters by method, path glob and mocked`() {
        val store = CaptureStore(100, Long.MAX_VALUE)
        repeat(6) { store.add(tx(it)) }
        assertEquals(3, store.list(mocked = true).size)
        assertEquals(1, store.list(path = "/p3").size)
        assertEquals(6, store.list(path = "/p*").size)
        assertEquals(0, store.list(method = "POST").size)
        assertEquals(2, store.list(limit = 2).size)
    }

    @Test
    fun `since returns only newer transactions and get finds by id`() {
        val store = CaptureStore(100, Long.MAX_VALUE)
        repeat(5) { store.add(tx(it)) }
        assertEquals(listOf("tx-4", "tx-3"), store.list(since = 2L).map { it.id })
        assertEquals("tx-2", store.get("tx-2")?.id)
        store.clear()
        assertTrue(store.list().isEmpty())
    }
}
