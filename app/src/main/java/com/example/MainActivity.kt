package com.example

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.Screen
import com.example.ui.navigation.bottomNavItems
import com.example.ui.screens.*
import com.example.ui.theme.SaveLockTheme
import com.example.util.NotificationManagerHelper
import com.example.viewmodel.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Notification Channels (Savings Reminders and Lock Alerts)
        NotificationManagerHelper.init(applicationContext)
        
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = SaveLockViewModels.Factory)
            val settingsState by settingsViewModel.uiState.collectAsState()

            // Resolve dynamic app theme (Light / Dark / System default)
            val isDarkTheme = when (settingsState.themeMode) {
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            SaveLockTheme(darkTheme = isDarkTheme) {
                val context = LocalContext.current

                // Runtime permission request flow for notifications (on Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted) {
                            Log.d("MainActivity", "Notification permission granted")
                        } else {
                            Log.d("MainActivity", "Notification permission denied")
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                // Main App Content
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Instantiate rest of the ViewModels (all share the same repository-backed data,
                // so the saving-enabled toggle stays in sync automatically — no manual bridging).
                val dashboardViewModel: DashboardViewModel = viewModel(factory = SaveLockViewModels.Factory)
                val historyViewModel: HistoryViewModel = viewModel(factory = SaveLockViewModels.Factory)
                val recoveryViewModel: RecoveryViewModel = viewModel(factory = SaveLockViewModels.Factory)
                val lockOverlayViewModel: LockOverlayViewModel = viewModel(factory = SaveLockViewModels.Factory)

                // Bottom bar visibility control
                val showBottomBar = currentRoute in listOf(
                    Screen.Dashboard.route,
                    Screen.History.route,
                    Screen.Settings.route
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                bottomNavItems.forEach { item ->
                                    val isSelected = currentRoute == item.screen.route
                                    NavigationBarItem(
                                        icon = { Icon(item.icon, contentDescription = item.label) },
                                        label = { Text(item.label) },
                                        selected = isSelected,
                                        onClick = {
                                            if (currentRoute != item.screen.route) {
                                                navController.navigate(item.screen.route) {
                                                    popUpTo(Screen.Dashboard.route) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Dashboard.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        // 1. Dashboard Screen
                        composable(Screen.Dashboard.route) {
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                onNavigateToRecoveryCode = {
                                    navController.navigate(Screen.RecoveryCodeEntry.route)
                                }
                            )
                        }

                        // 2. History Screen
                        composable(Screen.History.route) {
                            HistoryScreen(
                                viewModel = historyViewModel
                            )
                        }

                        // 3. Settings Screen
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateToRecoveryCodes = {
                                    navController.navigate(Screen.RecoveryCodes.route)
                                },
                                onGenerateNewCodes = {
                                    // Regenerate a fresh batch, then open the reveal screen to show them once.
                                    recoveryViewModel.generateNewCodes()
                                    navController.navigate(Screen.RecoveryCodes.route)
                                },
                                onSimulateLockOverlay = {
                                    navController.navigate(Screen.LockOverlay.route)
                                }
                            )
                        }

                        // 4. Recovery Codes List Screen (Child of Settings)
                        composable(Screen.RecoveryCodes.route) {
                            RecoveryCodesScreen(
                                viewModel = recoveryViewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // 5. Recovery Code Entry Screen (Standalone Lockout Overrider)
                        composable(Screen.RecoveryCodeEntry.route) {
                            RecoveryCodeEntryScreen(
                                viewModel = recoveryViewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onResetSuccess = {
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        // 6. Lockout Overlay Screen (Simulated lockout view)
                        composable(Screen.LockOverlay.route) {
                            OverlayScreen(
                                viewModel = lockOverlayViewModel,
                                dashboardViewModel = dashboardViewModel,
                                onNavigateToRecoveryEntry = {
                                    navController.navigate(Screen.RecoveryCodeEntry.route)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
