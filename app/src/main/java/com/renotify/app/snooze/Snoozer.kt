package com.renotify.app.snooze

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.renotify.app.data.ReNotifyDatabase
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Snoozer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Whether exact alarms are allowed. Below Android 12 they always are; from 12 on
     * the user can grant "Alarms & reminders" in system settings.
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /**
     * Schedules the reminder alarm and persists it, so it survives reboots and app
     * updates (see [BootReceiver]) and shows up in the Snoozed tab.
     */
    fun schedule(context: Context, notificationId: Long, triggerAtMillis: Long) {
        setAlarm(context, notificationId, triggerAtMillis)
        val appContext = context.applicationContext
        scope.launch {
            ReNotifyDatabase.get(appContext).notificationDao()
                .setScheduled(notificationId, triggerAtMillis)
        }
    }

    /** Alarm only, no DB write; used by [BootReceiver] to re-arm persisted entries. */
    fun setAlarm(context: Context, notificationId: Long, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context, notificationId)
        if (canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
            )
        } else {
            // Still fires with the app closed, just possibly a few minutes late.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
            )
        }
    }

    /** Cancels a pending reminder and clears its persisted schedule. */
    fun cancel(context: Context, notificationId: Long) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(pendingIntent(context, notificationId))
        val appContext = context.applicationContext
        scope.launch {
            ReNotifyDatabase.get(appContext).notificationDao().clearScheduled(notificationId)
        }
    }

    private fun pendingIntent(context: Context, notificationId: Long): PendingIntent {
        val intent = Intent(context, SnoozeReceiver::class.java)
            .putExtra(SnoozeReceiver.EXTRA_ID, notificationId)
        return PendingIntent.getBroadcast(
            context,
            notificationId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun inOneHour(): Long = System.currentTimeMillis() + 60 * 60 * 1000L

    fun thisEvening(): Long = nextOccurrence(hourOfDay = 19)

    fun tomorrowMorning(): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun nextOccurrence(hourOfDay: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis() + 60_000) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
