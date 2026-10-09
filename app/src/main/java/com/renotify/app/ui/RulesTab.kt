package com.renotify.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.renotify.app.AppCatalog
import com.renotify.app.R
import com.renotify.app.data.Delivery
import com.renotify.app.data.Rule
import com.renotify.app.ui.theme.IosType
import com.renotify.app.ui.theme.LocalIosPalette
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun minuteLabel(minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)

@Composable
private fun modeLabel(mode: String): String = stringResource(
    when (mode) {
        Rule.MODE_CONTAINS -> R.string.rule_mode_contains
        Rule.MODE_NOT_CONTAINS -> R.string.rule_mode_not
        else -> R.string.rule_mode_any
    }
)

@Composable
private fun actionLabel(action: String): String = stringResource(
    if (action == Rule.ACTION_SILENCE) R.string.rule_action_silence
    else R.string.rule_action_hide
)

@Composable
private fun scopeLabel(scope: String): String = stringResource(
    when (scope) {
        Rule.SCOPE_TITLE -> R.string.rule_scope_title
        Rule.SCOPE_TEXT -> R.string.rule_scope_text
        else -> R.string.rule_scope_both
    }
)

/** The Rules tab body: add button, empty state, one card per rule. */
@Composable
fun RulesContent(
    rules: List<Rule>,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onAdd: () -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelivery: (Long, Int) -> Unit,
    onSelect: (Long) -> Unit,
    onEdit: (Rule) -> Unit,
) {
    val palette = LocalIosPalette.current
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!selectionMode) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(palette.card, RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAdd()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = palette.blue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.rule_new),
                    style = IosType.body,
                    color = palette.blue
                )
            }
        }

        if (rules.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painterResource(R.drawable.ic_boost),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = palette.secondaryLabel
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.rules_empty_title),
                    style = IosType.title3,
                    color = palette.label
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.rules_empty_sub),
                    style = IosType.subhead,
                    color = palette.secondaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            rules.forEach { rule ->
                RuleCard(
                    rule,
                    selectionMode = selectionMode,
                    selected = rule.id in selectedIds,
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle(rule.id, it)
                    },
                    onDelivery = { onDelivery(rule.id, it) },
                    onSelect = { onSelect(rule.id) },
                    onClick = { onEdit(rule) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RuleCard(
    rule: Rule,
    selectionMode: Boolean,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelivery: (Int) -> Unit,
    onSelect: () -> Unit,
    onClick: () -> Unit,
) {
    val palette = LocalIosPalette.current
    val appPart = rule.appLabel ?: stringResource(R.string.rule_any_app)
    val modePart = when (rule.mode) {
        Rule.MODE_ANY -> modeLabel(rule.mode)
        else -> "${modeLabel(rule.mode)} \"${rule.keywordList().joinToString("\", \"")}\""
    }
    val timePart =
        if (rule.startMinute != null && rule.endMinute != null) {
            " · " + stringResource(
                R.string.rule_time_between,
                minuteLabel(rule.startMinute),
                minuteLabel(rule.endMinute)
            )
        } else ""
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.card, RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = { if (selectionMode) onSelect() else onClick() },
                onLongClick = onSelect
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionCheck(selected)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "$appPart · $modePart$timePart · ${actionLabel(rule.action)}",
                style = IosType.subheadBold,
                color = if (rule.enabled) palette.label else palette.secondaryLabel,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.rule_blocked_count, rule.matchCount),
                style = IosType.footnote,
                color = palette.secondaryLabel
            )
        }
        if (!selectionMode) {
            Spacer(Modifier.width(8.dp))
            // Gear first, so the main switch stays where the thumb expects it.
            DeliveryGear(mask = rule.delivery, onChange = onDelivery)
            Spacer(Modifier.width(4.dp))
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
    }
}

