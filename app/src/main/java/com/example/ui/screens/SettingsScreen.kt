package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SaveLockAmber
import com.example.ui.theme.SaveLockPrimary
import com.example.ui.theme.SaveLockRed
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.entity.LockMode
import com.example.util.NotificationManagerHelper
import com.example.util.PermissionsHelper
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.ThemeMode

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (granted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SaveLockPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text("On", color = SaveLockPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = SaveLockPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Grant")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToRecoveryCodes: () -> Unit,
    onGenerateNewCodes: () -> Unit,
    onSimulateLockOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Title
            Text(
                text = "SaveLock Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Re-check permission statuses each time the screen resumes (e.g. after returning
            // from a system settings page).
            val lifecycleOwner = LocalLifecycleOwner.current
            var permRefresh by remember { mutableStateOf(0) }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) permRefresh++
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            Text(
                text = "SETUP & PERMISSIONS",
                style = MaterialTheme.typography.labelMedium,
                color = SaveLockPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Finish setting up",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap each item and turn it on. SaveLock can't grant these for you (Android won't allow it).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    PermissionRow(
                        title = "Notifications",
                        subtitle = "Reminders and lock alerts",
                        granted = remember(permRefresh) { PermissionsHelper.isNotificationsEnabled(context) },
                        onGrant = { PermissionsHelper.openNotificationSettings(context) }
                    )
                    PermissionRow(
                        title = "Display over other apps",
                        subtitle = "Lets the lock screen appear over blocked apps",
                        granted = remember(permRefresh) { PermissionsHelper.isOverlayGranted(context) },
                        onGrant = { PermissionsHelper.openOverlaySettings(context) }
                    )
                    PermissionRow(
                        title = "Accessibility (App Blocker)",
                        subtitle = "Detects when you open a blocked app",
                        granted = remember(permRefresh) { PermissionsHelper.isAccessibilityEnabled(context) },
                        onGrant = { PermissionsHelper.openAccessibilitySettings(context) }
                    )
                    PermissionRow(
                        title = "Battery: Unrestricted",
                        subtitle = "Stops the phone from killing SaveLock",
                        granted = remember(permRefresh) { PermissionsHelper.isIgnoringBatteryOptimizations(context) },
                        onGrant = { PermissionsHelper.openBatteryOptimizationSettings(context) }
                    )
                    PermissionRow(
                        title = "Alarms & reminders (exact)",
                        subtitle = "Makes the daily lock fire on time",
                        granted = remember(permRefresh) { PermissionsHelper.canScheduleExactAlarms(context) },
                        onGrant = { PermissionsHelper.openExactAlarmSettings(context) }
                    )
                    PermissionRow(
                        title = "Uninstall protection",
                        subtitle = "Must turn off before uninstalling (optional)",
                        granted = remember(permRefresh) { PermissionsHelper.isDeviceAdminActive(context) },
                        onGrant = { PermissionsHelper.requestDeviceAdmin(context) }
                    )
                }
            }

            // Section 1: Savings & Target
            Text(
                text = "SAVINGS CONFIGURATION",
                style = MaterialTheme.typography.labelMedium,
                color = SaveLockPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Daily Target
                    OutlinedTextField(
                        value = uiState.dailySavingsAmount,
                        onValueChange = { viewModel.updateDailySavingsAmount(it) },
                        label = { Text("Daily Savings Amount (KES)") },
                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = SaveLockPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = uiState.amountError != null,
                        supportingText = { Text(uiState.amountError ?: "Target savings amount due every 24h") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("daily_amount_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SaveLockPrimary,
                            errorBorderColor = SaveLockRed
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // M-Pesa Number
                    OutlinedTextField(
                        value = uiState.mpesaNumber,
                        onValueChange = { viewModel.updateMpesaNumber(it) },
                        label = { Text("Primary M-Pesa Phone Number") },
                        leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = SaveLockPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        isError = uiState.mpesaError != null,
                        supportingText = { Text(uiState.mpesaError ?: "Format: 2547XXXXXXXX") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mpesa_settings_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SaveLockPrimary,
                            errorBorderColor = SaveLockRed
                        )
                    )
                }
            }

            // Section: Lock strictness mode
            Text(
                text = "LOCK STRICTNESS",
                style = MaterialTheme.typography.labelMedium,
                color = SaveLockPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How strict is the lock?",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "\"Chosen apps\" blocks only the apps you tick below. \"Full lockdown\" blocks everything except phone calls and messages — even Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LockMode.values().forEach { mode ->
                            val isSelected = uiState.lockMode == mode
                            val label = when (mode) {
                                LockMode.CHOSEN_APPS -> "Chosen apps"
                                LockMode.FULL_LOCKDOWN -> "Full lockdown"
                            }
                            val icon = when (mode) {
                                LockMode.CHOSEN_APPS -> Icons.Default.Apps
                                LockMode.FULL_LOCKDOWN -> Icons.Default.Lock
                            }
                            ElevatedFilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateLockMode(mode) },
                                label = { Text(label) },
                                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("lockmode_${mode.name.lowercase()}"),
                                colors = FilterChipDefaults.elevatedFilterChipColors(
                                    selectedContainerColor = SaveLockPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = SaveLockPrimary,
                                    selectedLeadingIconColor = SaveLockPrimary
                                )
                            )
                        }
                    }

                    if (uiState.lockMode == LockMode.FULL_LOCKDOWN) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "⚠ Full lockdown also blocks Settings. To unlock you must pay, enter a recovery code, or restart the phone in Safe Mode. Keep your recovery codes written down somewhere safe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SaveLockRed,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Section 3: Distraction Apps Block list — only relevant in chosen-apps mode.
            // Full lockdown blocks everything, so there's nothing to pick here.
            if (uiState.lockMode == LockMode.CHOSEN_APPS) {
            Text(
                text = "RESTRICTED DISTRACTION APPS",
                style = MaterialTheme.typography.labelMedium,
                color = SaveLockPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Missed-Savings Restricted Apps",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "These apps will be blocked if you miss your daily savings target. Restoring access requires saving or a recovery code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // List out apps
                    uiState.distractionApps.forEachIndexed { index, app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleDistractionApp(app.packageName) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic placeholder mock app icon based on name
                                val initials = app.name.take(2)
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (app.isRestricted) SaveLockRed.copy(alpha = 0.2f)
                                            else SaveLockPrimary.copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = if (app.isRestricted) SaveLockRed else SaveLockPrimary
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = app.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Checkbox(
                                checked = app.isRestricted,
                                onCheckedChange = { viewModel.toggleDistractionApp(app.packageName) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = SaveLockRed,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.testTag("app_checkbox_${app.packageName}")
                            )
                        }

                        if (index < uiState.distractionApps.size - 1) {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
            } // end chosen-apps-only distraction section

            // Section 4: Emergency Emergency Actions
            Text(
                text = "EMERGENCY RECOVERY & OVERRIDES",
                style = MaterialTheme.typography.labelMedium,
                color = SaveLockPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Emergency Recovery",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Generate and store recovery keys offline to override locks when M-Pesa is unavailable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Button(
                        onClick = onNavigateToRecoveryCodes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("view_recovery_codes_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("View Recovery Codes")
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.triggerGenerateRecoveryWarning(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_recovery_codes_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SaveLockRed)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Generate New Recovery Codes")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onSimulateLockOverlay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("simulate_lock_overlay_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SaveLockRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Simulate Lockout Overlay Screen")
                        }
                    }
                }
            }

            // Section 5: Theme Settings
            Text(
                text = "THEME & VISUAL PREFERENCES",
                style = MaterialTheme.typography.labelMedium,
                color = SaveLockPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "App Theme Mode",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose light, dark, or automatic system matching theme.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.values().forEach { mode ->
                            val isSelected = uiState.themeMode == mode
                            val label = when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            }
                            val icon = when (mode) {
                                ThemeMode.SYSTEM -> Icons.Default.SettingsSuggest
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.DARK -> Icons.Default.DarkMode
                            }

                            ElevatedFilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateThemeMode(mode) },
                                label = { Text(label) },
                                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_chip_${mode.name.lowercase()}"),
                                colors = FilterChipDefaults.elevatedFilterChipColors(
                                    selectedContainerColor = SaveLockPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = SaveLockPrimary,
                                    selectedLeadingIconColor = SaveLockPrimary
                                )
                            )
                        }
                    }
                }
            }

            // Section 6: Notifications System Testing
            Text(
                text = "NOTIFICATIONS SYSTEM TESTING",
                style = MaterialTheme.typography.labelMedium,
                color = SaveLockPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Simulate Alerts & Reminders",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Trigger real device status bar alerts from SaveLock to test Notification API system integration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                NotificationManagerHelper.showNotification(
                                    context,
                                    NotificationManagerHelper.CHANNEL_SAVINGS_REMINDERS,
                                    101,
                                    "SaveLock Milestone Met 🎉",
                                    "Congratulations! You saved KES ${uiState.dailySavingsAmount} today and secured your device apps."
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SaveLockPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("notify_milestone_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Goal Met", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                NotificationManagerHelper.showNotification(
                                    context,
                                    NotificationManagerHelper.CHANNEL_LOCK_ALERTS,
                                    102,
                                    "Critical Lock Warning! ⚠️",
                                    "A savings payment is due soon. Pay on time to prevent your apps from locking."
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SaveLockRed),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("notify_lock_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Incoming Lock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp)) // Padding from BottomNav
        }

        // Generate Recovery Codes warning dialog
        if (uiState.showGenerateRecoveryWarning) {
            AlertDialog(
                onDismissRequest = { viewModel.triggerGenerateRecoveryWarning(false) },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = SaveLockRed) },
                title = { Text("Regenerate Recovery Codes?") },
                text = {
                    Text("Regenerating recovery codes will invalidate all existing unused recovery codes. You MUST write down the new codes offline. Continue?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.generateNewRecoveryCodes()   // dismiss the warning dialog
                            onGenerateNewCodes()                    // regenerate + open the reveal screen
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SaveLockRed)
                    ) {
                        Text("Regenerate")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.triggerGenerateRecoveryWarning(false) }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
