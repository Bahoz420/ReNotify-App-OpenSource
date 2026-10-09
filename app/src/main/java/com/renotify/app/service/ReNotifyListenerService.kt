package com.renotify.app.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.renotify.app.data.Delivery
import com.renotify.app.data.ReNotifyDatabase
import com.renotify.app.data.StoredNotification
import com.renotify.app.repost.IntentCache
import com.renotify.app.repost.Reposter
import com.renotify.app.rules.RulesEngine
import com.renotify.app.rules.ShadeFilter
import com.renotify.app.widget.HistoryWidget
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ReNotifyListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Keys this service cancelled to silence them, see [onNotificationRemoved]. */
    private val ownCancels: MutableSet<String> = Collections.synchronizedSet(HashSet())

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        if (isIgnored(sbn, notification)) return

        // Boost: remember the original click action while it is still alive so
        // a resend within the window opens the exact original destination.
        IntentCache.put(sbn.key, notification.contentIntent)

        val (title, text) = contentOf(notification) ?: return
        val appLabel = labelOf(sbn.packageName)

        val isProtected = isProtected(sbn.packageName, notification)

        // Rules: a hide match takes the notification out of the shade
        // immediately but still archives it below, so nothing is ever lost.
        val rule =
            if (isProtected) null else RulesEngine.match(this, sbn.packageName, title, text)

        // A silence match swaps the original for a quiet copy, see below. When
        // ReNotify itself may not post, there would be no copy, and silencing
        // would turn into hiding; then the original simply stays.
        val silencingRule = rule?.takeIf {
            it.silences && Reposter.canPost(this, Delivery.SILENT)
        }
        val matchedRule = rule?.takeIf { !it.silences }

        // The shade allow list hides every app the user did not pick. Rules run
        // first, they are the more specific instruction, and that includes a
        // silence rule: it asked to keep the notification, only quietly.
        val hiddenByShadeFilter = !isProtected && rule == null &&
            ShadeFilter.hides(this, sbn.packageName)

        if (matchedRule != null || hiddenByShadeFilter || silencingRule != null) {
            // Android starts the sound and vibration before any listener hears
            // about the notification. Cancelling the original is the one lever
            // there is: the system stops whatever of it is still playing.
            if (silencingRule != null) ownCancels.add(sbn.key)
            try {
                cancelNotification(sbn.key)
            } catch (_: Exception) {
                ownCancels.remove(sbn.key)
            }
        }

        val db = ReNotifyDatabase.get(this)
        val dao = db.notificationDao()
        scope.launch {
            if (matchedRule != null || hiddenByShadeFilter) {
                dao.insert(
                    StoredNotification(
                        sbnKey = sbn.key,
                        packageName = sbn.packageName,
                        appLabel = appLabel,
                        title = title,
                        text = text,
                        postedAt = sbn.postTime,
                        blocked = true,
                    )
                )
                if (matchedRule != null) db.ruleDao().incrementMatch(matchedRule.id)
                HistoryWidget.refresh(this@ReNotifyListenerService)
                return@launch
            }
            val existing = dao.findActiveByKey(sbn.key)
            val stored = if (existing != null) {
                // Same notification updated in place (e.g. messaging apps) - keep one row.
                existing.copy(title = title, text = text, postedAt = sbn.postTime)
                    .also { dao.update(it) }
            } else {
                StoredNotification(
                    sbnKey = sbn.key,
                    packageName = sbn.packageName,
                    appLabel = appLabel,
                    title = title,
                    text = text,
                    postedAt = sbn.postTime,
                ).let { it.copy(id = dao.insert(it)) }
            }
            if (silencingRule != null) {
                // The quiet copy: same row id, so an update of the original
                // replaces it in the shade instead of stacking a second one.
                // It carries the original tap target while that is alive.
                Reposter.repost(
                    this@ReNotifyListenerService, listOf(stored), Delivery.SILENT
                )
                db.ruleDao().incrementMatch(silencingRule.id)
            }
            HistoryWidget.refresh(this@ReNotifyListenerService)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        // Taken away by a silence rule, not by the user: the notification is
        // still in the shade as a quiet copy, so it was not removed.
        if (ownCancels.remove(sbn.key)) return
        val dao = ReNotifyDatabase.get(this).notificationDao()
        scope.launch {
            dao.markRemoved(sbn.key, System.currentTimeMillis())
        }
    }

    /**
     * Never hidden, no matter which filter asks for it: media playback
     * (cancelling it can kill the player), alarms, calls, navigation, calendar
     * reminders, anything that asked for a full screen alert, and foreground
     * services. These are recorded normally instead.
     */
    private fun isProtected(packageName: String, notification: Notification): Boolean =
        notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 ||
            notification.category in PROTECTED_CATEGORIES ||
            notification.fullScreenIntent != null ||
            notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
            isCalendar(packageName)

    /**
     * A calendar reminder is time critical in the same way an alarm is: hiding
     * it means the appointment is missed, and unlike a chat message it is
     * worthless once the meeting has started. Most calendar apps tag their
     * reminders with CATEGORY_EVENT or CATEGORY_REMINDER, which the category
     * set above already covers, but plenty of them set no category at all, so
     * the package is checked as well. A false positive here only means one
     * notification too many stays visible, which is the harmless direction.
     */
    private fun isCalendar(packageName: String): Boolean =
        packageName in CALENDAR_PACKAGES || packageName.contains("calendar", ignoreCase = true)

    /** Notifications this app never touches, in any code path. */
    private fun isIgnored(sbn: StatusBarNotification, notification: Notification): Boolean {
        if (sbn.packageName == packageName) return true
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return true
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return true
        val excluded = getSharedPreferences("settings", MODE_PRIVATE)
            .getStringSet("excluded_packages", emptySet()) ?: emptySet()
        return sbn.packageName in excluded
    }

    /** Title and body, or null when there is neither. */
    private fun contentOf(notification: Notification): Pair<String, String>? {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return title to text
    }

    private fun labelOf(packageName: String): String = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        packageName
    }

    /**
     * Applies rules and the shade allow list to what is already hanging in the
     * status bar. Two jobs, both of them catching up on decisions that were
     * never made:
     *
     * - Switching a filter on should clean the shade, not only change what
     *   happens to the next notification.
     * - The system kills this process whenever it needs the memory, which on an
     *   idle phone overnight is most of the time. Everything that arrived while
     *   the listener was gone is still sitting in the shade unjudged when it
     *   comes back, which made a night rule look like it had stopped working.
     *
     * The alert itself cannot be undone. Android plays the sound the moment the
     * source app posts, long before any listener is asked about it.
     */
    private fun sweep() {
        val active = try {
            activeNotifications
        } catch (_: Exception) {
            null
        } ?: return

        val db = ReNotifyDatabase.get(this)
        val dao = db.notificationDao()
        for (sbn in active) {
            val notification = sbn.notification ?: continue
            if (isIgnored(sbn, notification)) continue
            if (isProtected(sbn.packageName, notification)) continue
            val (title, text) = contentOf(notification) ?: continue

            val matchedRule = RulesEngine.match(this, sbn.packageName, title, text)
            // A silence rule has nothing left to do here: whatever is already in
            // the shade has made its sound, and it is allowed to stay.
            if (matchedRule != null && matchedRule.silences) continue
            val hiddenByShadeFilter =
                matchedRule == null && ShadeFilter.hides(this, sbn.packageName)
            if (matchedRule == null && !hiddenByShadeFilter) continue

            try {
                cancelNotification(sbn.key)
            } catch (_: Exception) {
            }

            val appLabel = labelOf(sbn.packageName)
            scope.launch {
                // Already archived means this one went through
                // onNotificationPosted normally and the sweep is only cleaning
                // up the shade.
                if (dao.findByKeyAt(sbn.key, sbn.postTime) != null) return@launch
                dao.insert(
                    StoredNotification(
                        sbnKey = sbn.key,
                        packageName = sbn.packageName,
                        appLabel = appLabel,
                        title = title,
                        text = text,
                        postedAt = sbn.postTime,
                        blocked = true,
                    )
                )
                if (matchedRule != null) db.ruleDao().incrementMatch(matchedRule.id)
                HistoryWidget.refresh(this@ReNotifyListenerService)
            }
        }
    }

    override fun onListenerConnected() {
        isConnected = true
        instance = this
        Reposter.ensureChannel(this)
        // Catch up on everything that arrived while the process was gone.
        scope.launch { sweep() }
    }

    override fun onListenerDisconnected() {
        isConnected = false
        instance = null
        // Aggressive OEMs (MIUI etc.) kill the process; ask the system to rebind.
        requestRebind(ComponentName(this, ReNotifyListenerService::class.java))
    }

    override fun onDestroy() {
        isConnected = false
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Categories a rule must never hide, no matter how broad it is. */
        private val PROTECTED_CATEGORIES = setOf(
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_EVENT,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_REMINDER,
            Notification.CATEGORY_TRANSPORT,
        )

        /** Calendar apps whose package name does not contain "calendar". */
        private val CALENDAR_PACKAGES = setOf(
            "com.appgenix.bizcal",
            "com.anydo.cal",
            "ws.xsoh.etar",
        )

        @Volatile
        var isConnected = false
            private set

        /** Only alive while the system holds the service; cleared on both exits. */
        @Volatile
        private var instance: ReNotifyListenerService? = null

        /**
         * Cleans the status bar after a filter or a rule changed. Does nothing
         * while the listener is not connected; the change still applies to
         * everything that arrives afterwards.
         */
        fun sweepShade() {
            val service = instance ?: return
            service.scope.launch { service.sweep() }
        }

        fun rebind(context: Context) {
            requestRebind(ComponentName(context, ReNotifyListenerService::class.java))
        }
    }
}
