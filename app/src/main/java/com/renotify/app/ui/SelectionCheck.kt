package com.renotify.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.renotify.app.ui.theme.LocalIosPalette

/** The round checkbox every selectable card carries on its left edge. */
@Composable
fun SelectionCheck(selected: Boolean) {
    val palette = LocalIosPalette.current
    if (selected) {
        Box(
            Modifier.size(22.dp).background(palette.blue, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    } else {
        Box(Modifier.size(22.dp).border(1.5.dp, palette.separator, CircleShape))
    }
}
