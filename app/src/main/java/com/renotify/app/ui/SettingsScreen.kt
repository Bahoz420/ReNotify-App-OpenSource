package com.renotify.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.renotify.app.R
import com.renotify.app.service.ListenerGuard
import com.renotify.app.telemetry.Telemetry
import com.renotify.app.data.ReNotifyDatabase
import com.renotify.app.data.Retention
import com.renotify.app.data.StoredNotification
import com.renotify.app.ui.theme.IosType
import com.renotify.app.ui.theme.LocalIosPalette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PRIVACY_POLICY_URL = "https://re-notify.com/privacy"
private const val TERMS_URL = "https://re-notify.com/terms"
private const val CONTACT_EMAIL = "dev@fluxera.us"

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalIosPalette.current
    var batteryExempt by remember { mutableStateOf(ListenerGuard.isBatteryExempt(context)) }
    var listenerGranted by remember { mutableStateOf(ListenerGuard.hasAccess(context)) }
    var telemetryEnabled by remember { mutableStateOf(Telemetry.isEnabled(context)) }
    var retentionDays by remember { mutableStateOf(Retention.days(context)) }
    var showRetention by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Every feature is free in this edition, nothing is ever locked.
    val hasProAccess = true
    fun showProOffer() {}

    LifecycleResumeEffect(Unit) {
        batteryExempt = ListenerGuard.isBatteryExempt(context)
        listenerGranted = ListenerGuard.hasAccess(context)
        onPauseOrDispose { }
    }

    fun exportHistory() {
        scope.launch(Dispatchers.IO) {
            val items = ReNotifyDatabase.get(context).notificationDao().getAll()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val sb = StringBuilder("time,app,package,title,text,removed\n")
            for (n in items) {
                sb.append(
                    listOf(
                        dateFormat.format(Date(n.postedAt)),
                        n.appLabel,
                        n.packageName,
                        n.title,
                        n.text,
                        if (n.removedAt != null) "yes" else "no"
                    ).joinToString(",") {
                        "\"" + it.replace("\"", "\"\"").replace("\n", " ") + "\""
                    }
                ).append('\n')
            }
            val dir = File(context.cacheDir, "export").apply { mkdirs() }
            val file = File(dir, "renotify-history.csv")
            file.writeText(sb.toString())
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file
            )
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType("text/csv")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        null
                    )
                )
            }
        }
    }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    BackHandler(onBack = onBack)


    fun addDemoData() {
        scope.launch(Dispatchers.IO) {
            val dao = ReNotifyDatabase.get(context).notificationDao()
            // Refresh demos in the current app language instead of stacking old ones.
            dao.deleteDemos()
            val now = System.currentTimeMillis()
            val pm = context.packageManager

            // Prefer an installed app per category so real icons and labels show up.
            fun pick(candidates: List<Pair<String, String>>): Pair<String, String> {
                for ((pkg, _) in candidates) {
                    try {
                        val info = pm.getApplicationInfo(pkg, 0)
                        return pkg to pm.getApplicationLabel(info).toString()
                    } catch (_: Exception) {
                        // not installed, try next candidate
                    }
                }
                return candidates.first()
            }

            data class Demo(
                val app: Pair<String, String>,
                val titleRes: Int, val textRes: Int,
                val ageMs: Long, val removed: Boolean,
            )
            val demos = listOf(
                Demo(
                    pick(
                        listOf(
                            "com.whatsapp" to "WhatsApp",
                            "org.telegram.messenger" to "Telegram",
                            "org.thoughtcrime.securesms" to "Signal",
                        )
                    ),
                    R.string.demo_wa_title, R.string.demo_wa_text, 5 * 60_000L, false
                ),
                Demo(
                    pick(listOf("com.google.android.gm" to "Gmail")),
                    R.string.demo_mail_title, R.string.demo_mail_text, 45 * 60_000L, true
                ),
                Demo(
                    pick(
                        listOf(
                            "com.instagram.android" to "Instagram",
                            "com.zhiliaoapp.musically" to "TikTok",
                            "com.twitter.android" to "X",
                        )
                    ),
                    R.string.demo_insta_title, R.string.demo_insta_text, 2 * 3_600_000L, true
                ),
                Demo(
                    pick(
                        listOf(
                            "com.amazon.mShop.android.shopping" to "Amazon",
                            "de.zalando.mobile" to "Zalando",
                            "com.ebay.mobile" to "eBay",
                        )
                    ),
                    R.string.demo_amazon_title, R.string.demo_amazon_text, 5 * 3_600_000L, false
                ),
                Demo(
                    pick(
                        listOf(
                            "com.paypal.android.p2pmobile" to "PayPal",
                            "de.number26.android" to "N26",
                            "com.starfinanz.smob.android.sfinanzstatus" to "Sparkasse",
                        )
                    ),
                    R.string.demo_bank_title, R.string.demo_bank_text, 26 * 3_600_000L, true
                ),
                Demo(
                    pick(
                        listOf(
                            "com.spotify.music" to "Spotify",
                            "com.google.android.youtube" to "YouTube",
                            "deezer.android.app" to "Deezer",
                        )
                    ),
                    R.string.demo_spotify_title, R.string.demo_spotify_text, 2 * 24 * 3_600_000L, true
                ),
            )
            for (d in demos) {
                dao.insert(
                    StoredNotification(
                        sbnKey = "demo-${d.app.first}-$now",
                        packageName = d.app.first,
                        appLabel = d.app.second,
                        title = context.getString(d.titleRes),
                        text = context.getString(d.textRes),
                        postedAt = now - d.ageMs,
                        removedAt = if (d.removed) now - d.ageMs + 60_000 else null,
                    )
                )
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.demo_added, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = null,
                tint = palette.blue,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onBack)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.settings_title), style = IosType.largeTitle, color = palette.label)
        Spacer(Modifier.height(20.dp))

        SectionHeader(stringResource(R.string.settings_general))
        SettingsGroup {
            SettingsRow(stringResource(R.string.settings_language)) {
                val intent = if (Build.VERSION.SDK_INT >= 33) {
                    Intent(
                        SystemSettings.ACTION_APP_LOCALE_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                } else {
                    Intent(
                        SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    context.startActivity(
                        Intent(
                            SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }
            }
            RowDivider()
            SettingsRow(
                label = stringResource(R.string.settings_notification_access),
                statusOk = listenerGranted,
            ) {
                context.startActivity(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            RowDivider()
            SettingsRow(
                label = stringResource(R.string.settings_battery),
                value = stringResource(
                    if (batteryExempt) R.string.battery_state_ok
                    else R.string.battery_state_bad
                ),
                valueColor = if (batteryExempt) palette.green else palette.secondaryLabel,
            ) {
                ListenerGuard.requestBatteryExemption(context)
            }
            RowDivider()
            SettingsRow(stringResource(R.string.settings_export)) {
                if (hasProAccess) exportHistory() else showProOffer()
            }
            RowDivider()
            SettingsRow(
                label = stringResource(R.string.settings_retention),
                value = retentionLabel(retentionDays),
            ) {
                showRetention = true
            }
        }


        Spacer(Modifier.height(24.dp))
        SectionHeader(stringResource(R.string.settings_section_about))
        SettingsGroup {
            SettingsRow(
                label = stringResource(R.string.settings_version),
                value = versionName,
                chevron = false,
                onClick = null
            )
            RowDivider()
            SettingsRow(stringResource(R.string.settings_privacy)) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
            }
            if (TERMS_URL.isNotBlank()) {
                RowDivider()
                SettingsRow(stringResource(R.string.settings_terms)) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL)))
                }
            }
            RowDivider()
            SettingsRow(stringResource(R.string.settings_contact)) {
                context.startActivity(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL"))
                )
            }
            // A build without a statistics server has nothing to switch.
            if (Telemetry.isAvailable) {
                RowDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.settings_telemetry),
                        style = IosType.body,
                        color = palette.label,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = telemetryEnabled,
                        onCheckedChange = { enabled ->
                            telemetryEnabled = enabled
                            Telemetry.setEnabled(context, enabled)
                        }
                    )
                }
            }
            RowDivider()
            SettingsRow(stringResource(R.string.settings_demo)) { addDemoData() }
        }

        Spacer(Modifier.height(48.dp))
    }


    if (showRetention) {
        RetentionSheet(
            selected = retentionDays,
            onSelect = { days ->
                retentionDays = days
                Retention.setDays(context, days)
                showRetention = false
            },
            onDismiss = { showRetention = false },
        )
    }
}

