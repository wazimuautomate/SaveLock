package com.example.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PaymentSheetContent
import com.example.ui.theme.SaveLockAmber
import com.example.ui.theme.SaveLockPrimary
import com.example.ui.theme.SaveLockPrimaryDark
import com.example.ui.theme.SaveLockRed
import com.example.viewmodel.DashboardViewModel
import com.example.viewmodel.PaymentStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToRecoveryCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var isSheetOpen by remember { mutableStateOf(false) }

    // Synchronize bottom sheet open state with Payment Status
    LaunchedEffect(uiState.paymentStatus) {
        if (uiState.paymentStatus != PaymentStatus.Idle) {
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
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header Bar
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
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "SaveLock",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Streak Indicator Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SaveLockAmber.copy(alpha = 0.15f))
                        .border(1.dp, SaveLockAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Streak",
                            tint = SaveLockAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${uiState.streakDays} Day Streak",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = SaveLockAmber
                        )
                    }
                }
            }

            // Total Saved Premium Banner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(SaveLockPrimary, SaveLockPrimaryDark)
                            )
                        )
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL SAVED BALANCE",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Secured",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = uiState.totalSaved,
                            style = MaterialTheme.typography.headlineLarge,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Divider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Funds secured offline in locked escrow",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // Today's Target Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Today's Saving Target",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Status Pill
                        val (pillBg, pillBorder, pillText, pillLabel, pillIcon) = if (uiState.isSavedToday) {
                            listOf(
                                SaveLockPrimary.copy(alpha = 0.15f),
                                SaveLockPrimary.copy(alpha = 0.4f),
                                SaveLockPrimary,
                                "Saved Today",
                                Icons.Default.CheckCircle
                            )
                        } else {
                            listOf(
                                SaveLockAmber.copy(alpha = 0.15f),
                                SaveLockAmber.copy(alpha = 0.4f),
                                SaveLockAmber,
                                "Not Saved Yet",
                                Icons.Default.Pending
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(pillBg as Color)
                                .border(1.dp, pillBorder as Color, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = pillIcon as ImageVector,
                                    contentDescription = null,
                                    tint = pillText as Color,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = pillLabel as String,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = pillText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = uiState.todaysTarget,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Countdown Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (uiState.isSavedToday) MaterialTheme.colorScheme.surfaceVariant
                                else SaveLockRed.copy(alpha = 0.1f)
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isSavedToday) Icons.Default.AccessTime else Icons.Default.HourglassBottom,
                                contentDescription = null,
                                tint = if (uiState.isSavedToday) MaterialTheme.colorScheme.onSurfaceVariant else SaveLockRed
                            )
                            Column {
                                Text(
                                    text = if (uiState.isSavedToday) "Lock Disarmed" else "Discipline Clock is Ticking",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.isSavedToday) MaterialTheme.colorScheme.onSurface else SaveLockRed
                                )
                                Text(
                                    text = if (uiState.isSavedToday) "Your distraction apps will not lock today." else "System locks in ${uiState.timeUntilLock} if unpaid.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Big Save Now Button
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { 
                    viewModel.resetPaymentState()
                    isSheetOpen = true 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("save_now_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isSavedToday) MaterialTheme.colorScheme.secondary else SaveLockPrimary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isSavedToday) Icons.Default.DoneAll else Icons.Default.Payment,
                        contentDescription = null
                    )
                    Text(
                        text = if (uiState.isSavedToday) "Save Again Today" else "Save Now & Disarm Lock",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Toggle Master Switch
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (uiState.isSavingEnabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (uiState.isSavingEnabled) SaveLockPrimary else SaveLockRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Saving Enabled",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Master status for locks and triggers",
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
                            checkedTrackColor = SaveLockPrimary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp)) // Extra spacing for bottom bar offset
        }

        // Confirmation Dialog for Disabling Saving
        if (uiState.showDisablingConfirmation) {
            AlertDialog(
                onDismissRequest = { viewModel.confirmDisableSaving(false) },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = SaveLockRed) },
                title = { Text("Disable Saving Discipline?") },
                text = {
                    Text("This disables today's lock triggers, active screen lockouts, and daily reminder messages. Doing so breaks your streak. Are you sure you want to continue?")
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmDisableSaving(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = SaveLockRed)
                    ) {
                        Text("Disable")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.confirmDisableSaving(false) }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // M-Pesa Payment Bottom Sheet Sheet representation
        if (isSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { 
                    isSheetOpen = false
                    viewModel.resetPaymentState()
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                PaymentSheetContent(
                    amount = uiState.chargeAmount,
                    phoneNumber = uiState.mpesaNumber,
                    paymentStatus = uiState.paymentStatus,
                    phoneError = uiState.paymentPhoneError,
                    onPhoneChange = { viewModel.updateMpesaNumber(it) },
                    onSendRequest = { viewModel.triggerPayment() },
                    onDismiss = { 
                        isSheetOpen = false
                        viewModel.resetPaymentState()
                    }
                )
            }
        }
    }
}
