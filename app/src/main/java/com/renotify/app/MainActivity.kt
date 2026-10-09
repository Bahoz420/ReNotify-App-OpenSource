package com.renotify.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.util.Calendar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.renotify.app.data.Delivery
import com.renotify.app.data.Rule
import com.renotify.app.data.StoredNotification
import com.renotify.app.repost.IntentCache
import com.renotify.app.service.ListenerGuard
import com.renotify.app.service.ReNotifyListenerService
import com.renotify.app.snooze.Snoozer
import com.renotify.app.telemetry.Telemetry
import com.renotify.app.ui.AppFilterSheet
import com.renotify.app.ui.AppGroup
import com.renotify.app.ui.DeliveryGear
import com.renotify.app.ui.DeliveryToggles
import com.renotify.app.ui.HistoryViewModel
import com.renotify.app.ui.IosSegmentedControl
import com.renotify.app.ui.OnboardingScreen
import com.renotify.app.ui.RuleEditorSheet
import com.renotify.app.ui.RulesContent
import com.renotify.app.ui.SelectionCheck
import com.renotify.app.ui.SettingsScreen
import com.renotify.app.ui.SwipeRow
import com.renotify.app.ui.TrendsContent
import com.renotify.app.ui.theme.IosType
import com.renotify.app.ui.theme.LocalIosPalette
import com.renotify.app.ui.theme.ReNotifyTheme
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        setContent {
            ReNotifyTheme {
                var showOnboarding by remember {
                    mutableStateOf(
                        !prefs.getBoolean("onboarding_done", false) &&
                            !hasListenerAccess(this)
                    )
                }
                var showSettings by remember { mutableStateOf(false) }
                when {
                    showOnboarding -> OnboardingScreen(onFinished = {
                        prefs.edit().putBoolean("onboarding_done", true).apply()
                        showOnboarding = false
                    })
                    showSettings -> SettingsScreen(onBack = { showSettings = false })
                    else -> ReNotifyScreen(onOpenSettings = { showSettings = true })
                }
            }
        }
    }
}

