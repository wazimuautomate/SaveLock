package com.example.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * A thin, invisible overlay pinned across the top of the screen. Because it sits ON TOP of the
 * status bar and consumes touches there, it stops the notification shade from being pulled down
 * while a full lockdown is active. Requires the "Display over other apps" permission.
 *
 * NOTE: this is best-effort hardening for a sideloaded app. A 100% kiosk (fully disabling
 * home/recents) is only possible with Device Owner provisioning (factory reset + a computer),
 * which is intentionally out of scope. The accessibility redirect covers home/recents/other apps.
 */
object ShadeGuard {

    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null

    fun show(context: Context) {
        main.post {
            if (view != null) return@post
            if (!Settings.canDrawOverlays(context)) return@post
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            val strip = View(context) // fully transparent, just intercepts touches
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                statusBarHeight(context),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP }
            runCatching {
                wm.addView(strip, params)
                view = strip
            }
        }
    }

    fun hide(context: Context) {
        main.post {
            val v = view ?: return@post
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            runCatching { wm?.removeView(v) }
            view = null
        }
    }

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id)
        else (28 * context.resources.displayMetrics.density).toInt()
    }
}
