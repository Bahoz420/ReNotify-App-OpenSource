package com.renotify.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.renotify.app.R
import com.renotify.app.data.Delivery
import com.renotify.app.ui.theme.IosType
import com.renotify.app.ui.theme.LocalIosPalette

private data class DeliveryOption(val flag: Int, val labelRes: Int, val hintRes: Int)

private val OPTIONS = listOf(
    DeliveryOption(Delivery.PUSH, R.string.delivery_push, R.string.delivery_push_hint),
    DeliveryOption(Delivery.SOUND, R.string.delivery_sound, R.string.delivery_sound_hint),
    DeliveryOption(Delivery.VIBRATE, R.string.delivery_vibrate, R.string.delivery_vibrate_hint),
    DeliveryOption(Delivery.FLASH, R.string.delivery_flash, R.string.delivery_flash_hint),
)

/**
 * The four delivery toggles of one notification. [mask] is a [Delivery]
 * bitmask; [onChange] gets the full new mask so callers only ever store one
 * value.
 */
@Composable
fun DeliveryToggles(
    mask: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showHints: Boolean = true,
) {
    val palette = LocalIosPalette.current
    val haptic = LocalHapticFeedback.current
    Column(modifier) {
        OPTIONS.forEachIndexed { index, option ->
            val on = Delivery.has(mask, option.flag)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onChange(Delivery.with(mask, option.flag, !on))
                    }
                    .padding(horizontal = 16.dp, vertical = if (showHints) 8.dp else 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(option.labelRes),
                        style = IosType.body,
                        color = palette.label
                    )
                    if (showHints) {
                        Text(
                            stringResource(option.hintRes),
                            style = IosType.caption,
                            color = palette.secondaryLabel
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = on,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onChange(Delivery.with(mask, option.flag, it))
                    }
                )
            }
            if (index < OPTIONS.lastIndex && showHints) {
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

/**
 * Small gear next to a row that opens the four toggles as a dropdown, used
 * wherever a card has no room for them (rules, scheduled reminders).
 */
@Composable
fun DeliveryGear(mask: Int, onChange: (Int) -> Unit) {
    val palette = LocalIosPalette.current
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }

    Box {
        Icon(
            Icons.Default.Settings,
            contentDescription = stringResource(R.string.delivery_title),
            tint = palette.secondaryLabel,
            modifier = Modifier
                .size(30.dp)
                .background(palette.searchFill, RoundedCornerShape(15.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    expanded = true
                }
                .padding(7.dp)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = palette.card,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(276.dp),
        ) {
            Text(
                stringResource(R.string.delivery_title).uppercase(),
                style = IosType.footnote,
                color = palette.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
            )
            DeliveryToggles(mask = mask, onChange = onChange)
            Spacer(Modifier.height(4.dp))
        }
    }
}
