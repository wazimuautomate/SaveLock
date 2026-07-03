package com.example.domain

/**
 * Parses Safaricom's M-Pesa confirmation SMS so the app can auto-unlock offline when the user pays
 * their till directly (M-Pesa menu / SIM Toolkit) — no internet needed on the phone, only GSM signal.
 *
 * Pure/String-only so it is unit-testable and carries no Android dependency. The receiver
 * ([com.example.service.MpesaSmsReceiver]) supplies the sender + body; this decides what it means.
 *
 * Typical Buy Goods (till) customer SMS:
 *   "SL74XXABCD Confirmed. Ksh500.00 paid to JOHN'S SHOP. on 3/7/26 at 2:30 PM. New M-PESA balance…"
 * We treat a message as a real payment to the user's own till only when the payee name matches the
 * till name they configured — so buying groceries elsewhere never unlocks the phone.
 */
object MpesaPaymentSms {

    data class Parsed(val receipt: String, val amount: Int, val payee: String)

    // Ksh1,500.00 / KSh 500 / Ksh50 — captures the number (commas + optional cents).
    private val AMOUNT = Regex("""ksh\s?([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    // The transaction code is the leading token, e.g. "SL74XXABCD Confirmed."
    private val RECEIPT = Regex("""^([A-Z0-9]{8,12})\b""")
    // "paid to NAME." (Buy Goods) or "sent to NAME for account…/ on…" (Paybill / send money).
    private val PAYEE = Regex(
        """(?:paid to|sent to)\s+(.+?)(?:\s+for account|\s+on\s|\.)""",
        RegexOption.IGNORE_CASE
    )

    /** M-Pesa messages come from the alphanumeric sender "MPESA". */
    fun isMpesaSender(sender: String?): Boolean =
        sender != null && sender.trim().equals("MPESA", ignoreCase = true)

    /** Extract (receipt, whole-KES amount, payee) from an M-Pesa payment SMS, or null if it isn't one. */
    fun parse(body: String?): Parsed? {
        val text = body?.trim() ?: return null
        val amount = AMOUNT.find(text)?.groupValues?.get(1)
            ?.replace(",", "")?.toDoubleOrNull()?.toInt() ?: return null
        if (amount <= 0) return null
        val receipt = RECEIPT.find(text)?.groupValues?.get(1) ?: return null
        val payee = PAYEE.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return Parsed(receipt, amount, payee)
    }

    /** True when the SMS payee is the user's own till (loose, case/punctuation-insensitive contains). */
    fun matchesTill(parsed: Parsed, tillName: String): Boolean {
        val till = normalize(tillName)
        if (till.isEmpty()) return false
        val payee = normalize(parsed.payee)
        return payee.contains(till) || till.contains(payee)
    }

    private fun normalize(s: String): String =
        s.lowercase().map { if (it.isLetterOrDigit() || it == ' ') it else ' ' }
            .joinToString("").replace(Regex("\\s+"), " ").trim()
}