/** Sentence-builder editor: every blue chip is a replaceable building block. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorSheet(
    initial: Rule?,
    onSave: (Rule) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalIosPalette.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var packageName by remember { mutableStateOf(initial?.packageName) }
    var appLabel by remember { mutableStateOf(initial?.appLabel) }
    var mode by remember { mutableStateOf(initial?.mode ?: Rule.MODE_ANY) }
    var scope by remember { mutableStateOf(initial?.scope ?: Rule.SCOPE_BOTH) }
    var action by remember { mutableStateOf(initial?.action ?: Rule.ACTION_HIDE) }
    var keywords by remember { mutableStateOf(initial?.keywordList() ?: emptyList()) }
    var keywordInput by remember { mutableStateOf("") }
    var startMinute by remember { mutableStateOf(initial?.startMinute) }
    var endMinute by remember { mutableStateOf(initial?.endMinute) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }
    var showScopePicker by remember { mutableStateOf(false) }
    var showActionPicker by remember { mutableStateOf(false) }
    var appSearch by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppCatalog.Entry>>(emptyList()) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { AppCatalog.all(context) }
    }

    fun tap() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)

    fun pickTime() {
        val start = startMinute ?: (22 * 60)
        TimePickerDialog(
            context,
            { _, h1, m1 ->
                val end = endMinute ?: (8 * 60)
                TimePickerDialog(
                    context,
                    { _, h2, m2 ->
                        startMinute = h1 * 60 + m1
                        endMinute = h2 * 60 + m2
                    },
                    end / 60, end % 60,
                    android.text.format.DateFormat.is24HourFormat(context)
                ).show()
            },
            start / 60, start % 60,
            android.text.format.DateFormat.is24HourFormat(context)
        ).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                stringResource(if (initial == null) R.string.rule_new else R.string.rule_edit),
                style = IosType.title3,
                color = palette.label
            )
            Spacer(Modifier.height(16.dp))

            when {
                showAppPicker -> {
                    // Full installed-app list with icons and a search bar.
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
                            if (appSearch.isEmpty()) {
                                Text(
                                    stringResource(R.string.rule_search_apps),
                                    style = IosType.subhead,
                                    color = palette.secondaryLabel
                                )
                            }
                            BasicTextField(
                                value = appSearch,
                                onValueChange = { appSearch = it },
                                singleLine = true,
                                textStyle = IosType.subhead.copy(color = palette.label),
                                cursorBrush = SolidColor(palette.blue),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (appSearch.isNotEmpty()) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = palette.secondaryLabel,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { appSearch = "" }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (appSearch.isBlank()) {
                        PickerRow(
                            label = stringResource(R.string.rule_any_app),
                            icon = null,
                            selected = packageName == null
                        ) {
                            tap()
                            packageName = null; appLabel = null
                            showAppPicker = false; appSearch = ""
                        }
                    }
                    apps.filter { it.label.contains(appSearch, ignoreCase = true) }
                        .forEach { app ->
                            HorizontalDivider(thickness = 0.5.dp, color = palette.separator)
                            PickerRow(
                                label = app.label,
                                icon = rememberPickerIcon(app.packageName),
                                selected = packageName == app.packageName
                            ) {
                                tap()
                                packageName = app.packageName
                                appLabel = app.label
                                showAppPicker = false; appSearch = ""
                            }
                        }
                }

                showModePicker -> {
                    listOf(Rule.MODE_ANY, Rule.MODE_CONTAINS, Rule.MODE_NOT_CONTAINS)
                        .forEachIndexed { index, option ->
                            if (index > 0) {
                                HorizontalDivider(thickness = 0.5.dp, color = palette.separator)
                            }
                            PickerRow(
                                label = modeLabel(option),
                                icon = null,
                                selected = mode == option
                            ) {
                                tap()
                                mode = option
                                showModePicker = false
                            }
                        }
                }

                showScopePicker -> {
                    listOf(Rule.SCOPE_BOTH, Rule.SCOPE_TITLE, Rule.SCOPE_TEXT)
                        .forEachIndexed { index, option ->
                            if (index > 0) {
                                HorizontalDivider(thickness = 0.5.dp, color = palette.separator)
                            }
                            PickerRow(
                                label = scopeLabel(option),
                                icon = null,
                                selected = scope == option
                            ) {
                                tap()
                                scope = option
                                showScopePicker = false
                            }
                        }
                }

                showActionPicker -> {
                    listOf(Rule.ACTION_HIDE, Rule.ACTION_SILENCE)
                        .forEachIndexed { index, option ->
                            if (index > 0) {
                                HorizontalDivider(thickness = 0.5.dp, color = palette.separator)
                            }
                            PickerRow(
                                label = actionLabel(option),
                                icon = null,
                                selected = action == option
                            ) {
                                tap()
                                action = option
                                showActionPicker = false
                            }
                        }
                }

                else -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SentenceWord(stringResource(R.string.rule_sentence_when))
                        Chip(appLabel ?: stringResource(R.string.rule_any_app)) {
                            tap(); showAppPicker = true
                        }
                        SentenceWord(stringResource(R.string.rule_sentence_that))
                        Chip(modeLabel(mode)) { tap(); showModePicker = true }
                        if (mode != Rule.MODE_ANY) {
                            keywords.forEach { word ->
                                RemovableChip(word) { tap(); keywords = keywords - word }
                            }
                            Chip(scopeLabel(scope)) { tap(); showScopePicker = true }
                        }
                        Chip(
                            if (startMinute != null && endMinute != null) {
                                stringResource(
                                    R.string.rule_time_between,
                                    minuteLabel(startMinute!!),
                                    minuteLabel(endMinute!!)
                                )
                            } else {
                                stringResource(R.string.rule_time_any)
                            }
                        ) { tap(); pickTime() }
                        if (startMinute != null) {
                            RemovableChip("") {
                                tap(); startMinute = null; endMinute = null
                            }
                        }
                        SentenceWord(stringResource(R.string.rule_sentence_then))
                        Chip(actionLabel(action)) { tap(); showActionPicker = true }
                    }

                    // Said up front, because it is the one thing a silence rule
                    // cannot do and the first thing someone would test.
                    if (action == Rule.ACTION_SILENCE) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.rule_silence_hint),
                            style = IosType.footnote,
                            color = palette.secondaryLabel
                        )
                    }

                    if (mode != Rule.MODE_ANY) {
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .background(palette.searchFill, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                if (keywordInput.isEmpty()) {
                                    Text(
                                        stringResource(R.string.rule_keyword_hint),
                                        style = IosType.subhead,
                                        color = palette.secondaryLabel
                                    )
                                }
                                BasicTextField(
                                    value = keywordInput,
                                    onValueChange = { keywordInput = it },
                                    singleLine = true,
                                    textStyle = IosType.subhead.copy(color = palette.label),
                                    cursorBrush = SolidColor(palette.blue),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            val canAdd = keywordInput.isNotBlank()
                            Text(
                                stringResource(R.string.rule_add_filter),
                                style = IosType.subheadBold,
                                color = if (canAdd) palette.blue else palette.separator,
                                modifier = Modifier.clickable(enabled = canAdd) {
                                    tap()
                                    keywords = keywords + keywordInput.trim()
                                    keywordInput = ""
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    val canSave = mode == Rule.MODE_ANY || keywords.isNotEmpty()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (canSave) palette.blue else palette.searchFill,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable(enabled = canSave) {
                                tap()
                                onSave(
                                    Rule(
                                        id = initial?.id ?: 0L,
                                        packageName = packageName,
                                        appLabel = appLabel,
                                        mode = mode,
                                        keywords = if (mode == Rule.MODE_ANY) ""
                                        else keywords.joinToString("\n"),
                                        scope = scope,
                                        startMinute = startMinute,
                                        endMinute = endMinute,
                                        enabled = initial?.enabled ?: true,
                                        // Editing a rule used to reset its
                                        // delivery setup to the default.
                                        delivery = initial?.delivery ?: Delivery.DEFAULT,
                                        action = action,
                                        matchCount = initial?.matchCount ?: 0,
                                        createdAt = initial?.createdAt ?: 0L,
                                    )
                                )
                            }
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.rule_save),
                            style = IosType.headline,
                            color = if (canSave) Color.White else palette.secondaryLabel
                        )
                    }
                    if (initial != null) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.rule_delete),
                            style = IosType.subhead,
                            color = palette.red,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tap(); onDelete(initial.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberPickerIcon(packageName: String): ImageBitmap? {
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
private fun SentenceWord(text: String, bold: Boolean = false) {
    val palette = LocalIosPalette.current
    Text(
        text,
        style = if (bold) IosType.subheadBold else IosType.subhead,
        color = palette.label,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

/**
 * Editable building block: blue-tinted pill badge with a dropdown arrow so it
 * clearly reads as "tap to change", unlike the static sentence words.
 */
@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    val palette = LocalIosPalette.current
    Row(
        Modifier
            .background(palette.blue.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = IosType.subheadBold, color = palette.blue)
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = palette.blue,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun RemovableChip(text: String, onRemove: () -> Unit) {
    val palette = LocalIosPalette.current
    Row(
        Modifier
            .background(palette.blue.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
            .clickable(onClick = onRemove)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (text.isNotEmpty()) {
            Text("\"$text\"", style = IosType.subheadBold, color = palette.blue)
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            Icons.Default.Close,
            contentDescription = null,
            tint = palette.blue,
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
private fun PickerRow(
    label: String,
    icon: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalIosPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Image(it, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            label,
            style = if (selected) IosType.subheadBold else IosType.body,
            color = if (selected) palette.blue else palette.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = palette.blue,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
