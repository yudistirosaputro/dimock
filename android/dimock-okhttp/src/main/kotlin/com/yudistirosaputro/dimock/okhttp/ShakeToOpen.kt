package com.yudistirosaputro.dimock.okhttp

import android.app.Activity
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import kotlin.math.sqrt

/**
 * Opens the inspector when the phone is shaken while any host Activity is in front (`Config.shakeToOpen`).
 *
 * Listens to the accelerometer only while an Activity is resumed, so it costs nothing in the background,
 * and ignores shakes while the inspector itself is in front so it never opens twice.
 */
internal class ShakeToOpen(private val app: Application, private val open: (Context) -> Unit) : SensorEventListener {

    private val sensorManager = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var current: Activity? = null
    private var lastShakeAt = 0L

    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            current = activity
            val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            sensorManager.registerListener(this@ShakeToOpen, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        override fun onActivityPaused(activity: Activity) {
            sensorManager?.unregisterListener(this@ShakeToOpen)
            if (current === activity) current = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    fun start() {
        if (sensorManager == null) return
        app.registerActivityLifecycleCallbacks(callbacks)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        app.unregisterActivityLifecycleCallbacks(callbacks)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val gX = event.values[0] / SensorManager.GRAVITY_EARTH
        val gY = event.values[1] / SensorManager.GRAVITY_EARTH
        val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        if (gForce < THRESHOLD_G) return
        val now = System.currentTimeMillis()
        if (now - lastShakeAt < COOLDOWN_MS) return
        lastShakeAt = now
        val activity = current ?: return
        if (activity.javaClass.name.startsWith("com.yudistirosaputro.dimock.ui.")) return
        open(activity)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val THRESHOLD_G = 2.7f
        const val COOLDOWN_MS = 1_500L
    }
}
