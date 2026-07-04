package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.domain.GoalVars
import com.example.domain.LockMessage
import com.example.domain.LockMessages
import com.example.service.AllowedApps
import com.example.service.LockInteraction
import com.example.service.LockScreenController
import com.example.ui.components.PaymentSheetContent
import com.example.util.Connectivity
import com.example.ui.theme.SaveLockAmber
import com.example.ui.theme.SaveLockPrimary
import com.example.ui.theme.SaveLockRed
import com.example.viewmodel.DashboardViewModel
import com.example.viewmodel.LockOverlayViewModel
import com.example.viewmodel.PasteStatus
import com.example.viewmodel.PaymentStatus
import com.example.viewmodel.RecoveryViewModel

private enum class Panel { Main, Pay, Recovery, PasteCode }

/**
 * The lock screen shown inside the full-screen accessibility overlay (and in the in-app preview).
 * IMPORTANT: this uses NO Dialog / Popup / ModalBottomSheet composables — those need an Activity
 * window and would crash inside a raw WindowManager overlay. Everything is rendered inline.
 *
 * Only two ways out are offered: PAY a due plan, or ENTER A RECOVERY CODE. There is deliberately no
 * emergency/home/back escape here (Safe Mode remains the true escape hatch).
 */
@Composable
fun LockScreenContent(
    dashboardViewModel: DashboardViewModel,
    lockViewModel: LockOverlayViewModel,
    recoveryViewModel: RecoveryViewModel,
    modifier: Modifier = Modifier
) {
    val dash by dashboardViewModel.uiState.collectAsState()
    val lock by lockViewModel.uiState.collectAsState()
    val recovery by recoveryViewModel.uiState.collectAsState()
    val paste by dashboardViewModel.pasteStatus.collectAsState()
    val context = LocalContext.current

    var panel by remember { mutableStateOf(Panel.Main) }

    // Jump to the payment panel whenever a payment is in progress/finished.
    LaunchedEffect(dash.paymentStatus) {
        if (dash.paymentStatus != PaymentStatus.Idle) panel = Panel.Pay
    }

    // While the STK PIN is being requested, let the M-Pesa PIN dialog show ON TOP of the lock: make
    // the overlay non-focusable and pause its re-assert so it isn't slammed over the prompt. Restored
    // the moment the payment resolves. The phone still looks locked; no other app becomes reachable.
    LaunchedEffect(dash.paymentStatus) {
        val inProgress = dash.paymentStatus == PaymentStatus.Requesting ||
            dash.paymentStatus == PaymentStatus.WaitingForSTK
        LockInteraction.paymentInProgress = inProgress
        LockScreenController.setFocusable(context, !inProgress)
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (panel) {
            Panel.Main -> MainPanel(
                fullLockdown = lock.fullLockdown,
                duePlans = dash.plans.filter { it.isLocking },
                onPay = { id ->
                    dashboardViewModel.resetPaymentState()
                    dashboardViewModel.openPaymentForPlan(id)
                    panel = Panel.Pay
                },
                onRecovery = { panel = Panel.Recovery },
                onPasteCode = { dashboardViewModel.resetPasteStatus(); panel = Panel.PasteCode }
            )

            Panel.PasteCode -> PasteCodePanel(
                status = paste,
                onSubmit = { dashboardViewModel.confirmPastedMpesaCode(it) },
                onBack = { dashboardViewModel.resetPasteStatus(); panel = Panel.Main }
            )

            Panel.Pay -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PaymentSheetContent(
                    amount = dash.chargeAmount,
                    phoneNumber = dash.mpesaNumber,
                    paymentStatus = dash.paymentStatus,
                    phoneError = dash.paymentPhoneError,
                    onPhoneChange = { dashboardViewModel.updateMpesaNumber(it) },
                    onSendRequest = { dashboardViewModel.triggerPayment() },
                    onDismiss = {
                        dashboardViewModel.resetPaymentState()
                        panel = Panel.Main
                    },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                    editableAmount = dash.payIsFlexible,
                    amountText = dash.payAmountText,
                    minAmount = dash.payMinAmount,
                    amountError = dash.paymentAmountError,
                    onAmountChange = { dashboardViewModel.updatePayAmount(it) }
                )
            }

            Panel.Recovery -> RecoveryPanel(
                enteredCode = recovery.enteredCode,
                error = recovery.codeValidationError,
                success = recovery.codeValidationSuccess,
                onCodeChange = { recoveryViewModel.updateEnteredCode(it) },
                onSubmit = { recoveryViewModel.submitRecoveryCode() },
                onBack = { recoveryViewModel.resetValidationState(); panel = Panel.Main }
            )
        }
    }
}

