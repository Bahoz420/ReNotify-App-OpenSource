package com.renotify.app.telemetry

import android.content.Context
import com.renotify.app.BuildConfig
import java.util.UUID

/**
 * Minimal usage statistics for the server set in TELEMETRY_URL, sent only
 * after the user agreed. What leaves the device: a random install id, app
 * version, Android version, language, the COUNT of stored notifications, and
 * crash traces; the Play edition adds whether Pro is unlocked. Notification content, titles, app names or
 * anything readable never leave the device.
 *
 * The install id stays the same between pings, so this is pseudonymous, not
 * anonymous, and the texts say so.
 */
object Telemetry {

    /** From the build, see TELEMETRY_URL in app/build.gradle.kts. */
    val BASE_URL: String = BuildConfig.TELEMETRY_URL.trimEnd('/')

    /** False in a build without a statistics server: nothing to send, nothing to ask. */
    val isAvailable: Boolean get() = BASE_URL.isNotEmpty()

    private const val PREFS = "telemetry"
    private const val KEY_ID = "install_id"
    /** Legacy opt-out switch from before consent, read once by [consent]. */
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CONSENT = "consent"
    private const val CONSENT_GRANTED = "granted"
    private const val CONSENT_DENIED = "denied"
    const val KEY_PENDING_CRASH = "pending_crash"
    const val KEY_LAST_PING = "last_ping_at"

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * true, false, or null while the user has not been asked. Before consent
     * existed there was only an opt-out switch: whoever turned that off has
     * already said no and is not asked again. Everyone else is asked, because
     * a switch that was on by default was never anybody's yes.
     */
    fun consent(context: Context): Boolean? {
        val p = prefs(context)
        return when (p.getString(KEY_CONSENT, null)) {
            CONSENT_GRANTED -> true
            CONSENT_DENIED -> false
            else -> if (p.contains(KEY_ENABLED) && !p.getBoolean(KEY_ENABLED, true)) false else null
        }
    }

    fun needsConsent(context: Context): Boolean = isAvailable && consent(context) == null

    fun isEnabled(context: Context): Boolean = isAvailable && consent(context) == true

    fun setEnabled(context: Context, enabled: Boolean) {
        val edit = prefs(context).edit()
            .putString(KEY_CONSENT, if (enabled) CONSENT_GRANTED else CONSENT_DENIED)
            .remove(KEY_ENABLED)
        if (!enabled) {
            // A later yes starts as a new installation, unlinked to anything
            // sent before, and an unsent crash report is not sent at all.
            edit.remove(KEY_ID).remove(KEY_PENDING_CRASH).remove(KEY_LAST_PING)
        }
        edit.apply()
        if (enabled) TelemetryWorker.pingIfDue(context)
    }

    /** Random UUID, generated once per install. Not tied to the device. */
    fun installId(context: Context): String {
        val p = prefs(context)
        p.getString(KEY_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        p.edit().putString(KEY_ID, id).apply()
        return id
    }

    /** Call once from Application.onCreate. */
    fun init(context: Context) {
        installCrashHandler(context)
        TelemetryWorker.schedule(context)
        // The periodic worker alone is not reliable enough for daily numbers:
        // its 24 h window can slide to 48 h, and OEM battery killers drop the
        // job entirely. A throttled ping at process start closes that gap.
        TelemetryWorker.pingIfDue(context)
    }

    /**
     * Stores a crash summary locally; the next telemetry ping uploads it.
     * Never touches the network inside the crash handler itself.
     */
    private fun installCrashHandler(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (isEnabled(appContext)) {
                    val stack = android.util.Log.getStackTraceString(throwable).take(4000)
                    prefs(appContext).edit()
                        .putString(
                            KEY_PENDING_CRASH,
                            "${System.currentTimeMillis()}\n$stack"
                        )
                        .commit() // synchronous on purpose - the process is dying
                }
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
