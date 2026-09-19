package com.yudistirosaputro.dimock.okhttp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.yudistirosaputro.dimock.core.EngineConfig
import com.yudistirosaputro.dimock.core.HostInfo
import com.yudistirosaputro.dimock.core.DimockEngine
import com.yudistirosaputro.dimock.core.engine.FileRuleStorage
import com.yudistirosaputro.dimock.core.engine.Redactor
import com.yudistirosaputro.dimock.core.server.WireServer
import okhttp3.Interceptor
import java.io.File

/**
 * Public entry point of the debug artifact. The `dimock-okhttp-no-op` artifact ships the same signatures
 * with empty bodies, so host code never needs a `BuildConfig.DEBUG` check.
 *
 * ```kotlin
 * // usually done for you by DimockInitProvider; call explicitly to customise
 * Dimock.init(context, Dimock.Config(port = 6767))
 * OkHttpClient.Builder().addInterceptor(Dimock.interceptor()).build()
 * ```
 */
object Dimock {

    data class Config(
        val port: Int = DimockEngine.DEFAULT_PORT,
        val maxTransactions: Int = 500,
        val maxStoreBytes: Long = 50L * 1024 * 1024,
        val maxBodyBytes: Int = 1024 * 1024,
        val redactHeaders: Set<String> = Redactor.DEFAULT_HEADERS,
        val redactBodyPatterns: List<Regex> = emptyList(),
        /**
         * The Chucker-style ongoing notification (`dimock · N calls`, newest calls listed, tap opens the inspector).
         * Rendered by `dimock-ui`; ignored when that module is absent.
         */
        val showNotification: Boolean = true,
        /** Shake any Activity of the host app to open the inspector. Needs no permission; off by default. */
        val shakeToOpen: Boolean = false,
        /** Start the loopback wire server. Off means captures and rules still work, but no agent can reach them. */
        val startServer: Boolean = true,
        /** Initial value of the per-device "agent may change mock rules" switch. */
        val agentWriteEnabled: Boolean = true,
    )

    const val VERSION = "0.1.0"
    private const val TAG = "dimock"
    private const val INSPECTOR_ACTIVITY = "com.yudistirosaputro.dimock.ui.DimockInspectorActivity"

    @Volatile private var engine: DimockEngine? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var server: WireServer? = null
    @Volatile private var _config: Config? = null
    @Volatile private var shake: ShakeToOpen? = null
    private val initListeners = java.util.concurrent.CopyOnWriteArrayList<(Context, DimockEngine) -> Unit>()

    /** The config passed to [init], or null before init. */
    val config: Config? get() = _config

    private val lazyInterceptor: DimockInterceptor by lazy { DimockInterceptor({ engine }) }

    /** Idempotent. A second call with a different config restarts the server on the new port. */
    @Synchronized
    fun init(context: Context, config: Config = Config()): Dimock {
        val app = context.applicationContext
        if (this._config == config && engine != null) return this
        shutdown()
        appContext = app
        val versionName = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
        val engineConfig = EngineConfig(
            maxTransactions = config.maxTransactions,
            maxStoreBytes = config.maxStoreBytes,
            maxBodyBytes = config.maxBodyBytes,
            redactHeaders = config.redactHeaders,
            redactBodyPatterns = config.redactBodyPatterns,
            ruleStorage = FileRuleStorage(File(app.filesDir, "dimock/rules.json")),
            agentWriteEnabled = config.agentWriteEnabled,
        )
        val created = DimockEngine(HostInfo(app.packageName, versionName, VERSION), engineConfig)
        engine = created
        this._config = config
        if (config.shakeToOpen && app is android.app.Application) shake = ShakeToOpen(app) { launch(it) }.also { it.start() }
        for (listener in initListeners) listener(app, created)
        if (config.startServer) {
            server = try {
                WireServer(created, config.port).also {
                    val bound = it.start()
                    Log.i(TAG, "wire server listening on 127.0.0.1:$bound for ${app.packageName} (protocol ${DimockEngine.PROTOCOL_VERSION})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "could not start wire server on port ${config.port}: ${e.message}. Captures and rules still work locally.")
                null
            }
        }
        return this
    }

    /** Safe to call before [init]: passes requests through until the engine exists. */
    fun interceptor(): Interceptor = lazyInterceptor

    /**
     * Runs [listener] now if the engine exists, and again after every future [init]. `dimock-ui` uses it to
     * attach the notification whether the host auto-inits or calls [init] itself later.
     */
    fun onInit(listener: (Context, DimockEngine) -> Unit) {
        initListeners += listener
        val current = engine ?: return
        listener(appContext ?: return, current)
    }

    val isInitialized: Boolean get() = engine != null
    val isServerRunning: Boolean get() = server != null
    val port: Int get() = server?.boundPort ?: -1

    /** The engine behind the interceptor; `dimock-ui` and tests build on it. */
    fun engine(): DimockEngine = engine ?: error("Dimock.init(context) has not been called")

    fun engineOrNull(): DimockEngine? = engine

    /**
     * Opens the inspector when `dimock-ui` is on the classpath; logs a hint otherwise. The notification is the
     * usual way in; this is for a debug menu or a button. Resolved by name so this module never depends on Compose.
     */
    fun launch(context: Context) {
        val intent = launchIntent(context) ?: return
        context.startActivity(intent)
    }

    /**
     * The Intent that opens the inspector, for callers that build their own PendingIntent or menu entry.
     * Null before [init] or when `dimock-ui` is absent (a warning is logged in both cases).
     */
    fun launchIntent(context: Context): Intent? {
        if (engine == null) {
            Log.w(TAG, "launchIntent() before init(); nothing to show")
            return null
        }
        return try {
            Intent(context, Class.forName(INSPECTOR_ACTIVITY)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } catch (_: ClassNotFoundException) {
            Log.w(TAG, "dimock-ui is not on the classpath; add debugImplementation(\"io.github.yudistirosaputro:dimock-ui\") for the inspector")
            null
        }
    }

    @Synchronized
    fun shutdown() {
        shake?.stop()
        shake = null
        server?.stop()
        server = null
        engine = null
        _config = null
    }

    internal fun readManifestConfig(context: Context): Pair<Boolean, Config> {
        val meta = runCatching {
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        }.getOrNull()
        val autoInit = meta?.getBoolean("com.yudistirosaputro.dimock.AUTO_INIT", true) ?: true
        val port = meta?.getInt("com.yudistirosaputro.dimock.PORT", DimockEngine.DEFAULT_PORT) ?: DimockEngine.DEFAULT_PORT
        val shake = meta?.getBoolean("com.yudistirosaputro.dimock.SHAKE_TO_OPEN", false) ?: false
        return autoInit to Config(port = port, shakeToOpen = shake)
    }
}
