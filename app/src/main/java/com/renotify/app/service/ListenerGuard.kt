package com.renotify.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Keeps the notification listener alive on devices whose battery managers kill
 * background processes (MIUI, HyperOS, ColorOS, One UI deep sleep, ...). Losing
 * the listener binding means the history silently stops recording, which is the
 * one failure this app cannot afford.
 */
object ListenerGuard {

    private const val PREFS = "settings"
    private const val KEY_LAST_TOGGLE = "listener_last_toggle"

    /** Minimum pause between component toggles so repeated kicks stay cheap. */
    private const val TOGGLE_THROTTLE_MS = 60_000L

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun isBatteryExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Shows the system dialog asking to exclude ReNotify from battery
     * optimization. Allowed by Play policy because the core function
     * (recording notifications) is adversely affected without it.
     */
    fun requestBatteryExemption(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Some OEMs hide the dialog; fall back to the list screen.
            try {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Revives a granted-but-disconnected listener. requestRebind() alone is
     * often ignored once the system has dropped the binding after a process
     * kill, so the component is additionally flipped off and on, which forces
     * the NotificationManagerService to rebind from scratch.
     */
    fun kick(context: Context) {
        if (!hasAccess(context)) return
        if (ReNotifyListenerService.isConnected) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_TOGGLE, 0L) >= TOGGLE_THROTTLE_MS) {
            prefs.edit().putLong(KEY_LAST_TOGGLE, now).apply()
            val component = ComponentName(context, ReNotifyListenerService::class.java)
            try {
                context.packageManager.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                context.packageManager.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {
            }
        }
        ReNotifyListenerService.rebind(context)
    }
}
