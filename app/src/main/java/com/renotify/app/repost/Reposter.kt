package com.renotify.app.repost

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.renotify.app.NotificationIcons
import com.renotify.app.R
import com.renotify.app.data.Delivery
import com.renotify.app.data.ReNotifyDatabase
import com.renotify.app.data.StoredNotification
import com.renotify.app.snooze.FlashBlinker

object Reposter {

    /**
     * From Android 8 sound, vibration and heads-up are properties of the
     * channel, not of the single notification. Every delivery setup therefore
     * gets its own channel, created the first time it is used.
     */
    private const val CHANNEL_PREFIX = "renotify_delivery_"

    /** The single channel of versions up to 1.5.2, replaced by the ones above. */
    private const val LEGACY_CHANNEL_ID = "renotify_reposts"

    /** Pre-creates the channel for the default setup, before the first resend needs it. */
    fun ensureChannel(context: Context) {
        channelFor(context, Delivery.DEFAULT)
    }

    /**
     * Whether a notification with this delivery setup would actually show up.
     * A silence rule checks it first: if ReNotify may not post, taking the
     * original away would hide the notification instead of silencing it.
     */
    fun canPost(context: Context, delivery: Int): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_PREFIX + delivery)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Pushes the given history entries back into the system, each with its own
     * delivery setup (entry first, then the rule for its app, then the
     * default), unless [deliveryOverride] sets one for all of them. Returns
     * how many were posted.
     */
    suspend fun repost(
        context: Context,
        items: List<StoredNotification>,
        deliveryOverride: Int? = null,
    ): Int {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return 0

        val rules = try {
            ReNotifyDatabase.get(context).ruleDao().getAll()
        } catch (_: Exception) {
            emptyList()
        }

        var posted = 0
        var flash = false
        for (item in items) {
            val delivery = deliveryOverride ?: Delivery.resolve(item, rules)
            if (Delivery.has(delivery, Delivery.FLASH)) flash = true

            // Boost: reuse the original click action while it is still alive,
            // so the tap lands exactly where the source app intended. Falls
            // back to simply opening the source app.
            val contentIntent = IntentCache.get(item.sbnKey)
                ?: context.packageManager
                    .getLaunchIntentForPackage(item.packageName)
                    ?.let {
                        PendingIntent.getActivity(
                            context,
                            item.id.toInt(),
                            it,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    }

            val builder = NotificationCompat.Builder(context, channelFor(context, delivery))
                .setSmallIcon(R.drawable.ic_renotify)
                .setContentTitle(item.title.ifBlank { item.appLabel })
                .setContentText(item.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.text))
                .setSubText(item.appLabel)
                .setWhen(item.postedAt)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setPriority(
                    if (Delivery.has(delivery, Delivery.PUSH)) NotificationCompat.PRIORITY_HIGH
                    else NotificationCompat.PRIORITY_DEFAULT
                )

            // User-picked category icon for self-created entries; otherwise the source
            // app icon, falling back to our own so every notification looks alike.
            val largeIcon = customIconBitmap(context, item.customIcon)
                ?: appIconBitmap(context, item.packageName)
                ?: appIconBitmap(context, context.packageName)
            largeIcon?.let { builder.setLargeIcon(it) }
            contentIntent?.let { builder.setContentIntent(it) }

            try {
                manager.notify(REPOST_TAG, item.id.toInt(), builder.build())
                posted++
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS was revoked mid-loop; stop quietly.
                break
            }
        }
        // One blink for the whole batch, no matter how many entries asked for it.
        if (flash && posted > 0) FlashBlinker.blink(context)
        return posted
    }

    private const val REPOST_TAG = "renotify_repost"

    /** Creates the channel for this delivery setup on first use and returns its id. */
    private fun channelFor(context: Context, delivery: Int): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = CHANNEL_PREFIX + delivery
        if (manager.getNotificationChannel(id) == null) {
            val channel = NotificationChannel(
                id,
                channelName(context, delivery),
                // Only IMPORTANCE_HIGH gets a heads-up banner; DEFAULT lands
                // quietly in the shade, which is exactly "push up off".
                if (Delivery.has(delivery, Delivery.PUSH)) NotificationManager.IMPORTANCE_HIGH
                else NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_desc)
                if (Delivery.has(delivery, Delivery.SOUND)) {
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                } else {
                    setSound(null, null)
                }
                enableVibration(Delivery.has(delivery, Delivery.VIBRATE))
            }
            manager.createNotificationChannel(channel)
            // The old catch-all channel would otherwise sit unused in the
            // system settings with its own sound and vibration.
            try {
                manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            } catch (_: Exception) {
            }
        }
        return id
    }

    /** "Restored notifications (Sound, Vibrate)", so system settings stay readable. */
    private fun channelName(context: Context, delivery: Int): String {
        val base = context.getString(R.string.channel_name)
        if (delivery == Delivery.DEFAULT) return base
        val parts = buildList {
            if (Delivery.has(delivery, Delivery.PUSH)) {
                add(context.getString(R.string.delivery_push))
            }
            if (Delivery.has(delivery, Delivery.SOUND)) {
                add(context.getString(R.string.delivery_sound))
            }
            if (Delivery.has(delivery, Delivery.VIBRATE)) {
                add(context.getString(R.string.delivery_vibrate))
            }
        }
        val suffix =
            if (parts.isEmpty()) context.getString(R.string.delivery_silent)
            else parts.joinToString(", ")
        return base + " (" + suffix + ")"
    }

    /** White category icon on a brand-blue circle, matching the app's look. */
    private fun customIconBitmap(context: Context, iconKey: String?): Bitmap? {
        val resId = NotificationIcons.res(iconKey) ?: return null
        return try {
            val size = 128
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A8CF4.toInt() }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            val drawable = context.getDrawable(resId)?.mutate() ?: return null
            drawable.setTint(Color.WHITE)
            val inset = (size * 0.24f).toInt()
            drawable.setBounds(inset, inset, size - inset, size - inset)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun appIconBitmap(context: Context, packageName: String): Bitmap? = try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val size = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(256)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bitmap
    } catch (_: Exception) {
        null
    }
}
