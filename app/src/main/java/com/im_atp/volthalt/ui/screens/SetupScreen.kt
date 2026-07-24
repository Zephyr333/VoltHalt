package com.im_atp.volthalt.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.im_atp.volthalt.R

@Composable
fun SetupScreen(
    onFinish: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestTile: () -> Unit,
    onRequestFullScreenIntent: () -> Unit = {}
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isBatteryUnrestricted     by remember { mutableStateOf(false) }
    var isNotificationsGranted    by remember { mutableStateOf(false) }
    var isFullScreenIntentGranted by remember { mutableStateOf(false) }
    var isTileAdded               by remember { mutableStateOf(false) }

    // Re-check permission states every time the user comes back from a system settings screen.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                isBatteryUnrestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    pm.isIgnoringBatteryOptimizations(context.packageName)
                } else {
                    true
                }

                isNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                isFullScreenIntentGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.canUseFullScreenIntent()
                } else {
                    true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text  = "Welcome to",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Text(
                text       = stringResource(R.string.app_name),
                style      = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text      = "Let's set up a few things to ensure your alarm works perfectly in the background.",
                style     = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(40.dp))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SetupStepCard(
                    title       = "Notifications",
                    description = "Required to alert you when your battery reaches the target level.",
                    icon        = Icons.Default.Notifications,
                    isCompleted = isNotificationsGranted,
                    actionText  = "Allow",
                    onAction    = onRequestNotifications
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            SetupStepCard(
                title       = "Unrestricted Battery",
                description = "Crucial for background monitoring. Disabling battery optimization is completely safe and does not harm your device — it is only required to ensure VoltHalt rings on time and the Quick Settings tile stays responsive.",
                icon        = Icons.Default.BatteryAlert,
                isCompleted = isBatteryUnrestricted,
                actionText  = "Disable Optimization",
                onAction    = onRequestBattery
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                SetupStepCard(
                    title       = "Alarm Pop-up Permission",
                    description = "Lets VoltHalt show a full-screen alert over your lock screen when the alarm fires. Without this, the alarm will only show as a notification.",
                    icon        = Icons.Default.NotificationImportant,
                    isCompleted = isFullScreenIntentGranted,
                    actionText  = "Allow Pop-ups",
                    onAction    = onRequestFullScreenIntent
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SetupStepCard(
                    title       = "Quick Settings Tile",
                    description = "Add a convenient toggle to your swipe-down menu for instant access to turn the alarm on or off.",
                    icon        = Icons.Default.Settings,
                    isCompleted = isTileAdded,
                    actionText  = "Add Tile",
                    onAction    = {
                        onRequestTile()
                        isTileAdded = true
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick  = onFinish,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text       = "Finish Setup",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SetupStepCard(
    title: String,
    description: String,
    icon: ImageVector,
    isCompleted: Boolean,
    actionText: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape    = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isCompleted,
                        transitionSpec = {
                            (scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200))) togetherWith
                            (scaleOut(tween(150)) + fadeOut(tween(150)))
                        },
                        label = "stepStatusIcon"
                    ) { completed ->
                        Icon(
                            imageVector        = if (completed) Icons.Default.CheckCircle else icon,
                            contentDescription = null,
                            tint               = if (completed) Color.White else MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = title,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    if (isCompleted) {
                        Text(
                            text       = "Completed",
                            style      = MaterialTheme.typography.bodySmall,
                            color      = Color(0xFF4CAF50),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text  = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isCompleted) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick  = onAction,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = actionText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
