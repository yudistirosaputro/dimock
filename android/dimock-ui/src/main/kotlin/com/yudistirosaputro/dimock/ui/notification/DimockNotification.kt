package com.yudistirosaputro.dimock.ui.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yudistirosaputro.dimock.core.DimockEngine
import com.yudistirosaputro.dimock.core.engine.ActivityEntry
import com.yudistirosaputro.dimock.core.engine.Labels
import com.yudistirosaputro.dimock.okhttp.Dimock
import com.yudistirosaputro.dimock.ui.DimockInspectorActivity
import com.yudistirosaputro.dimock.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The single ongoing, silent notification that fronts the inspector — the way in, like Chucker's.
 *
 * Title `dimock · N calls[ · M mocks]`, body = the newest calls, one per line (`GET /posts · 200 · 241 ms`).
 * Captures arrive far faster than a notification can be redrawn, so every engine event only *schedules* a
 * repost: the summary is rebuilt at most once per [THROTTLE_MS] and posted only when it changed.
 * Tap opens Traffic; *Clear* empties the buffer; *Disable mocks* appears while any rule is enabled.
 */
object DimockNotification {

    private const val CHANNEL = "dimock"
    private const val ID = 0x4449 // "DI"
    private const val THROTTLE_MS = 750L
    private const val LINES = 5

    private const val REQUEST_OPEN = 1
    private const val REQUEST_CLEAR = 2
    private const val REQUEST_DISABLE = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var attached: DimockEngine? = null
    private val pending = AtomicBoolean(false)
    private var job: Job? = null
    private var last: Summary? = null

    private data class Summary(val title: String, val lines: List<String>, val disableAction: Boolean)

    /** Idempotent per engine: re-attaches when `Dimock.init` builds a new engine, posts the first summary. */
    @Synchronized
    fun install(context: Context, engine: DimockEngine) {
        if (attached === engine) return
        attached = engine
        last = null
        val app = context.applicationContext
        createChannel(app)
        engine.rules.addListener { schedule(app, engine) }
        engine.captures.addListener { schedule(app, engine) }
        render(app, engine)
    }

    /** Re-post now, e.g. when the inspector resumes and POST_NOTIFICATIONS may have just been granted. */
    fun refresh(context: Context) {
        val engine = Dimock.engineOrNull() ?: return
        last = null
        render(context.applicationContext, engine)
    }

    fun cancel(context: Context) {
        job?.cancel()
        manager(context).cancel(ID)
        last = null
    }

    private fun schedule(context: Context, engine: DimockEngine) {
        if (!pending.compareAndSet(false, true)) return
        job = scope.launch {
            delay(THROTTLE_MS)
            pending.set(false)
            render(context, engine)
        }
    }

    private fun render(context: Context, engine: DimockEngine) {
        if (!canPost(context)) return
        val summary = summarise(engine)
        if (summary == last) return
        last = summary
        manager(context).notify(ID, build(context, summary))
    }

    private fun summarise(engine: DimockEngine): Summary {
        val calls = engine.captures.count()
        val active = engine.rules.activeCount()
        val enabledAny = engine.rules.entries().any { it.rule.enabled }
        val lines = engine.captures.list(limit = LINES).map(Labels::trafficLine).ifEmpty { listOf(Labels.WAITING) }
        return Summary(Labels.notificationTitle(calls, active), lines, enabledAny)
    }

    private fun build(context: Context, summary: Summary) = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_dimock_notification)
        .setContentTitle(summary.title)
        .setContentText(summary.lines.first())
        .setStyle(NotificationCompat.InboxStyle().also { style -> summary.lines.forEach { style.addLine(it) } })
        .setContentIntent(openIntent(context))
        .setOngoing(true)
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setColor(ACCENT)
        .addAction(0, context.getString(R.string.dimock_notif_clear), broadcast(context, REQUEST_CLEAR, DimockNotificationReceiver.ACTION_CLEAR))
        .apply {
            if (summary.disableAction) {
                addAction(0, context.getString(R.string.dimock_notif_disable_mocks), broadcast(context, REQUEST_DISABLE, DimockNotificationReceiver.ACTION_DISABLE_ALL))
            }
        }
        .build()

    private fun openIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(context, REQUEST_OPEN, DimockInspectorActivity.intent(context), flags())

    private fun broadcast(context: Context, request: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(context, request, Intent(context, DimockNotificationReceiver::class.java).setAction(action), flags())

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.dimock_notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.dimock_notif_channel_description)
            setShowBadge(false)
        }
        manager(context).createNotificationChannel(channel)
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun manager(context: Context) = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun flags() = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

    private val ACCENT = 0xFFC6F135.toInt()
}

/** Handles the notification's actions. Both are also available inside the inspector. */
class DimockNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = Dimock.engineOrNull() ?: return
        when (intent.action) {
            ACTION_CLEAR -> {
                engine.captures.clear()
                DimockNotification.refresh(context)
            }
            ACTION_DISABLE_ALL -> {
                engine.rules.entries().forEach { engine.rules.setEnabled(it.rule.id, false) }
                engine.logActivity(ActivityEntry.Kind.WRITE, "Disabled all mocks from the notification", "local")
            }
        }
    }

    companion object {
        const val ACTION_CLEAR = "com.yudistirosaputro.dimock.ui.CLEAR"
        const val ACTION_DISABLE_ALL = "com.yudistirosaputro.dimock.ui.DISABLE_ALL"
    }
}
