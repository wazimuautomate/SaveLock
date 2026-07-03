package com.example.util

/**
 * Normalises Kenyan M-Pesa numbers to Daraja's required `2547XXXXXXXX` / `2541XXXXXXXX` form.
 * Accepts the common ways people type them: 07.., 01.., +2547.., 2547.., or 7.. / 1.. with spaces.
 */
object PhoneUtils {

    /** Returns a normalised `2547XXXXXXXX`/`2541XXXXXXXX` number, or null if it isn't a valid one. */
    fun normalize(raw: String): String? {
        // Keep digits only (drop spaces, dashes, a leading +, etc.).
        var d = raw.filter { it.isDigit() }

        d = when {
            // 07XXXXXXXX / 01XXXXXXXX  -> drop the leading 0
            d.length == 10 && d.startsWith("0") -> "254" + d.substring(1)
            // 7XXXXXXXX / 1XXXXXXXX     -> add 254
            d.length == 9 && (d.startsWith("7") || d.startsWith("1")) -> "254$d"
            // already 2547XXXXXXXX / 2541XXXXXXXX
            d.length == 12 && d.startsWith("254") -> d
            else -> d
        }

        return if (Regex("^254[71]\\d{8}$").matches(d)) d else null
    }

    /** True if [raw] can be normalised to a valid Safaricom/Airtel M-Pesa number. */
    fun isValid(raw: String): Boolean = normalize(raw) != null
}
