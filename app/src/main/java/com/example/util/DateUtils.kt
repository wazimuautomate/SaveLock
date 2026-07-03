package com.example.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Small date/time helpers. Uses java.time (available on minSdk 30). */
object DateUtils {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DISPLAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)

    /** Today's local date as "yyyy-MM-dd". */
    fun today(): String = LocalDate.now().format(ISO)

    /** Convert "yyyy-MM-dd" to a friendly "Jul 03, 2026". Falls back to the input if unparseable. */
    fun isoToDisplay(iso: String): String =
        runCatching { LocalDate.parse(iso, ISO).format(DISPLAY) }.getOrDefault(iso)

    fun parse(iso: String): LocalDate = LocalDate.parse(iso, ISO)

    private val DATETIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM dd, yyyy • HH:mm", Locale.ENGLISH)

    /** Epoch millis -> "Jul 03, 2026 • 14:05" in local time. */
    fun epochToDisplay(millis: Long): String = runCatching {
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATETIME)
    }.getOrDefault("")

    private fun hourMinute(lockTime: String): Pair<Int, Int> {
        val parts = lockTime.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 20) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    /** True if the current local time is at or after today's [lockTime] ("HH:mm"). */
    fun isPastLockTime(lockTime: String): Boolean = runCatching {
        val (h, m) = hourMinute(lockTime)
        !LocalTime.now().isBefore(LocalTime.of(h, m))
    }.getOrDefault(false)

    /** Epoch millis of the next occurrence of [lockTime] (today if still ahead, else tomorrow). */
    fun nextLockOccurrenceMillis(lockTime: String): Long {
        val (h, m) = hourMinute(lockTime)
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(h, m)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** Epoch millis of the next occurrence of ([lockTime] minus [leadHours]); tomorrow if passed. */
    fun nextReminderMillis(lockTime: String, leadHours: Int): Long {
        val (h, m) = hourMinute(lockTime)
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(h, m).minusHours(leadHours.toLong())
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
