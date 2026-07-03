package com.example.util

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager

/**
 * Lock-screen connectivity helpers: is there internet, is WiFi/mobile-data on, and how to open the
 * system panels to turn them on. Apps can't toggle radios directly on modern Android, so we open the
 * quick panels (a small sheet) — briefly sanctioned via [com.example.service.LockInteraction].
 */
object Connectivity {

    /** True if the phone currently has validated internet (so an online M-Pesa payment can go through). */
    fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isWifiOn(context: Context): Boolean =
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true

    fun isMobileDataOn(context: Context): Boolean = try {
        val tm = context.getSystemService(TelephonyManager::class.java)
        tm?.isDataEnabled == true
    } catch (e: Exception) {
        // Some OEMs/permissions throw — fall back to the global setting, else assume off (show the button).
        runCatching { Settings.Global.getInt(context.contentResolver, "mobile_data", 0) == 1 }.getOrDefault(false)
    }

    /** Open the WiFi quick-panel (Android 10+) or WiFi settings. */
    fun wifiPanelIntent(): Intent {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI
        else Settings.ACTION_WIFI_SETTINGS
        return Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Open the internet panel (WiFi + mobile data, Android 11+) or mobile-data/roaming settings. */
    fun mobileDataPanelIntent(): Intent {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        else Settings.ACTION_DATA_ROAMING_SETTINGS
        return Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
