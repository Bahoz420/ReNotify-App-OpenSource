package com.renotify.app.snooze

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.renotify.app.data.ReNotifyDatabase
import com.renotify.app.repost.Reposter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = ReNotifyDatabase.get(context).notificationDao()
                dao.getByIds(listOf(id)).firstOrNull()?.let {
                    // Sound, vibration and torch come from the entry's own
                    // delivery setup, applied inside the reposter.
                    Reposter.repost(context, listOf(it))
                }
                dao.clearScheduled(id)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "notification_id"
    }
}
