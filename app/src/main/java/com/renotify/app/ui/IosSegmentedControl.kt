package com.renotify.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.renotify.app.ui.theme.IosType
import com.renotify.app.ui.theme.LocalIosPalette

/** The pill switcher used for the main tabs and inside the filter sheet. */
@Composable
fun IosSegmentedControl(
    selected: Int,
    labels: List<String>,
    onSelect: (Int) -> Unit,
) {
    val palette = LocalIosPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.searchFill, RoundedCornerShape(10.dp))
            .padding(3.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(
                        if (active) palette.card else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = if (active) IosType.subheadBold else IosType.subhead,
                    color = if (active) palette.label else palette.secondaryLabel
                )
            }
        }
    }
}