@Composable
private fun MainPanel(
    fullLockdown: Boolean,
    duePlans: List<com.example.viewmodel.PlanRow>,
    onPay: (Long) -> Unit,
    onRecovery: () -> Unit,
    onPasteCode: () -> Unit
) {
    val context = LocalContext.current
    // Poll connectivity + resolve the allowed apps for the launcher row.
    var connTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(4000); connTick++ } }
    val hasNet = remember(connTick) { Connectivity.hasInternet(context) }
    val wifiOn = remember(connTick) { Connectivity.isWifiOn(context) }
    val dataOn = remember(connTick) { Connectivity.isMobileDataOn(context) }
    val allowedApps = remember { AllowedApps.entries(context) }

    // Rotate the provoking copy once per day. The pool (savings vs goal) follows the primary due
    // plan; goal copy is filled with that goal's name, days left, amount remaining and percent.
    val dayIndex = remember { java.time.LocalDate.now().toEpochDay() }
    val primary = duePlans.firstOrNull()
    val message = remember(dayIndex, primary?.id, primary?.isGoal) {
        when {
            primary == null ->
                LockMessage("Phone Locked", "Pay a due plan below or enter a recovery code to unlock.")
            primary.isGoal -> LockMessages.forGoal(
                dayIndex,
                GoalVars(
                    goalName = primary.name,
                    daysLeft = primary.goalDaysLeft,
                    amountRemaining = primary.goalAmountRemaining,
                    percent = primary.goalPercent
                )
            )
            else -> LockMessages.forSavings(dayIndex)
        }
    }
    // Scale the headline down as it gets longer so even the long titles fit without clipping.
    val (titleSize, titleLine) = when {
        message.title.length <= 28 -> 26.sp to 32.sp
        message.title.length <= 52 -> 22.sp to 28.sp
        else -> 19.sp to 25.sp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(SaveLockRed.copy(alpha = 0.15f))
                .border(2.dp, SaveLockRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = "Locked", tint = SaveLockRed, modifier = Modifier.size(40.dp))
        }

        Spacer(Modifier.height(22.dp))
        Text(
            message.title,
            fontSize = titleSize,
            lineHeight = titleLine,
            fontWeight = FontWeight.Black,
            color = SaveLockRed,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            message.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (fullLockdown) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Only SIM Toolkit, Messages and the Phone are open — pay your till, read the code, or dial.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Offline? Offer to switch a radio on so an online M-Pesa payment can go through. Hidden when
        // there's already internet, and each icon hidden when that radio is already on.
        if (!hasNet && (!wifiOn || !dataOn)) {
            Spacer(Modifier.height(16.dp))
            Text(
                "No internet — turn one on to pay online:",
                style = MaterialTheme.typography.bodySmall,
                color = SaveLockAmber,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!wifiOn) {
                    ConnRadioButton("Wi-Fi", Icons.Default.Wifi) {
                        LockInteraction.grantSettings(60)
                        runCatching { context.startActivity(Connectivity.wifiPanelIntent()) }
                    }
                }
                if (!dataOn) {
                    ConnRadioButton("Data", Icons.Default.SignalCellularAlt) {
                        LockInteraction.grantSettings(60)
                        runCatching { context.startActivity(Connectivity.mobileDataPanelIntent()) }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (duePlans.isEmpty()) {
            Text("Unlocking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            duePlans.forEach { plan ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${plan.typeLabel} • ${plan.detailLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { onPay(plan.id) },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("lock_pay_${plan.id}"),
                            colors = ButtonDefaults.buttonColors(containerColor = SaveLockPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Pay KES %,d & unlock".format(plan.payAmount), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Paid the till already? Paste the M-Pesa code to confirm without waiting for the auto-detect.
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onPasteCode,
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("lock_paste_code"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("I paid — enter M-Pesa code", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRecovery, modifier = Modifier.height(48.dp)) {
            Icon(Icons.Default.VpnKey, contentDescription = null, tint = SaveLockAmber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Enter emergency recovery code", fontWeight = FontWeight.Bold, color = SaveLockAmber)
        }

        // Launcher row for the only apps allowed during lockdown, so the user can reach them directly.
        if (fullLockdown && allowedApps.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))
            Text(
                "Pay offline",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                allowedApps.forEach { entry ->
                    val icon = when (entry.kind) {
                        AllowedApps.Kind.SIM_TOOLKIT -> Icons.Default.Apps
                        AllowedApps.Kind.MESSAGES -> Icons.Default.Sms
                        AllowedApps.Kind.DIALER -> Icons.Default.Call
                    }
                    AllowedAppIcon(entry.label, icon) {
                        entry.launch?.let { runCatching { context.startActivity(it) } }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Small pill button to switch a radio on from the lock screen. */
@Composable
private fun ConnRadioButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.Bold)
    }
}

/** Tappable icon+label for an allowed app on the lock screen. */
@Composable
private fun AllowedAppIcon(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(SaveLockPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = SaveLockPrimary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RecoveryPanel(
    enteredCode: String,
    error: String?,
    success: Boolean,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (success) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SaveLockPrimary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Unlocked!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = SaveLockPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Recovery code accepted. Releasing the lock…", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Text("Enter Recovery Code", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Enter a valid, unused recovery code to release the lock immediately (works fully offline).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = enteredCode,
                onValueChange = onCodeChange,
                placeholder = { Text("SLXX-XXXX") },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = 2.sp),
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it, color = SaveLockRed) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().height(84.dp).testTag("lock_recovery_field")
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SaveLockPrimary),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Submit Unlock Code", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun PasteCodePanel(
    status: PasteStatus,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (status is PasteStatus.Success) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SaveLockPrimary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Confirmed!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = SaveLockPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Payment matched. Unlocking…", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Text("Enter M-Pesa Code", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Paid your till already? Type the confirmation code from the M-Pesa message (e.g. SL74XXABCD) to unlock. Works fully offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            val failed = status as? PasteStatus.Failed
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                placeholder = { Text("SL74XXABCD") },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = 2.sp),
                singleLine = true,
                isError = failed != null,
                supportingText = { failed?.let { Text(it.message, color = SaveLockRed) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit(code) }),
                modifier = Modifier.fillMaxWidth().height(84.dp).testTag("lock_paste_field")
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onSubmit(code) },
                enabled = status != PasteStatus.Checking,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SaveLockPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (status == PasteStatus.Checking) "Checking your messages…" else "Confirm payment",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
