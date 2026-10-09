package com.renotify.app.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.renotify.app.AppCatalog
import com.renotify.app.R
import com.renotify.app.rules.ShadeFilter
import com.renotify.app.service.ReNotifyListenerService
import com.renotify.app.ui.theme.IosType
import com.renotify.app.ui.theme.LocalIosPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Warning colour of the app, same one the "Blocked" badge uses. */
private val Warning = Color(0xFFFF9500)

private const val TAB_RECORD = 0
private const val TAB_SHADE = 1

/**
 * The funnel in the top bar. Two filters that read alike but do opposite things,
 * which is why they sit behind a switcher instead of two switches per row:
 *
 * - Record: a switched-off app never enters the history. A privacy control, free.
 * - Status bar: only switched-on apps keep their notifications in the shade,
 *   the rest is pulled out and lives on in the history. A Pro feature in the
 *   Play edition, like unlimited rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFilterSheet(onDismiss: () -> Unit, onRequirePro: () -> Unit = {}) {
    val context = LocalContext.current
    val palette = LocalIosPalette.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var apps by remember { mutableStateOf<List<AppCatalog.Entry>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(TAB_RECORD) }
    var excluded by remember {
        mutableStateOf(prefs.getStringSet("excluded_packages", emptySet())?.toSet() ?: emptySet())
    }
    var shadeEnabled by remember { mutableStateOf(ShadeFilter.isEnabled(context)) }
    var allowed by remember { mutableStateOf(ShadeFilter.allowed(context)) }

    val hasProAccess = true

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { AppCatalog.all(context) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                stringResource(R.string.settings_filter),
                style = IosType.title3,
                color = palette.label
            )
            Spacer(Modifier.height(12.dp))

            IosSegmentedControl(
                selected = tab,
                labels = listOf(
                    stringResource(R.string.filter_tab_record),
                    stringResource(R.string.filter_tab_shade),
                ),
                onSelect = { tab = it }
            )
            Spacer(Modifier.height(12.dp))

            if (tab == TAB_RECORD) {
                Text(
                    stringResource(R.string.filter_hint),
                    style = IosType.footnote,
                    color = palette.secondaryLabel
                )
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            stringResource(R.string.shade_filter_title),
                            style = IosType.headline,
                            color = palette.label
                        )
                        Text(
                            stringResource(R.string.shade_filter_hint),
                            style = IosType.footnote,
                            color = palette.secondaryLabel
                        )
                    }
                    Switch(
                        checked = shadeEnabled,
                        onCheckedChange = { on ->
                            if (on && !hasProAccess) {
                                onRequirePro()
                            } else {
                                shadeEnabled = on
                                ShadeFilter.setEnabled(context, on)
                                // Clean what is already hanging in the status bar.
                                ReNotifyListenerService.sweepShade()
                            }
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (shadeEnabled && allowed.isEmpty()) {
                    // Switched on with nothing allowed means the shade stays empty.
                    // Better said out loud than discovered.
                    Text(
                        stringResource(R.string.shade_filter_empty),
                        style = IosType.footnote,
                        color = Warning
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    stringResource(R.string.shade_filter_protected),
                    style = IosType.caption,
                    color = palette.secondaryLabel
                )
            }
            Spacer(Modifier.height(14.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(palette.searchFill, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = palette.secondaryLabel,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    if (search.isEmpty()) {
                        Text(
                            stringResource(R.string.rule_search_apps),
                            style = IosType.subhead,
                            color = palette.secondaryLabel
                        )
                    }
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        textStyle = IosType.subhead.copy(color = palette.label),
                        cursorBrush = SolidColor(palette.blue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (search.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = palette.secondaryLabel,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { search = "" }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                apps.filter { it.label.contains(search, ignoreCase = true) }
                    .forEachIndexed { index, app ->
                        if (index > 0) {
                            HorizontalDivider(thickness = 0.5.dp, color = palette.separator)
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rememberAppFilterIcon(app.packageName)?.let {
                                Image(
                                    it,
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(
                                app.label,
                                style = IosType.body,
                                color = palette.label,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            if (tab == TAB_RECORD) {
                                Switch(
                                    checked = app.packageName !in excluded,
                                    onCheckedChange = { enabled ->
                                        excluded = if (enabled) {
                                            excluded - app.packageName
                                        } else {
                                            excluded + app.packageName
                                        }
                                        prefs.edit()
                                            .putStringSet("excluded_packages", excluded)
                                            .apply()
                                    }
                                )
                            } else {
                                Switch(
                                    checked = app.packageName in allowed,
                                    onCheckedChange = { keep ->
                                        allowed = if (keep) {
                                            allowed + app.packageName
                                        } else {
                                            allowed - app.packageName
                                        }
                                        ShadeFilter.setAllowed(context, allowed)
                                        ReNotifyListenerService.sweepShade()
                                    }
                                )
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun rememberAppFilterIcon(packageName: String): ImageBitmap? {
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
