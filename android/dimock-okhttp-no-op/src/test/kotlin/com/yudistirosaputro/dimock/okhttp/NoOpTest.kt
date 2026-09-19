package com.yudistirosaputro.dimock.okhttp

import android.content.ContextWrapper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket

/** Release artifact acceptance: same API, nothing runs, nothing listens. */
class NoOpTest {

    @Test
    fun `init is a no-op and opens no socket`() {
        Dimock.init(ContextWrapper(null), Dimock.Config(port = 6767))
        assertFalse(Dimock.isInitialized)
        assertFalse(Dimock.isServerRunning)
        assertEquals(-1, Dimock.port)
        val open = try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", 6767), 300); true }
        } catch (_: ConnectException) { false } catch (_: java.io.IOException) { false }
        assertFalse("something is listening on 6767; the no-op artifact must not", open)
    }

    @Test
    fun `interceptor passes requests through untouched`() {
        val server = MockWebServer().also { it.start(); it.enqueue(MockResponse().setBody("real")) }
        try {
            val client = OkHttpClient.Builder().addInterceptor(Dimock.interceptor()).build()
            val response = client.newCall(Request.Builder().url(server.url("/x")).build()).execute()
            assertEquals("real", response.body!!.string())
            assertTrue(response.headers.names().none { it.startsWith("X-Dimock") })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `no-op classes never reference the engine`() {
        // A class named DimockEngine on the classpath of this module would mean core leaked into release.
        val leaked = runCatching { Class.forName("com.yudistirosaputro.dimock.core.DimockEngine") }.isSuccess
        assertFalse("dimock-core must not be on the no-op classpath", leaked)
    }
}
