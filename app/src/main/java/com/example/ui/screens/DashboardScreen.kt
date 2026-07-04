package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PaymentSheetContent
import com.example.ui.theme.SaveLockAmber
import com.example.ui.theme.SaveLockPrimary
import com.example.ui.theme.SaveLockPrimaryDark
import com.example.ui.theme.SaveLockRed
import com.example.viewmodel.DashboardViewModel
import com.example.viewmodel.PaymentStatus
import com.example.viewmodel.PlanRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var isSheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.paymentStatus) {
        if (uiState.paymentStatus != PaymentStatus.Idle) isSheetOpen = true
    }

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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SaveLockPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "SaveLock",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (uiState.activeLocks > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SaveLockRed.copy(alpha = 0.15f))
                            .border(1.dp, SaveLockRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${uiState.activeLocks} locked",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = SaveLockRed
                        )
                    }
                }
            }

            // Total saved banner
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Brush.linearGradient(listOf(SaveLockPrimary, SaveLockPrimaryDark)))
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "TOTAL SAVED BALANCE",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = uiState.totalSaved,
                            style = MaterialTheme.typography.headlineLarge,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Across ${uiState.plans.size} active plan${if (uiState.plans.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Section header
            Text(
                text = "YOUR SAVINGS & GOALS",
                style = MaterialTheme.typography.labelMedium,
                color = SaveLockPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )

            if (!uiState.hasPlans) {
                EmptyPlansCard()
            } else {
                if (!uiState.isLockStarted) {
                    StartLockingCard(onStart = { viewModel.startLocking() })
                    Spacer(modifier = Modifier.height(12.dp))
                }
                uiState.plans.forEach { plan ->
                    PlanCard(
                        plan = plan,
                        onSave = {
                            viewModel.resetPaymentState()
                            viewModel.openPaymentForPlan(plan.id)
                            isSheetOpen = true
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        // Payment sheet
        if (isSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isSheetOpen = false; viewModel.resetPaymentState() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PaymentSheetContent(
                        amount = uiState.chargeAmount,
                        phoneNumber = uiState.mpesaNumber,
                        paymentStatus = uiState.paymentStatus,
                        phoneError = uiState.paymentPhoneError,
                        onPhoneChange = { viewModel.updateMpesaNumber(it) },
                        onSendRequest = { viewModel.triggerPayment() },
                        onDismiss = { isSheetOpen = false; viewModel.resetPaymentState() },
                        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                        editableAmount = uiState.payIsFlexible,
                        amountText = uiState.payAmountText,
                        minAmount = uiState.payMinAmount,
                        amountError = uiState.paymentAmountError,
                        onAmountChange = { viewModel.updatePayAmount(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StartLockingCard(onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SaveLockRed.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = SaveLockRed, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Locking is not started",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SaveLockRed
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your plans are saved, but SaveLock will not cover the phone until you start locking.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("start_locking_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SaveLockRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start locking now", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PlanCard(plan: PlanRow, onSave: () -> Unit) {
    val accent = when {
        plan.isComplete -> SaveLockPrimary
        plan.isLocking -> SaveLockRed
        else -> SaveLockPrimary
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("plan_card_${plan.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (plan.isGoal) Icons.Default.Flag else Icons.Default.Savings,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "${plan.typeLabel} • ${plan.detailLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (plan.isLocking) {
                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = SaveLockRed, modifier = Modifier.size(18.dp))
                } else if (plan.isComplete) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Complete", tint = SaveLockPrimary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { plan.progress },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)),
                color = accent,
                trackColor = accent.copy(alpha = 0.15f)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(plan.progressLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = plan.statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (plan.isLocking) SaveLockRed else accent
                )
            }

            if (!plan.isComplete) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("plan_save_${plan.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (plan.isLocking) SaveLockRed else SaveLockPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (plan.isLocking) "Pay KES %,d & unlock".format(plan.payAmount)
                        else "Save KES %,d".format(plan.payAmount),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlansCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Savings, contentDescription = null, tint = SaveLockPrimary, modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("No plans yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Go to Settings → Savings & Goals to create a Savings plan or a Goal. Each runs on its own schedule and locks your phone when a payment is due. Their progress appears here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
