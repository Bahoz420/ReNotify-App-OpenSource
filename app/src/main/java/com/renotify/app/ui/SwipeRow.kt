package com.renotify.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.renotify.app.R
import com.renotify.app.ui.theme.LocalIosPalette

/**
 * The two gestures a list like this is expected to have: swipe right to send
 * the notification again, swipe left to delete it.
 *
 * Right does not remove the row. A resend is not a decision about the entry, it
 * stays in the history and can be sent again, so the row springs back and the
 * new notification is the feedback. Left is a real deletion and the row leaves
 * with the gesture.
 *
 * Both are switched off while the list is in selection mode, where a horizontal
 * drag means nothing and would only fight the checkboxes.
 */
@Composable
fun SwipeRow(
    enabled: Boolean,
    onResend: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    val palette = LocalIosPalette.current
    val haptic = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onResend()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val resending = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (resending) palette.blue else palette.red,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 22.dp),
                contentAlignment =
                    if (resending) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (resending) {
                    Icon(
                        painterResource(R.drawable.ic_resend),
                        contentDescription = stringResource(R.string.action_resend),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
    ) {
        content()
    }
}