private fun hasListenerAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReNotifyScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: HistoryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val palette = LocalIosPalette.current
    val notifications by viewModel.notifications.collectAsState()
    val appGroups by viewModel.appGroups.collectAsState()
    val openedApp by viewModel.openedApp.collectAsState()
    val visibleNotifications by viewModel.visibleNotifications.collectAsState()
    val pagedNotifications by viewModel.pagedNotifications.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val pageCount by viewModel.pageCount.collectAsState()
    val scheduledNotifications by viewModel.scheduledNotifications.collectAsState()
    val trends by viewModel.trends.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val repostCount by viewModel.lastRepostCount.collectAsState()

    var listenerEnabled by remember { mutableStateOf(hasListenerAccess(context)) }
    var batteryExempt by remember { mutableStateOf(ListenerGuard.isBatteryExempt(context)) }
    var batteryDismissed by remember {
        mutableStateOf(
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("battery_card_dismissed", false)
        )
    }
    var telemetryUndecided by remember { mutableStateOf(Telemetry.needsConsent(context)) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var showCompose by remember { mutableStateOf(false) }
    var showAppFilter by remember { mutableStateOf(false) }
    var sheetItem by remember { mutableStateOf<StoredNotification?>(null) }
    val clipboard = LocalClipboardManager.current
    val settingsPrefs = remember {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }
    var swipeHintSeen by remember {
        mutableStateOf(settingsPrefs.getBoolean("swipe_hint_seen", false))
    }
    fun markSwipeHintSeen() {
        if (swipeHintSeen) return
        swipeHintSeen = true
        settingsPrefs.edit().putBoolean("swipe_hint_seen", true).apply()
    }
    var sheetScheduled by remember { mutableStateOf<StoredNotification?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var showRuleEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<Rule?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(currentPage) {
        listState.animateScrollToItem(0)
    }

    // Smooth scroll back to the top whenever the tab or the opened app changes.
    LaunchedEffect(selectedTab, openedApp) {
        listState.animateScrollToItem(0)
    }

    // The history starts as a list of apps; a search or an opened app lists
    // single notifications instead.
    val showAppGroups = openedApp == null && searchQuery.isEmpty()

    // An opened app whose last entry was deleted has nothing left to show. The
    // overview hides the Select action, so selection mode has to end with it.
    LaunchedEffect(openedApp, appGroups, searchQuery) {
        if (searchQuery.isEmpty() && openedApp != null &&
            appGroups.none { it.packageName == openedApp }
        ) {
            if (selectionMode) {
                viewModel.clearSelection()
                selectionMode = false
            }
            viewModel.closeApp()
        }
    }

    fun leaveSelection() {
        if (selectionMode) {
            viewModel.clearSelection()
            selectionMode = false
        }
    }

    // Back walks up one level at a time: out of a selection, out of a search,
    // out of an opened app, back to the history tab. Only from the app
    // overview does it leave the app.
    BackHandler(
        enabled = selectionMode || searchQuery.isNotEmpty() || openedApp != null ||
            selectedTab != 0
    ) {
        when {
            selectionMode -> leaveSelection()
            searchQuery.isNotEmpty() -> viewModel.searchQuery.value = ""
            openedApp != null -> viewModel.closeApp()
            else -> selectedTab = 0
        }
    }

    // Coming back to the app starts on the overview of all apps again, not
    // wherever the last visit ended. The view model outlives the visit, so
    // the opened app would otherwise still be there.
    var leftApp by remember { mutableStateOf(false) }
    LifecycleStartEffect(Unit) {
        if (leftApp) {
            leftApp = false
            leaveSelection()
            viewModel.searchQuery.value = ""
            viewModel.closeApp()
            selectedTab = 0
        }
        onStopOrDispose { leftApp = true }
    }

    LifecycleResumeEffect(Unit) {
        listenerEnabled = hasListenerAccess(context)
        batteryExempt = ListenerGuard.isBatteryExempt(context)
        // Revive the listener if an aggressive OEM killed the process.
        ListenerGuard.kick(context)
        onPauseOrDispose { }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(repostCount) {
        repostCount?.let {
            snackbarHostState.showSnackbar(
                if (it == 1) context.getString(R.string.snackbar_sent_one)
                else context.getString(R.string.snackbar_sent_many, it)
            )
            viewModel.consumeRepostMessage()
        }
    }

    // Every feature is free in this edition, nothing is ever locked.
    val hasProAccess = true
    fun showProOffer() {}

    fun requirePro(action: () -> Unit) {
        if (hasProAccess) action() else showProOffer()
    }


    Scaffold(
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (selectionMode) {
                SelectionToolbar(onSelectAll = { viewModel.selectAllVisible(selectedTab) })
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ReNotify", style = IosType.largeTitle, color = palette.label)
                    Spacer(Modifier.weight(1f))
                    if (!selectionMode) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.compose_title),
                            tint = palette.blue,
                            modifier = Modifier
                                .size(26.dp)
                                .clickable { requirePro { showCompose = true } }
                        )
                        Spacer(Modifier.width(16.dp))
                        Icon(
                            painterResource(R.drawable.ic_filter),
                            contentDescription = stringResource(R.string.settings_filter),
                            tint = palette.blue,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { showAppFilter = true }
                        )
                        Spacer(Modifier.width(16.dp))
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = palette.blue,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(onClick = onOpenSettings)
                        )
                        Spacer(Modifier.width(16.dp))
                    } else {
                        val hasSelection = selectedIds.isNotEmpty()
                        // Rules cannot be sent anywhere, they are only deleted.
                        if (selectedTab != 3) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = stringResource(R.string.action_send),
                                tint = if (hasSelection) palette.blue else palette.separator,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(enabled = hasSelection) {
                                        requirePro {
                                            if (selectedTab == 1) {
                                                viewModel.sendSelectedScheduled()
                                            } else {
                                                viewModel.repostSelected()
                                            }
                                        }
                                    }
                            )
                            Spacer(Modifier.width(20.dp))
                        }
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(
                                if (selectedTab == 1) R.string.action_cancel_reminder
                                else R.string.action_delete
                            ),
                            tint = if (hasSelection) palette.red else palette.separator,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(enabled = hasSelection) {
                                    when (selectedTab) {
                                        1 -> viewModel.cancelSelectedReminders()
                                        3 -> viewModel.deleteSelectedRules()
                                        else -> viewModel.deleteSelected()
                                    }
                                }
                        )
                        Spacer(Modifier.width(20.dp))
                    }
                    // Trends has nothing to select, and neither has the app overview:
                    // there the rows are apps, not notifications.
                    if (selectedTab != 2 && !(selectedTab == 0 && showAppGroups)) {
                        Text(
                            if (selectionMode) stringResource(R.string.action_done)
                            else stringResource(R.string.action_select),
                            style = if (selectionMode) IosType.headline else IosType.body,
                            color = palette.blue,
                            modifier = Modifier.clickable {
                                if (selectionMode) viewModel.clearSelection()
                                selectionMode = !selectionMode
                            }
                        )
                    }
                }
            }

            item(key = "tabs") {
                IosSegmentedControl(
                    selected = selectedTab,
                    labels = listOf(
                        stringResource(R.string.tab_history),
                        stringResource(R.string.tab_snoozed),
                        stringResource(R.string.tab_trends),
                        stringResource(R.string.tab_rules),
                    ),
                    onSelect = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (selectionMode) {
                            viewModel.clearSelection()
                            selectionMode = false
                        }
                        selectedTab = it
                    }
                )
            }

            if (selectedTab == 0) {
                item(key = "search") {
                    IosSearchBar(
                        value = searchQuery,
                        onValueChange = {
                            viewModel.searchQuery.value = it
                            // Searching looks across every app, so it leaves one.
                            if (it.isNotEmpty()) viewModel.closeApp()
                        },
                    )
                }

                if (!listenerEnabled) {
                    item(key = "permission") { PermissionCard() }
                }

                if (listenerEnabled && !batteryExempt && !batteryDismissed) {
                    item(key = "battery") {
                        BatteryCard(
                            onAllow = { ListenerGuard.requestBatteryExemption(context) },
                            onDismiss = {
                                batteryDismissed = true
                                context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                                    .edit().putBoolean("battery_card_dismissed", true).apply()
                            }
                        )
                    }
                }

                // Statistics are off until someone says yes. Asked here once,
                // in the list and not as a sheet, so it never stacks on top of
                // the trial offer and can simply be scrolled past.
                if (telemetryUndecided && listenerEnabled) {
                    item(key = "telemetry") {
                        TelemetryCard(
                            onAnswer = { yes ->
                                Telemetry.setEnabled(context, yes)
                                telemetryUndecided = false
                            }
                        )
                    }
                }

                if (openedApp != null) {
                    item(key = "opened_app") {
                        OpenedAppRow(
                            label = appGroups.firstOrNull { it.packageName == openedApp }?.appLabel
                                ?: openedApp.orEmpty(),
                            onBack = {
                                leaveSelection()
                                viewModel.closeApp()
                            }
                        )
                    }
                }

                if (notifications.isNotEmpty() && !selectionMode) {
                    item(key = "repost_all") {
                        IosActionRow(
                            text = stringResource(R.string.resend_all),
                            locked = !hasProAccess
                        ) {
                            requirePro {
                                // Inside an app the row stays scoped to that app.
                                if (openedApp == null) viewModel.repostAll()
                                else viewModel.repostOpenedApp()
                            }
                        }
                    }
                    // Only where the list shows single notifications, so the
                    // overview cannot wipe the whole history in one tap.
                    if (!showAppGroups) {
                        item(key = "delete_all") {
                            IosActionRow(
                                text = stringResource(R.string.delete_all),
                                destructive = true
                            ) {
                                confirmDeleteAll = true
                            }
                        }
                    }
                }

                if (notifications.isEmpty()) {
                    item(key = "empty") { EmptyState(listenerEnabled) }
                } else if (showAppGroups) {
                    item(key = "apps_section") {
                        Text(
                            stringResource(R.string.section_apps),
                            style = IosType.footnote,
                            color = palette.secondaryLabel,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                        )
                    }
                    items(appGroups, key = { it.packageName }) { group ->
                        AppGroupRow(group = group, onClick = { viewModel.openApp(group.packageName) })
                    }
                } else {
                    item(key = "section") {
                        Column(Modifier.padding(start = 16.dp, top = 12.dp)) {
                            Text(
                                stringResource(R.string.section_history),
                                style = IosType.footnote,
                                color = palette.secondaryLabel
                            )
                            // A gesture nobody can see. Said once, then gone
                            // for good after the first swipe.
                            if (!swipeHintSeen) {
                                Text(
                                    stringResource(R.string.swipe_hint),
                                    style = IosType.caption,
                                    color = palette.secondaryLabel,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                    items(pagedNotifications, key = { it.id }) { item ->
                        SwipeRow(
                            enabled = !selectionMode,
                            onResend = {
                                markSwipeHintSeen()
                                viewModel.repostSingle(item)
                            },
                            onDelete = {
                                markSwipeHintSeen()
                                viewModel.deleteSingle(item.id)
                            },
                        ) {
                            NotificationCard(
                                item = item,
                                selected = item.id in selectedIds,
                                selectionMode = selectionMode,
                                onToggle = { viewModel.toggleSelection(item.id) },
                                onOpen = { sheetItem = item },
                                onRepost = { viewModel.repostSingle(item) },
                            )
                        }
                    }
                    if (pageCount > 1) {
                        item(key = "pagination") {
                            PaginationRow(
                                currentPage = currentPage,
                                pageCount = pageCount,
                                onPrevious = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.previousPage()
                                },
                                onNext = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.nextPage()
                                },
                            )
                        }
                    }
                }

            } else if (selectedTab == 2) {
                item(key = "trends") { TrendsContent(trends) }
            } else if (selectedTab == 3) {
                item(key = "rules") {
                    RulesContent(
                        rules = rules,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onAdd = {
                            if (hasProAccess || rules.isEmpty()) {
                                editingRule = null
                                showRuleEditor = true
                            } else {
                                showProOffer()
                            }
                        },
                        onToggle = { id, enabled -> viewModel.setRuleEnabled(id, enabled) },
                        onDelivery = { id, mask -> viewModel.setRuleDelivery(id, mask) },
                        onSelect = { viewModel.toggleSelection(it) },
                        onEdit = {
                            editingRule = it
                            showRuleEditor = true
                        },
                    )
                }
            } else {
                if (scheduledNotifications.isEmpty()) {
                    item(key = "snoozed_empty") { SnoozedEmptyState() }
                } else {
                    item(key = "snoozed_section") {
                        Text(
                            stringResource(R.string.tab_snoozed).uppercase(),
                            style = IosType.footnote,
                            color = palette.secondaryLabel,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                        )
                    }
                    items(scheduledNotifications, key = { "s${it.id}" }) { item ->
                        ScheduledCard(
                            item = item,
                            selected = item.id in selectedIds,
                            selectionMode = selectionMode,
                            delivery = Delivery.resolve(item, rules),
                            onDelivery = { viewModel.setNotificationDelivery(item.id, it) },
                            onToggle = { viewModel.toggleSelection(item.id) },
                            onOpen = { sheetScheduled = item },
                        )
                    }
                }
            }

            item(key = "footer_space") { Spacer(Modifier.height(24.dp)) }
        }
    }


    if (confirmDeleteAll) {
        val scopeLabel = appGroups.firstOrNull { it.packageName == openedApp }?.appLabel
        val count = visibleNotifications.size
        ModalBottomSheet(
            onDismissRequest = { confirmDeleteAll = false },
            containerColor = palette.card,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text(
                    if (scopeLabel != null) {
                        stringResource(R.string.delete_all_body, count, scopeLabel)
                    } else {
                        stringResource(R.string.delete_all_body_found, count)
                    },
                    style = IosType.footnote,
                    color = palette.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
                SheetRow(stringResource(R.string.delete_all), palette.red) {
                    viewModel.deleteVisible()
                    confirmDeleteAll = false
                }
                SheetRow(stringResource(R.string.action_cancel), palette.blue) {
                    confirmDeleteAll = false
                }
            }
        }
    }

    if (showRuleEditor) {
        RuleEditorSheet(
            initial = editingRule,
            onSave = { rule ->
                viewModel.saveRule(rule)
                showRuleEditor = false
            },
            onDelete = { id ->
                viewModel.deleteRule(id)
                showRuleEditor = false
            },
            onDismiss = { showRuleEditor = false },
        )
    }

    if (showAppFilter) {
        AppFilterSheet(
            onDismiss = { showAppFilter = false },
            // Never two sheets at once: the filter closes before anything else opens.
            onRequirePro = {
                showAppFilter = false
                showProOffer()
            },
        )
    }

    if (showCompose) {
        ComposeNotificationSheet(
            onDismiss = { showCompose = false },
            onCreate = { title, text, timestamp, iconKey ->
                viewModel.createCustom(title, text, timestamp, iconKey)
                showCompose = false
            }
        )
    }

    sheetItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { sheetItem = null },
            containerColor = palette.card,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            fun snooze(triggerAt: Long) {
                requirePro {
                    Snoozer.schedule(context, item.id, triggerAt)
                    maybeRequestExactAlarm(context)
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.snooze_set))
                    }
                    sheetItem = null
                }
            }
            // Notification text a rule or the source app would otherwise take
            // away with it: selectable, so any part of it can be picked up and
            // looked up somewhere else.
            val fullText = remember(item.id) {
                listOf(item.title, item.text).filter { it.isNotBlank() }.joinToString("\n")
            }
            // Scrollable: on a small or heavily scaled screen the sheet is
            // taller than the display and the last rows would be cut off.
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
            ) {
                SelectionContainer {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
                        Text(
                            item.appLabel.uppercase(),
                            style = IosType.caption,
                            color = palette.secondaryLabel
                        )
                        if (item.title.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(item.title, style = IosType.subheadBold, color = palette.label)
                        }
                        if (item.text.isNotBlank()) {
                            Text(item.text, style = IosType.subhead, color = palette.label)
                        }
                    }
                }
                HorizontalDivider(
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = palette.separator
                )
                if (IntentCache.has(item.sbnKey)) {
                    Row(
                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_boost),
                            contentDescription = null,
                            tint = palette.blue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.boost_hint),
                            style = IosType.footnote,
                            color = palette.secondaryLabel
                        )
                    }
                }
                SheetRow(stringResource(R.string.action_resend), palette.blue) {
                    viewModel.repostSingle(item)
                    sheetItem = null
                }
                if (fullText.isNotBlank()) {
                    SheetRow(stringResource(R.string.action_copy), palette.label) {
                        clipboard.setText(AnnotatedString(fullText))
                        // Android 13 shows its own clipboard confirmation, a
                        // second one on top of it just looks broken.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.toast_copied)
                                )
                            }
                        }
                        sheetItem = null
                    }
                    SheetRow(stringResource(R.string.action_share), palette.label) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, fullText)
                            if (item.title.isNotBlank()) {
                                putExtra(Intent.EXTRA_SUBJECT, item.title)
                            }
                        }
                        try {
                            context.startActivity(Intent.createChooser(send, null))
                        } catch (_: Exception) {
                        }
                        sheetItem = null
                    }
                }
                HorizontalDivider(
                    Modifier.padding(horizontal = 24.dp),
                    thickness = 0.5.dp,
                    color = palette.separator
                )
                Text(
                    stringResource(R.string.action_snooze).uppercase(),
                    style = IosType.footnote,
                    color = palette.secondaryLabel,
                    modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 2.dp)
                )
                item.scheduledFor?.let { scheduledFor ->
                    ReminderChip(
                        scheduledFor,
                        Modifier.padding(start = 24.dp, top = 6.dp, bottom = 4.dp)
                    )
                    SheetRow(stringResource(R.string.action_cancel_reminder), palette.red) {
                        viewModel.cancelReminder(item.id)
                        sheetItem = null
                    }
                }
                SheetRow(stringResource(R.string.snooze_1h), palette.label) {
                    snooze(Snoozer.inOneHour())
                }
                SheetRow(stringResource(R.string.snooze_evening), palette.label) {
                    snooze(Snoozer.thisEvening())
                }
                SheetRow(stringResource(R.string.snooze_morning), palette.label) {
                    snooze(Snoozer.tomorrowMorning())
                }
                SheetRow(stringResource(R.string.snooze_custom), palette.blue) {
                    val now = Calendar.getInstance()
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val target = (now.clone() as Calendar).apply {
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                                // Alarm-clock behavior: a time already past today means tomorrow.
                                if (timeInMillis <= System.currentTimeMillis()) {
                                    add(Calendar.DAY_OF_YEAR, 1)
                                }
                            }
                            snooze(target.timeInMillis)
                        },
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        android.text.format.DateFormat.is24HourFormat(context)
                    ).show()
                }
                HorizontalDivider(
                    Modifier.padding(horizontal = 24.dp),
                    thickness = 0.5.dp,
                    color = palette.separator
                )
                Text(
                    stringResource(R.string.delivery_title).uppercase(),
                    style = IosType.footnote,
                    color = palette.secondaryLabel,
                    modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 2.dp)
                )
                var mask by remember(item.id) {
                    mutableStateOf(Delivery.resolve(item, rules))
                }
                DeliveryToggles(
                    mask = mask,
                    onChange = {
                        mask = it
                        viewModel.setNotificationDelivery(item.id, it)
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                HorizontalDivider(
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = palette.separator
                )
                SheetRow(stringResource(R.string.action_delete), palette.red) {
                    viewModel.deleteSingle(item.id)
                    sheetItem = null
                }
            }
        }
    }

    sheetScheduled?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { sheetScheduled = null },
            containerColor = palette.card,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                if (IntentCache.has(item.sbnKey)) {
                    Row(
                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_boost),
                            contentDescription = null,
                            tint = palette.blue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.boost_hint),
                            style = IosType.footnote,
                            color = palette.secondaryLabel
                        )
                    }
                }
                SheetRow(stringResource(R.string.action_send_now), palette.blue) {
                    viewModel.sendScheduledNow(item)
                    sheetScheduled = null
                }
                HorizontalDivider(
                    Modifier.padding(horizontal = 24.dp),
                    thickness = 0.5.dp,
                    color = palette.separator
                )
                SheetRow(stringResource(R.string.action_cancel_reminder), palette.red) {
                    viewModel.cancelReminder(item.id)
                    sheetScheduled = null
                }
            }
        }
    }
}

