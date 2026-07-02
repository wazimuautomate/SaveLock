package com.example.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Small date helpers. Uses java.time (available on minSdk 30). */
object DateUtils {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DISPLAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)

    /** Today's local date as "yyyy-MM-dd". */
    fun today(): String = LocalDate.now().format(ISO)

    /** Convert "yyyy-MM-dd" to a friendly "Jul 03, 2026". Falls back to the input if unparseable. */
    fun isoToDisplay(iso: String): String =
        runCatching { LocalDate.parse(iso, ISO).format(DISPLAY) }.getOrDefault(iso)

    fun parse(iso: String): LocalDate = LocalDate.parse(iso, ISO)
}
