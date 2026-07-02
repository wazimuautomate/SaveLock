package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SaveLockPrimary
import com.example.ui.theme.SaveLockRed
import com.example.viewmodel.RecoveryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryCodeEntryScreen(
    viewModel: RecoveryViewModel,
    onNavigateBack: () -> Unit,
    onResetSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Reset success triggers callback navigation automatically or via confirmation
    var showSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.codeValidationSuccess) {
        if (uiState.codeValidationSuccess) {
            showSuccessDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enter Recovery Code", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("entry_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = SaveLockPrimary,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Emergency Device Unlock",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter a valid, unused 9-character recovery code (e.g. SL2A-9X7B) to immediately release active app locks and reset the discipline clock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Big focused input field
            OutlinedTextField(
                value = uiState.enteredCode,
                onValueChange = { viewModel.updateEnteredCode(it) },
                placeholder = { Text("SLXX-XXXX", style = TextStyle(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)) },
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp
                ),
                singleLine = true,
                isError = uiState.codeValidationError != null,
                supportingText = {
                    uiState.codeValidationError?.let {
                        Text(
                            text = it,
                            color = SaveLockRed,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { viewModel.submitRecoveryCode() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .testTag("recovery_code_text_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SaveLockPrimary,
                    errorBorderColor = SaveLockRed
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.submitRecoveryCode() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("submit_code_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SaveLockPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Submit Unlock Code",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        // Success Confirmation Dialog
        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = {
                    showSuccessDialog = false
                    viewModel.resetValidationState()
                    onResetSuccess()
                },
                icon = { Icon(Icons.Default.LockOpen, contentDescription = null, tint = SaveLockPrimary, modifier = Modifier.size(36.dp)) },
                title = { Text("Device Unlocked Successfully!") },
                text = {
                    Text("The recovery code has been accepted. Active lockouts have been reset and all restricted distraction apps are fully accessible until tomorrow's deadline.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSuccessDialog = false
                            viewModel.resetValidationState()
                            onResetSuccess()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SaveLockPrimary)
                    ) {
                        Text("Awesome")
                    }
                }
            )
        }
    }
}