/**
 * From Android 12 exact alarms need the "Alarms & reminders" special access. Ask once
 * via the system screen; without it snoozes still fire, just possibly delayed.
 */
private fun maybeRequestExactAlarm(context: Context) {
    if (Build.VERSION.SDK_INT < 31 || Snoozer.canScheduleExact(context)) return
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    if (prefs.getBoolean("exact_alarm_prompted", false)) return
    prefs.edit().putBoolean("exact_alarm_prompted", true).apply()
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            )
        )
    } catch (_: Exception) {
    }
}

@Composable
private fun SheetRow(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Text(
        text,
        style = IosType.body,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeNotificationSheet(
    onDismiss: () -> Unit,
    onCreate: (title: String, text: String, timestamp: Long, iconKey: String) -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalIosPalette.current
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf(NotificationIcons.DEFAULT) }
    var dateTime by remember { mutableStateOf(Calendar.getInstance()) }

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
            Text(stringResource(R.string.compose_title), style = IosType.title3, color = palette.label)
            Spacer(Modifier.height(16.dp))
            ComposeInput(
                value = title,
                onValueChange = { title = it },
                placeholder = stringResource(R.string.compose_field_title)
            )
            Spacer(Modifier.height(10.dp))
            ComposeInput(
                value = message,
                onValueChange = { message = it },
                placeholder = stringResource(R.string.compose_field_text)
            )
            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.compose_icon),
                style = IosType.subheadBold,
                color = palette.label
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NotificationIcons.all.forEach { (key, resId) ->
                    val selected = key == iconKey
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(
                                if (selected) palette.blue else palette.searchFill,
                                CircleShape
                            )
                            .clickable { iconKey = key },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(resId),
                            contentDescription = key,
                            tint = if (selected) Color.White else palette.secondaryLabel,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                dateTime = (dateTime.clone() as Calendar).apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, day)
                                }
                            },
                            dateTime.get(Calendar.YEAR),
                            dateTime.get(Calendar.MONTH),
                            dateTime.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.compose_date),
                    style = IosType.body,
                    color = palette.label,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    DateFormat.getDateInstance(DateFormat.MEDIUM)
                        .format(Date(dateTime.timeInMillis)),
                    style = IosType.body,
                    color = palette.blue
                )
            }
            androidx.compose.material3.HorizontalDivider(
                thickness = 0.5.dp,
                color = palette.separator
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                dateTime = (dateTime.clone() as Calendar).apply {
                                    set(Calendar.HOUR_OF_DAY, hour)
                                    set(Calendar.MINUTE, minute)
                                }
                            },
                            dateTime.get(Calendar.HOUR_OF_DAY),
                            dateTime.get(Calendar.MINUTE),
                            android.text.format.DateFormat.is24HourFormat(context)
                        ).show()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.compose_time),
                    style = IosType.body,
                    color = palette.label,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(Date(dateTime.timeInMillis)),
                    style = IosType.body,
                    color = palette.blue
                )
            }

            Spacer(Modifier.height(20.dp))
            val canSend = title.isNotBlank() || message.isNotBlank()
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (canSend) palette.blue else palette.searchFill,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(enabled = canSend) {
                        onCreate(title.trim(), message.trim(), dateTime.timeInMillis, iconKey)
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.action_send),
                    style = IosType.headline,
                    color = if (canSend) Color.White else palette.secondaryLabel
                )
            }
        }
    }
}

