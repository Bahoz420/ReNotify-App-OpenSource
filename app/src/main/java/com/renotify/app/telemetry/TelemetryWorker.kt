package com.renotify.app.telemetry

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.renotify.app.BuildConfig
import com.renotify.app.data.ReNotifyDatabase
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * Daily ping to the statistics server (see [Telemetry] for what is sent) and
 * upload of a pending crash report if the last session died. Fails soft: any
 * network or server error just retries on the next run.
 */
class TelemetryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!Telemetry.isEnabled(context)) return Result.success()

        try {
            sendPendingCrash(context)

            val payload = JSONObject()
                .put("device", Telemetry.installId(context))
                .put("version", BuildConfig.VERSION_NAME)
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("locale", Locale.getDefault().toLanguageTag())
                .put(
                    "notifTotal",
                    ReNotifyDatabase.get(context).notificationDao().count()
                )
            post("/api/ping", payload)
            Telemetry.prefs(context).edit()
                .putLong(Telemetry.KEY_LAST_PING, System.currentTimeMillis())
                .apply()
            return Result.success()
        } catch (_: Exception) {
            return Result.retry()
        }
    }

    private fun sendPendingCrash(context: Context) {
        val prefs = Telemetry.prefs(context)
        val pending = prefs.getString(Telemetry.KEY_PENDING_CRASH, null) ?: return
        val newline = pending.indexOf('\n')
        if (newline <= 0) {
            prefs.edit().remove(Telemetry.KEY_PENDING_CRASH).apply()
            return
        }
        val payload = JSONObject()
            .put("device", Telemetry.installId(context))
            .put("version", BuildConfig.VERSION_NAME)
            .put("at", pending.substring(0, newline).toLongOrNull() ?: 0L)
            .put("stack", pending.substring(newline + 1))
        post("/api/error", payload)
        prefs.edit().remove(Telemetry.KEY_PENDING_CRASH).apply()
    }

    private fun post(path: String, payload: JSONObject) {
        val connection =
            URL(Telemetry.BASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val WORK_NAME = "telemetry_ping"
        private const val WORK_NAME_NOW = "telemetry_ping_now"

        /** A device counts as active once a day, so anything below is noise. */
        private const val MIN_PING_INTERVAL_MS = 20L * 60 * 60 * 1000

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Pings from the app process if the last successful ping is more than
         * [MIN_PING_INTERVAL_MS] ago. Cheap no-op otherwise, so it can be
         * called on every start.
         */
        fun pingIfDue(context: Context) {
            if (!Telemetry.isEnabled(context)) return
            val last = Telemetry.prefs(context).getLong(Telemetry.KEY_LAST_PING, 0L)
            val since = System.currentTimeMillis() - last
            // A negative value means the clock jumped backwards; ping anyway
            // instead of going silent until it catches up.
            if (last != 0L && since in 0 until MIN_PING_INTERVAL_MS) return
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<TelemetryWorker>()
                    .setConstraints(constraints())
                    .build()
            )
        }

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TelemetryWorker>(24, TimeUnit.HOURS)
                    .setConstraints(constraints())
                    .build()
            )
        }
    }
}
