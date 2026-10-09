package com.renotify.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.renotify.app.R
import com.renotify.app.ui.theme.IosType
import com.renotify.app.ui.theme.LocalIosPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** The Trends tab: aggregate statistics over the stored history, iOS style. */
@Composable
fun TrendsContent(data: TrendsData?) {
    if (data == null) return
    if (data.total == 0) {
        TrendsEmptyState()
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard(data)
        TopAppsCard(data)
        HourCard(data)
        WeekCard(data)
    }
}

@Composable
private fun TrendsEmptyState() {
    val palette = LocalIosPalette.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = palette.secondaryLabel
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.stats_empty_title),
            style = IosType.title3,
            color = palette.label
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.stats_empty_sub),
            style = IosType.subhead,
            color = palette.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun StatsCard(content: @Composable () -> Unit) {
    val palette = LocalIosPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.card, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun CardHeader(text: String) {
    val palette = LocalIosPalette.current
    Text(text.uppercase(), style = IosType.footnote, color = palette.secondaryLabel)
}

@Composable
private fun SummaryCard(data: TrendsData) {
    val palette = LocalIosPalette.current
    StatsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${data.total}", style = IosType.largeTitle, color = palette.label)
                Text(
                    stringResource(R.string.stats_total),
                    style = IosType.footnote,
                    color = palette.secondaryLabel
                )
            }
            if (data.lastWeek > 0) {
                val pct =
                    ((data.thisWeek - data.lastWeek) * 100.0 / data.lastWeek).roundToInt()
                val up = pct >= 0
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (up) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (up) palette.green else palette.red,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "${abs(pct)} %",
                            style = IosType.headline,
                            color = if (up) palette.green else palette.red
                        )
                    }
                    Text(
                        stringResource(R.string.stats_vs_week),
                        style = IosType.caption,
                        color = palette.secondaryLabel
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                R.string.stats_per_day,
                String.format(Locale.getDefault(), "%.1f", data.perDay)
            ),
            style = IosType.subhead,
            color = palette.secondaryLabel
        )
    }
}

@Composable
private fun rememberTrendAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(96, 96).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
private fun TopAppsCard(data: TrendsData) {
    val palette = LocalIosPalette.current
    val maxCount = data.topApps.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    StatsCard {
        CardHeader(stringResource(R.string.stats_top_apps))
        Spacer(Modifier.height(12.dp))
        data.topApps.forEachIndexed { index, app ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            AppBarRow(
                label = app.appLabel,
                icon = rememberTrendAppIcon(app.packageName),
                count = app.count,
                total = data.total,
                fraction = app.count.toFloat() / maxCount,
                barColor = palette.blue,
            )
        }
        if (data.othersCount > 0) {
            Spacer(Modifier.height(12.dp))
            AppBarRow(
                label = stringResource(R.string.stats_others),
                icon = null,
                count = data.othersCount,
                total = data.total,
                fraction = data.othersCount.toFloat() / maxCount,
                barColor = palette.separator,
            )
        }
    }
}

@Composable
private fun AppBarRow(
    label: String,
    icon: ImageBitmap?,
    count: Int,
    total: Int,
    fraction: Float,
    barColor: androidx.compose.ui.graphics.Color,
) {
    val palette = LocalIosPalette.current
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0.02f, 1f),
        animationSpec = tween(600),
        label = "bar"
    )
    val share = (count * 100.0 / total.coerceAtLeast(1)).roundToInt()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Image(it, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = IosType.subheadBold,
                color = palette.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$count · $share %",
                style = IosType.footnote,
                color = palette.secondaryLabel
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(palette.searchFill, RoundedCornerShape(3.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(6.dp)
                    .background(barColor, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun HourCard(data: TrendsData) {
    val palette = LocalIosPalette.current
    val peakHour = data.hourHist.indices.maxByOrNull { data.hourHist[it] } ?: 0
    val maxCount = data.hourHist.max().coerceAtLeast(1)
    val barColor = palette.blue
    val dimColor = palette.blue.copy(alpha = 0.28f)
    StatsCard {
        CardHeader(stringResource(R.string.stats_by_hour))
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val slot = size.width / 24f
            val barWidth = slot * 0.55f
            val minBar = 2.dp.toPx()
            val radius = CornerRadius(1.5.dp.toPx())
            data.hourHist.forEachIndexed { hour, count ->
                val h = if (count == 0) minBar
                else (size.height * count / maxCount).coerceAtLeast(minBar)
                drawRoundRect(
                    color = when {
                        count == 0 -> dimColor.copy(alpha = 0.12f)
                        hour == peakHour -> barColor
                        else -> dimColor
                    },
                    topLeft = Offset(hour * slot + (slot - barWidth) / 2f, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = radius,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("0", "6", "12", "18").forEach {
                Text(
                    it,
                    style = IosType.caption,
                    color = palette.secondaryLabel,
                    modifier = Modifier.weight(1f)
                )
            }
            Text("24", style = IosType.caption, color = palette.secondaryLabel)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(
                R.string.stats_peak,
                String.format(Locale.getDefault(), "%02d:00", peakHour)
            ),
            style = IosType.footnote,
            color = palette.secondaryLabel
        )
    }
}

@Composable
private fun WeekCard(data: TrendsData) {
    val palette = LocalIosPalette.current
    val maxCount = data.dayCounts.max().coerceAtLeast(1)
    val dayFormat = remember { SimpleDateFormat("EE", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    val dayMs = 24 * 60 * 60 * 1000L
    StatsCard {
        CardHeader(stringResource(R.string.stats_week))
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().height(150.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            data.dayCounts.forEachIndexed { index, count ->
                val isToday = index == 6
                val fraction = count.toFloat() / maxCount
                val animated by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(600),
                    label = "day"
                )
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (count > 0) {
                        Text(
                            "$count",
                            style = IosType.caption,
                            color = palette.secondaryLabel
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                    Box(
                        Modifier
                            .width(18.dp)
                            .height((100.dp * animated).coerceAtLeast(3.dp))
                            .background(
                                if (isToday) palette.blue else palette.blue.copy(alpha = 0.45f),
                                RoundedCornerShape(5.dp)
                            )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        dayFormat.format(Date(now - (6 - index) * dayMs)),
                        style = IosType.caption,
                        color = if (isToday) palette.label else palette.secondaryLabel
                    )
                }
            }
        }
    }
}
