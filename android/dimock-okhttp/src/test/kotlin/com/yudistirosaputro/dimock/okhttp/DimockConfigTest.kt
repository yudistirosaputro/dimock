package com.yudistirosaputro.dimock.okhttp

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import com.yudistirosaputro.dimock.core.DimockEngine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Wave 6: `Config.enabled` / `Config.inspectorTheme`, the manifest keys behind them, and the invariant
 * `dimock-ui` depends on - that `Dimock.config` is readable from an init listener registered after init.
 */
class DimockConfigTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setUp() {
        Dimock.shutdown()
        context = FakeContext(temp.newFolder("files"))
    }

    @After
    fun tearDown() = Dimock.shutdown()

    // ---- Config.enabled -------------------------------------------------------------------------------------

    @Test
    fun `enabled false stores the config but creates no engine, no server and no port`() {
        Dimock.init(context, Dimock.Config(port = 0, enabled = false))

        assertFalse(Dimock.isInitialized)
        assertFalse(Dimock.isServerRunning)
        assertEquals(-1, Dimock.port)
        assertNull(Dimock.engineOrNull())
        assertNotNull("a host must be able to read back why dimock is inert", Dimock.config)
        assertFalse(Dimock.config!!.enabled)
        assertEquals(InspectorTheme.System, Dimock.config!!.inspectorTheme)
    }

    @Test
    fun `enabled false tears down an engine and wire server that were already running`() {
        Dimock.init(context, Dimock.Config(port = 0))
        assertTrue(Dimock.isInitialized)
        assertTrue(Dimock.isServerRunning)
        val boundPort = Dimock.port
        assertTrue("expected an ephemeral bound port, got $boundPort", boundPort > 0)
        assertTrue(canConnect(boundPort))

        Dimock.init(context, Dimock.Config(port = 0, enabled = false))

        assertFalse(Dimock.isInitialized)
        assertFalse(Dimock.isServerRunning)
        assertEquals(-1, Dimock.port)
        assertFalse("the wire server socket must be closed", canConnect(boundPort))
    }

    @Test
    fun `enabled false never invokes init listeners`() {
        val fired = mutableListOf<DimockEngine>()
        Dimock.onInit { _, engine -> fired += engine }

        Dimock.init(context, Dimock.Config(port = 0, enabled = false))
        assertEquals(emptyList<DimockEngine>(), fired)

        // A listener registered afterwards must not fire either - there is no engine to hand it.
        Dimock.onInit { _, engine -> fired += engine }
        assertEquals(emptyList<DimockEngine>(), fired)
    }

    @Test
    fun `interceptor still passes every request through while dimock is disabled`() {
        Dimock.init(context, Dimock.Config(port = 0, enabled = false))
        val upstream = MockWebServer().also { it.start(); it.enqueue(MockResponse().setBody("real")) }
        try {
            val client = OkHttpClient.Builder().addInterceptor(Dimock.interceptor()).build()
            val response = client.newCall(Request.Builder().url(upstream.url("/x")).build()).execute()
            assertEquals("real", response.body!!.string())
            assertNull(response.header(DimockInterceptor.MOCK_HEADER))
            assertEquals(1, upstream.requestCount)
        } finally {
            upstream.shutdown()
        }
    }

    // ---- the invariant dimock-ui reads ----------------------------------------------------------------------

    @Test
    fun `config is readable from an init listener registered after init, like DimockUiInitProvider`() {
        // DimockInitProvider (initOrder 90) runs first and inits; DimockUiInitProvider (initOrder 80) then
        // registers a listener, which fires immediately and reads Dimock.config.
        Dimock.init(context, Dimock.Config(port = 0, startServer = false, showNotification = false))

        val seen = mutableListOf<Dimock.Config?>()
        Dimock.onInit { _, _ -> seen += Dimock.config }

        assertEquals(1, seen.size)
        assertNotNull("Dimock.config must survive init; dimock-ui reads showNotification from it", seen.single())
        assertEquals(false, seen.single()!!.showNotification)
        assertNotNull(Dimock.config)
    }

    @Test
    fun `shutdown clears the config`() {
        Dimock.init(context, Dimock.Config(port = 0, startServer = false))
        assertNotNull(Dimock.config)
        Dimock.shutdown()
        assertNull(Dimock.config)
        assertFalse(Dimock.isInitialized)
    }

    // ---- manifest keys --------------------------------------------------------------------------------------

    @Test
    fun `ENABLED given as a manifest placeholder string is honoured`() {
        // manifestPlaceholders substitute a String, so Bundle.getBoolean returns the default: the string wins.
        val (autoInit, config) = Dimock.readManifestConfig(FakeMeta(mapOf(KEY_ENABLED to "false")))
        assertTrue(autoInit)
        assertFalse(config.enabled)
    }

    @Test
    fun `ENABLED given as a real boolean is honoured`() {
        assertFalse(Dimock.readManifestConfig(FakeMeta(mapOf(KEY_ENABLED to false))).second.enabled)
        assertTrue(Dimock.readManifestConfig(FakeMeta(mapOf(KEY_ENABLED to true))).second.enabled)
    }

    @Test
    fun `ENABLED defaults to true when absent or unparseable`() {
        assertTrue(Dimock.readManifestConfig(FakeMeta(emptyMap())).second.enabled)
        assertTrue(Dimock.readManifestConfig(null).second.enabled)
        assertTrue(Dimock.readManifestConfig(FakeMeta(mapOf(KEY_ENABLED to "yes"))).second.enabled)
    }

    @Test
    fun `AUTO_INIT and SHAKE_TO_OPEN also accept the placeholder string form`() {
        assertFalse(Dimock.readManifestConfig(FakeMeta(mapOf(KEY_AUTO_INIT to "false"))).first)
        assertTrue(Dimock.readManifestConfig(FakeMeta(mapOf(KEY_SHAKE to "true"))).second.shakeToOpen)
    }

    @Test
    fun `INSPECTOR_THEME is case-insensitive and unknown values fall back to System`() {
        fun theme(value: String?) = Dimock.readManifestConfig(FakeMeta(value?.let { mapOf(KEY_THEME to it) } ?: emptyMap())).second.inspectorTheme
        assertEquals(InspectorTheme.Dark, theme("dark"))
        assertEquals(InspectorTheme.Dark, theme("DARK"))
        assertEquals(InspectorTheme.Light, theme("Light"))
        assertEquals(InspectorTheme.System, theme("system"))
        assertEquals(InspectorTheme.System, theme("neon"))
        assertEquals(InspectorTheme.System, theme(null))
        assertEquals(InspectorTheme.System, Dimock.readManifestConfig(null).second.inspectorTheme)
    }

    @Test
    fun `PORT is still read and defaults to 6767`() {
        assertEquals(7000, Dimock.readManifestConfig(FakeMeta(mapOf(KEY_PORT to 7000))).second.port)
        assertEquals(DimockEngine.DEFAULT_PORT, Dimock.readManifestConfig(FakeMeta(emptyMap())).second.port)
    }

    private fun canConnect(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300); true }
    } catch (_: java.io.IOException) {
        false
    }

    private companion object {
        const val KEY_AUTO_INIT = "com.yudistirosaputro.dimock.AUTO_INIT"
        const val KEY_PORT = "com.yudistirosaputro.dimock.PORT"
        const val KEY_SHAKE = "com.yudistirosaputro.dimock.SHAKE_TO_OPEN"
        const val KEY_ENABLED = "com.yudistirosaputro.dimock.ENABLED"
        const val KEY_THEME = "com.yudistirosaputro.dimock.INSPECTOR_THEME"
    }
}

/**
 * Mirrors `android.os.Bundle`: `getBoolean` returns the default when the entry is a String (what a
 * `manifestPlaceholders` substitution produces), and `getString` returns null when it is a real boolean.
 */
private class FakeMeta(private val values: Map<String, Any>) : ManifestMeta {
    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
    override fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default
    override fun getString(key: String): String? = values[key] as? String
}

/** The little bit of Context that [Dimock.init] touches, without Robolectric. */
private class FakeContext(private val files: File) : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this
    override fun getPackageName(): String = "com.example.host"
    override fun getPackageManager(): PackageManager = throw UnsupportedOperationException("no PackageManager in a JVM unit test")
    override fun getFilesDir(): File = files
}