@Composable
private fun ComposeInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val palette = LocalIosPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(palette.searchFill, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = IosType.body, color = palette.secondaryLabel)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = IosType.body.copy(color = palette.label),
            cursorBrush = SolidColor(palette.blue),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun IosSearchBar(value: String, onValueChange: (String) -> Unit) {
    val palette = LocalIosPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.searchFill, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = palette.secondaryLabel,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    stringResource(R.string.search_hint),
                    style = IosType.body,
                    color = palette.secondaryLabel
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = IosType.body.copy(color = palette.label),
                cursorBrush = SolidColor(palette.blue),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Text(
                stringResource(R.string.action_cancel),
                style = IosType.subhead,
                color = palette.blue,
                modifier = Modifier.clickable { onValueChange("") }
            )
        }
    }
}

@Composable
private fun IosActionRow(
    text: String,
    locked: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalIosPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.card, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = IosType.body,
            color = if (destructive) palette.red else palette.blue,
            modifier = Modifier.weight(1f)
        )
        if (locked) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = palette.secondaryLabel,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun PermissionCard() {
    val context = LocalContext.current
    val palette = LocalIosPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.card, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(stringResource(R.string.perm_title), style = IosType.headline, color = palette.label)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.perm_body),
            style = IosType.subhead,
            color = palette.secondaryLabel
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.perm_hint),
            style = IosType.footnote,
            color = palette.secondaryLabel
        )
        Spacer(Modifier.height(12.dp))
        Row {
            IosPillButton(stringResource(R.string.allow_access), filled = true) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            Spacer(Modifier.width(8.dp))
            IosPillButton(stringResource(R.string.open_app_info), filled = false) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                )
            }
        }
    }
}


