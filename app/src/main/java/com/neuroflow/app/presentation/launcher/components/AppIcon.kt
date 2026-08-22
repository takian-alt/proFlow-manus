package com.neuroflow.app.presentation.launcher.components

import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import com.neuroflow.app.presentation.launcher.hyperfocus.HyperFocusActivity
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusPreferences
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.RewardEngine
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.neuroflow.app.R
import com.neuroflow.app.presentation.launcher.data.AppInfo
import com.neuroflow.app.presentation.launcher.domain.AdaptiveIconProcessor
import com.neuroflow.app.presentation.launcher.domain.IconShape
import com.neuroflow.app.presentation.launcher.theme.LocalLauncherTheme

/**
 * Reusable app icon composable used throughout the launcher.
 *
 * Responsibilities:
 * 1. Load icon from AppInfo (already cached in AppRepository)
 * 2. Apply AdaptiveIconProcessor shape mask
 * 3. Overlay work profile badge (bottom-right) if isWorkProfile
 * 4. Overlay notification badge (top-end, locale-aware) if badgeCount > 0
 * 5. Overlay lock badge if isLocked
 * 6. Apply 0.4f alpha if focusActive && distractionScore > 70
 * 7. Long-press triggers ShortcutPopup anchored to this icon's position
 *
 * Badge alignment is locale-aware:
 * - LTR: Alignment.TopEnd
 * - RTL: Alignment.TopStart
 *
 * @param appInfo App information including icon, label, package name
 * @param launcherApps LauncherApps system service for shortcuts
 * @param badgeCount Notification badge count (0 = no badge)
 * @param isLocked Whether app requires biometric authentication
 * @param focusActive Whether focus mode is currently active
 * @param hyperFocusPrefs Current hyper focus preferences for blocking check (Requirement 6.1)
 * @param modifier Modifier for the icon container
 * @param onTap Callback when icon is tapped (intercepted if app is blocked)
 * @param onPinToDock Callback to pin app to dock
 * @param onHide Callback to hide app from drawer
 * @param onLock Callback to lock app with biometric authentication
 * @param onLongPress Optional callback invoked before showing shortcut popup
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    appInfo: AppInfo,
    launcherApps: LauncherApps,
    badgeCount: Int = 0,
    isLocked: Boolean = false,
    focusActive: Boolean = false,
    hyperFocusPrefs: HyperFocusPreferences? = null,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onPinToDock: () -> Unit,
    onHide: () -> Unit,
    onLock: () -> Unit = {},
    onAddToHome: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    isInteractive: Boolean = true,
    iconSize: Dp = 48.dp,
    enableLongPress: Boolean = true
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    // Hyper Focus blocking redirect (Requirement 6.1)
    val handleTap: () -> Unit = {
        val prefs = hyperFocusPrefs
        if (prefs != null &&
            prefs.isActive &&
            appInfo.packageName in prefs.blockedPackages &&
            !RewardEngine.isUnlockActive(prefs)
        ) {
            val intent = Intent(context, HyperFocusActivity::class.java).apply {
                putExtra("blocked_package", appInfo.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } else {
            onTap()
        }
    }

    // State for ShortcutPopup
    var showShortcutPopup by remember { mutableStateOf(false) }
    var iconPosition by remember { mutableStateOf(DpOffset.Zero) }

    // Determine badge alignment based on layout direction (RTL support)
    val badgeAlignment = if (layoutDirection == LayoutDirection.Ltr) {
        Alignment.TopEnd
    } else {
        Alignment.TopStart
    }

    // Apply focus mode dimming if distraction score > 70
    val iconAlpha = if (focusActive && appInfo.distractionScore > 70) {
        0.4f
    } else {
        1.0f
    }

    // Process icon with shape mask
    val processedIcon = remember(appInfo.icon, theme.iconShape) {
        val sizePx = (iconSize.value * context.resources.displayMetrics.density).toInt()
        AdaptiveIconProcessor.process(appInfo.icon, theme.iconShape, sizePx, context)
    }

    Box(
        modifier = modifier
            .size(iconSize)
            .onGloballyPositioned { coordinates ->
                // Capture icon position for popup anchoring
                val position = coordinates.positionInWindow()
                iconPosition = with(density) {
                    DpOffset(position.x.toDp(), position.y.toDp())
                }
            }
            .then(
                if (!isInteractive) {
                    Modifier
                } else if (enableLongPress) {
                    Modifier.combinedClickable(
                        onClick = handleTap,
                        onLongClick = {
                            onLongPress?.invoke()
                            // Show ShortcutPopup on long-press (Requirement 5.1)
                            showShortcutPopup = true
                        }
                    )
                } else {
                    Modifier.clickable(onClick = handleTap)
                }
            )
    ) {
        // Main icon with optional dimming
        Image(
            bitmap = processedIcon.asImageBitmap(),
            contentDescription = appInfo.label,
            modifier = Modifier
                .size(iconSize)
                .alpha(iconAlpha)
        )

        // Work profile badge (bottom-right)
        if (appInfo.isWorkProfile) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
            ) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(12.dp)
                ) {
                    // Work badge indicator (small dot)
                }
            }
        }

        // Notification badge (top-end, locale-aware)
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(badgeAlignment)
                    .padding(2.dp)
            ) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Lock badge (center overlay)
        if (isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.Center)
                )
            }
        }
    }

    // ShortcutPopup (anchored to icon position)
    if (showShortcutPopup) {
        ShortcutPopup(
            packageName = appInfo.packageName,
            userHandle = appInfo.userHandle,
            launcherApps = launcherApps,
            offset = iconPosition,
            onDismiss = { showShortcutPopup = false },
            onOpen = handleTap,
            onPinToDock = onPinToDock,
            onHide = onHide,
            onLock = onLock,
            onAddToHome = onAddToHome,
            onAppInfo = {
                // Open app info settings (Requirement 5.6)
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", appInfo.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        )
    }
}

