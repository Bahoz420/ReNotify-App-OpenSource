package com.renotify.app.snooze

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.renotify.app.data.ReNotifyDatabase
import com.renotify.app.repost.Reposter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager alarms are cleared on reboot and on app updates. This receiver re-arms
 * every persisted reminder; anything that should have fired in the meantime is
 * delivered immediately.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = ReNotifyDatabase.get(context).notificationDao()
                val now = System.currentTimeMillis()
                for (item in dao.getScheduled()) {
                    val triggerAt = item.scheduledFor ?: continue
                    if (triggerAt > now) {
                        Snoozer.setAlarm(context, item.id, triggerAt)
                    } else {
                        Reposter.repost(context, listOf(item))
                        dao.clearScheduled(item.id)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