@Composable
private fun BatteryCard(onAllow: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalIosPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.card, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.battery_title),
            style = IosType.headline,
            color = palette.label
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.battery_body),
            style = IosType.subhead,
            color = palette.secondaryLabel
        )
        Spacer(Modifier.height(12.dp))
        Row {
            IosPillButton(stringResource(R.string.battery_allow), filled = true, onClick = onAllow)
            Spacer(Modifier.width(8.dp))
            IosPillButton(stringResource(R.string.onb_later), filled = false, onClick = onDismiss)
        }
    }
}

@Composable
private fun TelemetryCard(onAnswer: (Boolean) -> Unit) {
    val palette = LocalIosPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.card, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.telemetry_card_title),
            style = IosType.headline,
            color = palette.label
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.telemetry_card_body),
            style = IosType.subhead,
            color = palette.secondaryLabel
        )
        Spacer(Modifier.height(12.dp))
        Row {
            IosPillButton(stringResource(R.string.telemetry_allow), filled = true) {
                onAnswer(true)
            }
            Spacer(Modifier.width(8.dp))
            IosPillButton(stringResource(R.string.telemetry_deny), filled = false) {
                onAnswer(false)
            }
        }
    }
}

@Composable
private fun IosPillButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val palette = LocalIosPalette.current
    Box(
        Modifier
            .background(
                if (filled) palette.blue else palette.searchFill,
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            style = IosType.subheadBold,
            color = if (filled) Color.White else palette.blue
        )
    }
}