@Composable
private fun retentionLabel(days: Int): String = when (days) {
    0 -> stringResource(R.string.retention_never)
    1 -> stringResource(R.string.retention_one_day)
    else -> stringResource(R.string.retention_days, days)
}

/**
 * Picks the retention window. Free in every edition: deleting your own data
 * is never something to pay for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetentionSheet(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalIosPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.settings_retention),
                style = IosType.title3,
                color = palette.label,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )
            Text(
                stringResource(R.string.retention_hint),
                style = IosType.footnote,
                color = palette.secondaryLabel,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            Retention.OPTIONS.forEach { days ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(days) }
                        .padding(horizontal = 24.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        retentionLabel(days),
                        style = IosType.body,
                        color = palette.label,
                        modifier = Modifier.weight(1f)
                    )
                    if (days == selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = palette.blue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val palette = LocalIosPalette.current
    Text(
        text,
        style = IosType.footnote,
        color = palette.secondaryLabel,
        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    val palette = LocalIosPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.card, RoundedCornerShape(12.dp))
    ) {
        content()
    }
}

@Composable
private fun RowDivider() {
    val palette = LocalIosPalette.current
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.5.dp,
        color = palette.separator
    )
}

@Composable
private fun SettingsRow(
    label: String,
    value: String? = null,
    valueColor: androidx.compose.ui.graphics.Color? = null,
    chevron: Boolean = true,
    statusOk: Boolean? = null,
    onClick: (() -> Unit)?,
) {
    val palette = LocalIosPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = IosType.body, color = palette.label, modifier = Modifier.weight(1f))
        if (statusOk != null) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(
                        if (statusOk) palette.green else palette.red,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (statusOk) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (value != null) {
            Text(value, style = IosType.body, color = valueColor ?: palette.secondaryLabel)
        }
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.separator,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
