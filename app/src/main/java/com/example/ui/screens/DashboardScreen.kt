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
    onNavigateToCreatePlan: () -> Unit,
    onEditPlan: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var isSheetOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PlanRow?>(null) }

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

            // Section header + create
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "YOUR SAVINGS & GOALS",
                    style = MaterialTheme.typography.labelMedium,
                    color = SaveLockPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                TextButton(onClick = onNavigateToCreatePlan, modifier = Modifier.testTag("create_plan_button")) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", fontWeight = FontWeight.Bold)
                }
            }

            if (!uiState.hasPlans) {
                EmptyPlansCard(onNavigateToCreatePlan)
            } else {
                uiState.plans.forEach { plan ->
                    PlanCard(
                        plan = plan,
                        onSave = {
                            viewModel.resetPaymentState()
                            viewModel.openPaymentForPlan(plan.id)
                            isSheetOpen = true
                        },
                        onEdit = { onEditPlan(plan.id) },
                        onDelete = { deleteTarget = plan }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Master switch
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.isSavingEnabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (uiState.isSavingEnabled) SaveLockPrimary else SaveLockRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Saving Enabled", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Master switch for all locks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = uiState.isSavingEnabled,
                        onCheckedChange = { viewModel.toggleSavingEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SaveLockPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        // Disable confirmation
        if (uiState.showDisablingConfirmation) {
            AlertDialog(
                onDismissRequest = { viewModel.confirmDisableSaving(false) },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = SaveLockRed) },
                title = { Text("Disable Saving?") },
                text = { Text("This turns off all locks and triggers for every plan. Are you sure?") },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmDisableSaving(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = SaveLockRed)
                    ) { Text("Disable") }
                },
                dismissButton = { TextButton(onClick = { viewModel.confirmDisableSaving(false) }) { Text("Cancel") } }
            )
        }

        // Delete confirmation
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

        // Payment sheet
        if (isSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isSheetOpen = false; viewModel.resetPaymentState() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                PaymentSheetContent(
                    amount = uiState.chargeAmount,
                    phoneNumber = uiState.mpesaNumber,
                    paymentStatus = uiState.paymentStatus,
                    phoneError = uiState.paymentPhoneError,
                    onPhoneChange = { viewModel.updateMpesaNumber(it) },
                    onSendRequest = { viewModel.triggerPayment() },
                    onDismiss = { isSheetOpen = false; viewModel.resetPaymentState() }
                )
            }
        }
    }
}

@Composable
private fun PlanCard(plan: PlanRow, onSave: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
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
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
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
private fun EmptyPlansCard(onCreate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.AddCircle, contentDescription = null, tint = SaveLockPrimary, modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("No plans yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Create a Savings plan (save a set amount every period) or a Goal (reach a target). Each runs on its own schedule and locks your phone when a payment is due.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SaveLockPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create your first plan", fontWeight = FontWeight.Bold)
            }
        }
    }
}
