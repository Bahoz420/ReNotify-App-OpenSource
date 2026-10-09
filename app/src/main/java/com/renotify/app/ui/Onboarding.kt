package com.renotify.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.renotify.app.R
import com.renotify.app.ui.theme.IosType
import com.renotify.app.ui.theme.LocalIosPalette

/**
 * First-run flow, two steps:
 * 1. Disclosure + unlock restricted settings via app info (sideloads).
 * 2. Enable notification listener access.
 * The POST_NOTIFICATIONS dialog fires right at the start.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalIosPalette.current
    var step by remember { mutableIntStateOf(1) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Finish automatically once the user flips the switch in system settings.
    LifecycleResumeEffect(Unit) {
        if (NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        ) {
            onFinished()
        }
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            Modifier.size(88.dp).background(palette.blue, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (step == 1) Icons.Default.Settings else Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(26.dp))
        Text(
            stringResource(if (step == 1) R.string.onb_title1 else R.string.onb_title2),
            style = IosType.largeTitle.copy(fontSize = 26.sp),
            color = palette.label,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(if (step == 1) R.string.onb_body1 else R.string.onb_body2),
            style = IosType.subhead,
            color = palette.secondaryLabel,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        if (step == 1) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(palette.card, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    stringResource(R.string.onb_steps),
                    style = IosType.subhead,
                    color = palette.label,
                    lineHeight = 26.sp
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Box(
            Modifier
                .fillMaxWidth()
                .background(palette.blue, RoundedCornerShape(14.dp))
                .clickable {
                    if (step == 1) {
                        context.startActivity(
                            Intent(
                                SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    } else {
                        context.startActivity(
                            Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    }
                }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(if (step == 1) R.string.open_app_info else R.string.allow_access),
                style = IosType.headline.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(if (step == 1) R.string.onb_continue else R.string.onb_later),
            style = IosType.subhead,
            color = if (step == 1) palette.blue else palette.secondaryLabel,
            modifier = Modifier
                .clickable { if (step == 1) step = 2 else onFinished() }
                .padding(8.dp)
        )
        Spacer(Modifier.height(14.dp))
    }
}
