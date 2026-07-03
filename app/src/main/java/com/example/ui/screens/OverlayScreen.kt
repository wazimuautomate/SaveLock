package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onOpenDialer: () -> Unit = {},
    onOpenMessages: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val dashboardUiState by dashboardViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var isSheetOpen by remember { mutableStateOf(false) }

    // Synchronize bottom sheet open state with Payment Status
    LaunchedEffect(dashboardUiState.paymentStatus) {
        if (dashboardUiState.paymentStatus != PaymentStatus.Idle) {
            isSheetOpen = true
        }
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // RESTRICTED RED HEADER EMBLEM
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(SaveLockRed.copy(alpha = 0.15f))
                    .border(2.dp, SaveLockRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Device Restricted",
                    tint = SaveLockRed,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "You Haven't Saved Today",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = SaveLockRed,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Non-aggressive but highly legible banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiState.bannerMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Amount Due Display
            Text(
                text = "DAILY TARGET DUE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = uiState.amountDue,
                style = MaterialTheme.typography.headlineLarge,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Primary save/unlock action
            Button(
                onClick = { 
                    dashboardViewModel.resetPaymentState()
                    isSheetOpen = true 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("overlay_save_now_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SaveLockPrimary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Payment, contentDescription = null)
                    Text(
                        text = "Save Now & Unlock",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Secondary unlock link
            TextButton(
                onClick = onNavigateToRecoveryEntry,
                modifier = Modifier
                    .testTag("overlay_recovery_link")
                    .height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = SaveLockAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Enter emergency recovery code",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SaveLockAmber
                    )
                }
            }

            // In full lockdown the home screen is blocked, so offer direct Call / Messages access here.
            // (Emergency calling must always be reachable.)
            if (uiState.fullLockdown) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenDialer,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("overlay_call_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Call")
                    }
                    OutlinedButton(
                        onClick = onOpenMessages,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("overlay_messages_button")
                    ) {
                        Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Messages")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Integrated Payment Sheet presentation for seamless pay-from-lock experience
        if (isSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { 
                    isSheetOpen = false
                    dashboardViewModel.resetPaymentState()
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                PaymentSheetContent(
                    amount = dashboardUiState.chargeAmount,
                    phoneNumber = dashboardUiState.mpesaNumber,
                    paymentStatus = dashboardUiState.paymentStatus,
                    phoneError = dashboardUiState.paymentPhoneError,
                    onPhoneChange = { dashboardViewModel.updateMpesaNumber(it) },
                    onSendRequest = { dashboardViewModel.triggerPayment() },
                    onDismiss = { 
                        isSheetOpen = false
                        dashboardViewModel.resetPaymentState()
                    }
                )
            }
        }
    }
}