@Composable
private fun EmptyState(listenerEnabled: Boolean) {
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
            if (listenerEnabled) stringResource(R.string.empty_none_title)
            else stringResource(R.string.empty_noaccess_title),
            style = IosType.title3,
            color = palette.label
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (listenerEnabled) stringResource(R.string.empty_none_sub)
            else stringResource(R.string.empty_noaccess_sub),
            style = IosType.subhead,
            color = palette.secondaryLabel
        )
    }
}

@Composable
private fun rememberAppIcon(packageName: String): ImageBitmap? {
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

/** One app in the history overview: icon, name, how many entries are stored. */
@Composable
private fun AppGroupRow(group: AppGroup, onClick: () -> Unit) {
    val palette = LocalIosPalette.current
    val appIcon = rememberAppIcon(group.packageName)
    val timeText = remember(group.latestAt) {
        DateUtils.getRelativeTimeSpanString(
            group.latestAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.card, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (appIcon != null) {
                Image(appIcon, contentDescription = null, modifier = Modifier.size(34.dp))
            } else {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = palette.secondaryLabel,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                group.appLabel,
                style = IosType.headline,
                color = palette.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(timeText, style = IosType.caption, color = palette.secondaryLabel)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .defaultMinSize(minWidth = 24.dp)
                .background(palette.blue, RoundedCornerShape(12.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (group.count > 99) "99+" else group.count.toString(),
                style = IosType.caption,
                color = Color.White
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.separator,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Header of an opened app inside the history; tapping it goes back to the apps. */
@Composable
private fun OpenedAppRow(label: String, onBack: () -> Unit) {
    val palette = LocalIosPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onBack)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.section_apps),
            tint = palette.blue,
            modifier = Modifier.size(24.dp)
        )
        Text(
            label,
            style = IosType.title3,
            color = palette.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationCard(
    item: StoredNotification,
    selected: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onRepost: () -> Unit,
) {
    val palette = LocalIosPalette.current
    val appIcon = rememberAppIcon(item.packageName)
    val timeText = remember(item.postedAt) {
        DateUtils.getRelativeTimeSpanString(
            item.postedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.card, RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = { if (selectionMode) onToggle() else onOpen() },
                onLongClick = onToggle
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionCheck(selected)
            Spacer(Modifier.width(12.dp))
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val customIconRes = NotificationIcons.res(item.customIcon)
                if (customIconRes != null) {
                    Icon(
                        painterResource(customIconRes),
                        contentDescription = null,
                        tint = palette.blue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                } else {
                    appIcon?.let {
                        Image(
                            it,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text(
                    item.appLabel.uppercase(),
                    style = IosType.caption,
                    color = palette.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.weight(1f))
                if (item.blocked) {
                    Text(
                        stringResource(R.string.badge_blocked),
                        style = IosType.caption,
                        color = Color(0xFFFF9500)
                    )
                    Spacer(Modifier.width(8.dp))
                } else if (item.removedAt != null) {
                    Text(
                        stringResource(R.string.badge_removed),
                        style = IosType.caption,
                        color = palette.red
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (IntentCache.has(item.sbnKey)) {
                    Icon(
                        painterResource(R.drawable.ic_boost),
                        contentDescription = null,
                        tint = palette.blue,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(timeText, style = IosType.caption, color = palette.secondaryLabel)
            }
            Spacer(Modifier.height(4.dp))
            if (item.title.isNotBlank()) {
                Text(
                    item.title,
                    style = IosType.subheadBold,
                    color = palette.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.text.isNotBlank()) {
                Text(
                    item.text,
                    style = IosType.subhead,
                    color = palette.label,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            item.scheduledFor?.let {
                Spacer(Modifier.height(8.dp))
                ReminderChip(it)
            }
        }

        if (!selectionMode) {
            val haptic = LocalHapticFeedback.current
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(30.dp)
                    .background(palette.blue, CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRepost()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_resend),
                    contentDescription = stringResource(R.string.action_resend),
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun ScheduledCard(
    item: StoredNotification,
    selected: Boolean,
    selectionMode: Boolean,
    delivery: Int,
    onDelivery: (Int) -> Unit,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val palette = LocalIosPalette.current
    val appIcon = rememberAppIcon(item.packageName)

    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.card, RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = { if (selectionMode) onToggle() else onOpen() },
                onLongClick = onToggle
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionCheck(selected)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val customIconRes = NotificationIcons.res(item.customIcon)
                if (customIconRes != null) {
                    Icon(
                        painterResource(customIconRes),
                        contentDescription = null,
                        tint = palette.blue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                } else {
                    appIcon?.let {
                        Image(it, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text(
                    item.appLabel.uppercase(),
                    style = IosType.caption,
                    color = palette.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (IntentCache.has(item.sbnKey)) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(20.dp)
                            .background(palette.blue, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_boost),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (item.title.isNotBlank()) {
                Text(
                    item.title,
                    style = IosType.subheadBold,
                    color = palette.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.text.isNotBlank()) {
                Text(
                    item.text,
                    style = IosType.subhead,
                    color = palette.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            item.scheduledFor?.let {
                Spacer(Modifier.height(8.dp))
                ReminderChip(it)
            }
        }
        if (!selectionMode) {
            Spacer(Modifier.width(10.dp))
            DeliveryGear(mask = delivery, onChange = onDelivery)
        }
    }
}

/** When a pending reminder fires, as the pill shown on history and Snoozed cards. */
@Composable
private fun ReminderChip(scheduledFor: Long, modifier: Modifier = Modifier) {
    val palette = LocalIosPalette.current
    val timeText = remember(scheduledFor) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(scheduledFor))
    }
    Text(
        stringResource(R.string.scheduled_for, timeText),
        style = IosType.caption,
        color = palette.blue,
        modifier = modifier
            .background(palette.blue.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun SnoozedEmptyState() {
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
            stringResource(R.string.snoozed_empty_title),
            style = IosType.title3,
            color = palette.label
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.snoozed_empty_sub),
            style = IosType.subhead,
            color = palette.secondaryLabel,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun PaginationRow(
    currentPage: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val palette = LocalIosPalette.current
    val canPrevious = currentPage > 0
    val canNext = currentPage < pageCount - 1
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(palette.card, CircleShape)
                .clickable(enabled = canPrevious, onClick = onPrevious),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = if (canPrevious) palette.blue else palette.separator,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            stringResource(R.string.page_of, currentPage + 1, pageCount),
            style = IosType.footnote,
            color = palette.secondaryLabel,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Box(
            Modifier
                .size(32.dp)
                .background(palette.card, CircleShape)
                .clickable(enabled = canNext, onClick = onNext),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (canNext) palette.blue else palette.separator,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SelectionToolbar(onSelectAll: () -> Unit) {
    val palette = LocalIosPalette.current
    Surface(color = palette.card) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.select_all),
                style = IosType.headline,
                color = palette.blue,
                modifier = Modifier
                    .clickable(onClick = onSelectAll)
                    .padding(horizontal = 24.dp, vertical = 2.dp)
            )
        }
    }
}
