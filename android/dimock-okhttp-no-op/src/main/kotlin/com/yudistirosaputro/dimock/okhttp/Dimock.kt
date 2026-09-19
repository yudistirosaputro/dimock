package com.yudistirosaputro.dimock.okhttp

import android.content.Context
import android.content.Intent
import okhttp3.Interceptor

/**
 * Release artifact. Same public surface as the debug artifact, no behaviour: no server, no storage,
 * no notification, no ContentProvider. `interceptor()` is a plain pass-through.
 */
object Dimock {

    data class Config(
        val port: Int = 6767,
        val maxTransactions: Int = 500,
        val maxStoreBytes: Long = 50L * 1024 * 1024,
        val maxBodyBytes: Int = 1024 * 1024,
        val redactHeaders: Set<String> = setOf("Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie", "X-Api-Key"),
        val redactBodyPatterns: List<Regex> = emptyList(),
        val showNotification: Boolean = true,
        val shakeToOpen: Boolean = false,
        val startServer: Boolean = true,
        val agentWriteEnabled: Boolean = true,
        val enabled: Boolean = true,
        val inspectorTheme: InspectorTheme = InspectorTheme.System,
    )

    const val VERSION = "0.1.0-alpha01"

    private val passThrough = Interceptor { chain -> chain.proceed(chain.request()) }

    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context, config: Config = Config()): Dimock = this

    fun interceptor(): Interceptor = passThrough

    @Suppress("UNUSED_PARAMETER")
    fun onInit(listener: (Context, Any) -> Unit) = Unit

    val isInitialized: Boolean get() = false
    val isServerRunning: Boolean get() = false
    val port: Int get() = -1

    val config: Config? get() = null

    @Suppress("UNUSED_PARAMETER")
    fun launch(context: Context) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun launchIntent(context: Context): Intent? = null

    fun shutdown() = Unit
}
