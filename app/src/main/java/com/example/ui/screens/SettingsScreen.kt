package com.example.ui.screens

import android.Manifest
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.entity.LockMode
import com.example.ui.theme.SaveLockAmber
import com.example.ui.theme.SaveLockPrimary
import com.example.ui.theme.SaveLockRed
import com.example.util.PermissionsHelper
import com.example.viewmodel.PlanLite
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.ThemeMode

private data class PermSpec(
    val title: String,
    val subtitle: String,
    val granted: Boolean,
    val onGrant: () -> Unit,
    val how: String? = null
)

@Composable
private fun SectionLabel(text: String, top: Int = 24) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = SaveLockPrimary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = top.dp, bottom = 8.dp)
    )
}

@Composable
private fun AppIcon(drawable: Drawable?, label: String, restricted: Boolean) {
    if (drawable != null) {
        val bmp = remember(drawable) {
            runCatching { drawable.toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
            )
            return
        }
    }
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (restricted) SaveLockRed.copy(alpha = 0.2f) else SaveLockPrimary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.take(2),
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            color = if (restricted) SaveLockRed else SaveLockPrimary
        )
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToRecoveryCodes: () -> Unit,
    onGenerateNewCodes: () -> Unit,
    onSimulateLockOverlay: () -> Unit,
    onCreatePlan: () -> Unit = {},
    onEditPlan: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<PlanLite?>(null) }

    // Runtime RECEIVE_SMS request for offline M-Pesa auto-unlock. On grant, turn the feature on.
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.setSmsAutoUnlock(true) }

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
            Text(
                text = "SaveLock Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Re-check permission statuses each time the screen resumes (e.g. after a settings page).
            val lifecycleOwner = LocalLifecycleOwner.current
            var permRefresh by remember { mutableStateOf(0) }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) permRefresh++
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // ---- Setup & Permissions (only the ones still OFF are shown) ----
            val perms = remember(permRefresh) {
                listOf(
                    PermSpec(
                        "Notifications", "Reminders and lock alerts",
                        PermissionsHelper.isNotificationsEnabled(context),
                        { PermissionsHelper.openNotificationSettings(context) }
                    ),
                    PermSpec(
                        "Display over other apps", "Lets the lock screen appear over blocked apps",
                        PermissionsHelper.isOverlayGranted(context),
                        { PermissionsHelper.openOverlaySettings(context) }
                    ),
                    PermSpec(
                        "Accessibility (App Blocker)", "Detects and blocks apps while locked",
                        PermissionsHelper.isAccessibilityEnabled(context),
                        { PermissionsHelper.openAccessibilitySettings(context) }
                    ),
                    PermSpec(
                        "Battery: Unrestricted", "Stops the phone from killing SaveLock",
                        PermissionsHelper.isIgnoringBatteryOptimizations(context),
                        { PermissionsHelper.openBatteryOptimizationSettings(context) }
                    ),
                    PermSpec(
                        "Alarms & reminders (exact)", "Makes the lock fire on time",
                        PermissionsHelper.canScheduleExactAlarms(context),
                        { PermissionsHelper.openExactAlarmSettings(context) }
                    ),
                    PermSpec(
                        "Uninstall protection", "You must turn this off before uninstalling (optional)",
                        PermissionsHelper.isDeviceAdminActive(context),
                        { PermissionsHelper.requestDeviceAdmin(context) },
                        how = "If the button doesn't open anything on your phone: open the phone's " +
                            "Settings → Security → Device admin apps (on Samsung: Settings → Security " +
                            "and privacy → Other security settings → Device admin apps), then switch " +
                            "SaveLock on. To uninstall later, come back here and turn it off first."
                    )
                )
            }
            val pending = perms.filterNot { it.granted }

            SectionLabel("SETUP & PERMISSIONS", top = 8)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (pending.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = SaveLockPrimary)
                            Text(
                                "All set — SaveLock is fully enabled.",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            "Finish setting up — tap each and turn it on. Android won't let SaveLock enable these for you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        pending.forEachIndexed { i, p ->
                            PendingPermissionRow(p)
                            if (i < pending.size - 1) Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }

            // ---- Savings & Goals management ----
            SectionLabel("SAVINGS & GOALS")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Master switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Saving Enabled", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "Master switch for all locks and triggers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.isSavingEnabled,
                            onCheckedChange = { viewModel.toggleSavingEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = androidx.compose.ui.graphics.Color.White, checkedTrackColor = SaveLockPrimary)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))

                    if (uiState.plans.isEmpty()) {
                        Text(
                            "No plans yet. Create a Savings plan or a Goal — each locks your phone until you pay it for the period.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.plans.forEachIndexed { i, plan ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(plan.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text(plan.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onEditPlan(plan.id) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = SaveLockPrimary, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { deleteTarget = plan }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = SaveLockRed, modifier = Modifier.size(20.dp))
                                }
                            }
                            if (i < uiState.plans.size - 1) Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onCreatePlan,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_create_plan"),
                        colors = ButtonDefaults.buttonColors(containerColor = SaveLockPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create Savings or Goal", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ---- Lock strictness ----
            SectionLabel("LOCK STRICTNESS")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("How strict is the lock?", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "\"Chosen apps\" blocks only the apps you tick below. \"Full lockdown\" blocks the whole phone — even Settings and the launcher — until you pay or use a recovery code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LockMode.values().forEach { mode ->
                            val isSelected = uiState.lockMode == mode
                            val label = if (mode == LockMode.CHOSEN_APPS) "Chosen apps" else "Full lockdown"
                            val icon = if (mode == LockMode.CHOSEN_APPS) Icons.Default.Apps else Icons.Default.Lock
                            ElevatedFilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateLockMode(mode) },
                                label = { Text(label) },
                                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f).testTag("lockmode_${mode.name.lowercase()}"),
                                colors = FilterChipDefaults.elevatedFilterChipColors(
                                    selectedContainerColor = SaveLockPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = SaveLockPrimary,
                                    selectedLeadingIconColor = SaveLockPrimary
                                )
                            )
                        }
                    }
                    if (uiState.lockMode == LockMode.FULL_LOCKDOWN) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "⚠ Full lockdown blocks everything (including Settings). To unlock: pay, use a recovery code, or restart the phone in Safe Mode. Keep your recovery codes written down somewhere safe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SaveLockRed,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // ---- Distraction apps (chosen-apps mode only) ----
            if (uiState.lockMode == LockMode.CHOSEN_APPS) {
                SectionLabel("APPS TO BLOCK")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Tick the apps to lock when a payment is due. Restoring access needs a payment or a recovery code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        if (uiState.distractionApps.isEmpty()) {
                            Text("Loading your installed apps…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        uiState.distractionApps.forEachIndexed { index, app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleDistractionApp(app.packageName) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(app.icon, app.name, app.isRestricted)
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(app.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Checkbox(
                                    checked = app.isRestricted,
                                    onCheckedChange = { viewModel.toggleDistractionApp(app.packageName) },
                                    colors = CheckboxDefaults.colors(checkedColor = SaveLockRed),
                                    modifier = Modifier.testTag("app_checkbox_${app.packageName}")
                                )
                            }
                            if (index < uiState.distractionApps.size - 1) {
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            // ---- Recovery ----
            SectionLabel("EMERGENCY RECOVERY & OVERRIDES")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Generate and write down recovery codes to unlock offline when M-Pesa is unavailable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onNavigateToRecoveryCodes,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("view_recovery_codes_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("View Recovery Codes")
                    }
                    OutlinedButton(
                        onClick = { viewModel.triggerGenerateRecoveryWarning(true) },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_recovery_codes_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SaveLockRed)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Generate New Recovery Codes")
                    }
                    Button(
                        onClick = onSimulateLockOverlay,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("simulate_lock_overlay_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SaveLockRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Preview Lock Screen")
                    }
                }
            }

            // ---- Offline M-Pesa auto-unlock (reads the till payment SMS) ----
            SectionLabel("OFFLINE M-PESA UNLOCK")
            val smsGranted = remember(permRefresh) { PermissionsHelper.isSmsPermissionGranted(context) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Pay your till directly from the M-Pesa menu (works on signal even with NO mobile data). " +
                            "SaveLock reads Safaricom's confirmation text and unlocks automatically — no internet needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uiState.tillName,
                        onValueChange = { viewModel.updateTillName(it) },
                        label = { Text("Your till/business name (exactly as in the M-Pesa SMS)") },
                        placeholder = { Text("e.g. JOHN'S SHOP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("till_name_field")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-unlock from M-Pesa SMS", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(
                                if (uiState.tillName.isBlank()) "Enter your till name above first"
                                else if (!smsGranted) "Needs SMS permission — tap to grant"
                                else "On — a till payment will unlock the phone offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.smsAutoUnlockEnabled && smsGranted,
                            enabled = uiState.tillName.isNotBlank(),
                            onCheckedChange = { on ->
                                if (!on) {
                                    viewModel.setSmsAutoUnlock(false)
                                } else if (smsGranted) {
                                    viewModel.setSmsAutoUnlock(true)
                                } else {
                                    // Ask for RECEIVE_SMS; the launcher flips the switch on when granted.
                                    smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = androidx.compose.ui.graphics.Color.White, checkedTrackColor = SaveLockPrimary)
                        )
                    }
                    Text(
                        "Tip: this also confirms normal M-Pesa (STK) payments if your internet drops mid-payment. " +
                            "True no-signal unlock still uses a recovery code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- Theme ----
            SectionLabel("THEME")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("App theme", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.values().forEach { mode ->
                            val isSelected = uiState.themeMode == mode
                            val label = when (mode) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }
                            val icon = when (mode) { ThemeMode.SYSTEM -> Icons.Default.SettingsSuggest; ThemeMode.LIGHT -> Icons.Default.LightMode; ThemeMode.DARK -> Icons.Default.DarkMode }
                            ElevatedFilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateThemeMode(mode) },
                                label = { Text(label) },
                                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f).testTag("theme_chip_${mode.name.lowercase()}"),
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

            Spacer(modifier = Modifier.height(100.dp))
        }

        // Delete plan confirmation
        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = SaveLockRed) },
                title = { Text("Delete \"${target.name}\"?") },
                text = { Text("This removes the plan and its saved progress from this phone. This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = { viewModel.deletePlan(target.id); deleteTarget = null },
                        colors = ButtonDefaults.buttonColors(containerColor = SaveLockRed)
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
            )
        }

        // Generate Recovery Codes warning dialog
        if (uiState.showGenerateRecoveryWarning) {
            AlertDialog(
                onDismissRequest = { viewModel.triggerGenerateRecoveryWarning(false) },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = SaveLockRed) },
                title = { Text("Regenerate Recovery Codes?") },
                text = { Text("This invalidates all existing unused codes. You MUST write down the new codes offline. Continue?") },
                confirmButton = {
                    Button(
                        onClick = { viewModel.generateNewRecoveryCodes(); onGenerateNewCodes() },
                        colors = ButtonDefaults.buttonColors(containerColor = SaveLockRed)
                    ) { Text("Regenerate") }
                },
                dismissButton = { TextButton(onClick = { viewModel.triggerGenerateRecoveryWarning(false) }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun PendingPermissionRow(p: PermSpec) {
    var showHow by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = SaveLockAmber, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(p.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(p.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = p.onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = SaveLockAmber),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Turn on", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold) }
        }
        if (p.how != null) {
            TextButton(onClick = { showHow = !showHow }, contentPadding = PaddingValues(0.dp)) {
                Icon(
                    if (showHow) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (showHow) "Hide steps" else "How do I turn this on?", style = MaterialTheme.typography.labelMedium)
            }
            if (showHow) {
                Text(
                    p.how,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 34.dp, bottom = 4.dp)
                )
            }
        }
    }
}
