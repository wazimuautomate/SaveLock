package com.example.data.local.entity

import com.squareup.moshi.JsonClass

/**
 * One app the user may choose to restrict. Stored inside [SavingsConfigEntity] as a JSON list
 * (see [com.example.data.local.Converters]). [isRestricted] = the user ticked it as a distraction app.
 *
 * SAFETY: only packages in this list with isRestricted == true are ever soft-locked. Emergency
 * apps (dialer/messages/settings/launcher) are hard-excluded in the AccessibilityService and can
 * never appear here in a way that blocks them.
 */
@JsonClass(generateAdapter = true)
data class DistractionAppRecord(
    val packageName: String,
    val name: String,
    val isRestricted: Boolean
)

/**
 * The default distraction-app list seeded on first launch. Mirrors the list the UI used to hard-code,
 * so the Settings screen looks identical on first open. The user can toggle these later.
 */
fun defaultDistractionApps(): List<DistractionAppRecord> = listOf(
    DistractionAppRecord("com.android.chrome", "Google Chrome", true),
    DistractionAppRecord("com.facebook.katana", "Facebook", true),
    DistractionAppRecord("com.instagram.android", "Instagram", false),
    DistractionAppRecord("com.twitter.android", "X (Twitter)", true),
    DistractionAppRecord("com.zhiliaoapp.musically", "TikTok", false),
    DistractionAppRecord("com.youtube.android", "YouTube", true),
    DistractionAppRecord("com.netflix.mediaclient", "Netflix", false),
    DistractionAppRecord("com.reddit.frontpage", "Reddit", false)
)
