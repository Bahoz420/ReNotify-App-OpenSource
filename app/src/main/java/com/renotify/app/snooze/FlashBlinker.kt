package com.renotify.app.snooze

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlinx.coroutines.delay

/**
 * Blinks the camera torch to draw attention when a notification is delivered.
 * Switched on per notification (Delivery.FLASH), not globally. setTorchMode
 * needs no runtime permission; fails soft on devices without a flash or while
 * the camera is in use.
 */
object FlashBlinker {

    /** Three short blinks, ~1.5s total. Call from a coroutine. */
    suspend fun blink(context: Context) {
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            repeat(3) {
                manager.setTorchMode(cameraId, true)
                delay(220)
                manager.setTorchMode(cameraId, false)
                delay(180)
            }
        } catch (_: Exception) {
            // No flash, camera busy, or torch blocked by the system: skip quietly.
        }
    }
}
