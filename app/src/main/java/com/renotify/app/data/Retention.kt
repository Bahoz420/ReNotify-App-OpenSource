package com.renotify.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.renotify.app.widget.HistoryWidget
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Retention: how many days of history to keep. Off by default, and a free
 * setting, because deleting your own data is never something to pay for.
 *
 * Entries with a pending reminder are never pruned; they go when they fire,
 * or when the user cancels them. Everything else older than the window is
 * deleted once a day by [RetentionWorker], and right away when the setting
 * changes or the process starts.
 */
object Retention {

    /** Allowed values in days; 0 means keep everything. */
    val OPTIONS = listOf(0, 1, 3, 7, 30, 90)

    private const val PREFS = "settings"
    private const val KEY_DAYS = "retention_days"
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun days(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_DAYS, 0)

    fun setDays(context: Context, days: Int) {
        require(days in OPTIONS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DAYS, days).apply()
        pruneAsync(context)
    }

    /** Deletes what the window no longer covers. Returns how many rows went. */
    suspend fun prune(context: Context): Int {
        val days = days(context)
        if (days <= 0) return 0
        val cutoff = System.currentTimeMillis() - days * DAY_MS
        val removed = ReNotifyDatabase.get(context).notificationDao().deleteOlderThan(cutoff)
        if (removed > 0) HistoryWidget.refresh(context)
        return removed
    }

    fun pruneAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch { prune(appContext) }
    }

    /** Daily run; the exact hour does not matter, the window is in days. */
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "retention",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS).build()
        )
    }
}

class RetentionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Retention.prune(applicationContext)
        return Result.success()
    }
}
