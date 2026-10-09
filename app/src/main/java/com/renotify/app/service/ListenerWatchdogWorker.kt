package com.renotify.app.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodic safety net: if the notification listener lost its binding while the
 * app was in the background (aggressive OEM battery managers), this worker
 * revives it without the user having to open the app. WorkManager persists the
 * schedule across reboots and app updates on its own.
 */
class ListenerWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        ListenerGuard.kick(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "listener_watchdog"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ListenerWatchdogWorker>(30, TimeUnit.MINUTES)
                    .build()
            )
        }
    }
}
