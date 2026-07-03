package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PaymentSheetContent
import com.example.ui.theme.SaveLockAmber
import com.example.ui.theme.SaveLockPrimary
import com.example.ui.theme.SaveLockRed
import com.example.viewmodel.DashboardViewModel
import com.example.viewmodel.LockOverlayViewModel
import com.example.viewmodel.PaymentStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayScreen(
    viewModel: LockOverlayViewModel,
    dashboardViewModel: DashboardViewModel,
    onNavigateToRecoveryEntry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenEmergency: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val dashboardUiState by dashboardViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var isSheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(dashboardUiState.paymentStatus) {
        if (dashboardUiState.paymentStatus != PaymentStatus.Idle) isSheetOpen = true
    }

    val duePlans = dashboardUiState.plans.filter { it.isLocking }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Phone Locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = SaveLockRed,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = uiState.bannerMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Each plan that is currently due, with its own Pay button.
            if (duePlans.isEmpty()) {
                Text(
                    text = "Resolving…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                duePlans.forEach { plan ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${plan.typeLabel} • ${plan.detailLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    dashboardViewModel.resetPaymentState()
                                    dashboardViewModel.openPaymentForPlan(plan.id)
                                    isSheetOpen = true
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SaveLockPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pay KES %,d & unlock".format(plan.payAmount), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onNavigateToRecoveryEntry,
                modifier = Modifier.testTag("overlay_recovery_link").height(48.dp)
            ) {
                Icon(Icons.Default.VpnKey, contentDescription = null, tint = SaveLockAmber, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Enter emergency recovery code", fontWeight = FontWeight.Bold, color = SaveLockAmber)
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onOpenEmergency,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("overlay_emergency_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SaveLockRed)
            ) {
                Icon(Icons.Default.Emergency, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Emergency call")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (isSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isSheetOpen = false; dashboardViewModel.resetPaymentState() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                PaymentSheetContent(
                    amount = dashboardUiState.chargeAmount,
                    phoneNumber = dashboardUiState.mpesaNumber,
                    paymentStatus = dashboardUiState.paymentStatus,
                    phoneError = dashboardUiState.paymentPhoneError,
                    onPhoneChange = { dashboardViewModel.updateMpesaNumber(it) },
                    onSendRequest = { dashboardViewModel.triggerPayment() },
                    onDismiss = { isSheetOpen = false; dashboardViewModel.resetPaymentState() }
                )
            }
        }
    }
}
