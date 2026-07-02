package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String) {
    object Dashboard : Screen("dashboard", "Dashboard")
    object History : Screen("history", "History")
    object Settings : Screen("settings", "Settings")
    object RecoveryCodes : Screen("recovery_codes", "Recovery Codes")
    object RecoveryCodeEntry : Screen("recovery_code_entry", "Unlock Screen")
    object LockOverlay : Screen("lock_overlay", "Lock Overlay")
}

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, Icons.Default.Home, "Home"),
    BottomNavItem(Screen.History, Icons.Default.History, "History"),
    BottomNavItem(Screen.Settings, Icons.Default.Settings, "Settings")
)
