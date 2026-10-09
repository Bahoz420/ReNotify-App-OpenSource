package com.renotify.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification

/**
 * "Real silence": ReNotify asks Android to leave the sound and vibration of
 * notifications to it and plays them itself, so a rule can silence a
 * notification before it makes any sound. Without it a rule can only cut a
 * tone off, because Android starts it before any listener is told.
 *
 * The lever is the listener hint wearables use to keep a phone quiet
 * (HINT_HOST_DISABLE_NOTIFICATION_EFFECTS). It is held only while the
 * listener is connected: if ReNotify stops running, Android takes the sounds
 * back on its own, so the failure mode is "rules ring again", never "the
 * phone went quiet". Calls are untouched, the hint leaves ringtones alone.
 *
 * [alert] mirrors what Android itself does for a posted notification: the
 * channel decides sound and vibration, Do Not Disturb, the ringer mode, a call
 * in progress, "alert only once" updates and group alert behaviour can each
 * keep it quiet.
 */
object AlertTakeover {

    private const val PREFS = "settings"
    private const val KEY_ENABLED = "real_silence"

    /** Android's own default pattern for a notification that vibrates. */
    private val DEFAULT_VIBRATION = longArrayOf(0, 250, 250, 250)

    /** One app posting a burst gets one sound, like Android's own rate limit. */
    private const val MIN_GAP_MS = 1_000L

    private val lastAlertAt = HashMap<String, Long>()
    private var ringtone: Ringtone? = null

    /** Key of the notification whose sound is looping, see [stopFor]. */
    private var loopingKey: String? = null

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) loopingKey?.let { stopFor(it) }
        ReNotifyListenerService.applyAlertTakeover(context)
    }

    /** Plays what Android would have played for this notification, if anything. */
    fun alert(context: Context, sbn: StatusBarNotification, ranking: Ranking, isUpdate: Boolean) {
        val notification = sbn.notification ?: return
        val channel = ranking.channel ?: return

        if (ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) return
        // Do Not Disturb holds it back.
        if (!ranking.matchesInterruptionFilter()) return
        if (Build.VERSION.SDK_INT >= 28 && ranking.isSuspended) return
        if (isUpdate && notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) return
        if (groupStaysQuiet(sbn, notification)) return
        // Ringtones were never taken over: Android still plays them itself.
        if (channel.audioAttributes?.usage == AudioAttributes.USAGE_NOTIFICATION_RINGTONE) return

        val audio = context.getSystemService(AudioManager::class.java) ?: return
        // In a call Android plays no notification sounds either.
        if (audio.mode != AudioManager.MODE_NORMAL) return

        val now = System.currentTimeMillis()
        synchronized(lastAlertAt) {
            val last = lastAlertAt[sbn.packageName] ?: 0L
            if (now - last in 0 until MIN_GAP_MS) return
            lastAlertAt[sbn.packageName] = now
        }

        val ringer = audio.ringerMode
        val sound = soundOf(channel, notification)
        val vibration = when {
            channel.shouldVibrate() -> channel.vibrationPattern ?: DEFAULT_VIBRATION
            // Vibrate mode turns a sound into a buzz, as Android does.
            sound != null && ringer == AudioManager.RINGER_MODE_VIBRATE -> DEFAULT_VIBRATION
            else -> null
        }

        if (sound != null && ringer == AudioManager.RINGER_MODE_NORMAL) {
            // "Insistent" notifications ring until they are dealt with, the
            // way some alarm and reminder apps want it.
            val insistent = notification.flags and Notification.FLAG_INSISTENT != 0
            playSound(context, sound, channel.audioAttributes, insistent)
            loopingKey = if (insistent) sbn.key else null
        }
        if (vibration != null && ringer != AudioManager.RINGER_MODE_SILENT) {
            vibrate(context, vibration)
        }
    }

    /** A group alerts through its summary or through its children, not both. */
    private fun groupStaysQuiet(sbn: StatusBarNotification, notification: Notification): Boolean {
        if (!sbn.isGroup) return false
        val summary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        return when (notification.groupAlertBehavior) {
            Notification.GROUP_ALERT_CHILDREN -> summary
            Notification.GROUP_ALERT_SUMMARY -> !summary
            else -> false
        }
    }

    /**
     * The channel's sound. Apps written before channels existed land in the
     * default channel and still set their sound on the notification itself.
     */
    @Suppress("DEPRECATION")
    private fun soundOf(channel: NotificationChannel, notification: Notification): Uri? {
        val sound = if (channel.id == NotificationChannel.DEFAULT_CHANNEL_ID) {
            notification.sound
                ?: if (notification.defaults and Notification.DEFAULT_SOUND != 0) {
                    Settings.System.DEFAULT_NOTIFICATION_URI
                } else {
                    channel.sound
                }
        } else {
            channel.sound
        }
        return sound?.takeIf { it != Uri.EMPTY }
    }

    /** Stops a looping sound once its notification is gone. */
    fun stopFor(key: String) {
        if (key != loopingKey) return
        loopingKey = null
        try {
            ringtone?.stop()
        } catch (_: Exception) {
        }
    }

    private fun playSound(context: Context, uri: Uri, attributes: AudioAttributes?, loop: Boolean) {
        // A custom sound in shared storage may not be readable for ReNotify;
        // the default notification sound is better than silence then.
        val playable = if (canOpen(context, uri)) uri else Settings.System.DEFAULT_NOTIFICATION_URI
        try {
            ringtone?.stop()
            val next = RingtoneManager.getRingtone(context, playable) ?: return
            next.audioAttributes = attributes ?: AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (loop && Build.VERSION.SDK_INT >= 28) next.isLooping = true
            next.play()
            ringtone = next
        } catch (_: Exception) {
        }
    }

    private fun canOpen(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.close()
        true
    } catch (_: Exception) {
        false
    }

    private fun vibrate(context: Context, pattern: LongArray) {
        try {
            if (pattern.none { it > 0 }) return
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            } ?: return
            val effect = VibrationEffect.createWaveform(pattern, -1)
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                )
            }
        } catch (_: Exception) {
        }
    }
}
