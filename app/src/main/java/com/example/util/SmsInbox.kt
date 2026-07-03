package com.example.util

import android.content.Context
import android.provider.Telephony
import com.example.domain.MpesaPaymentSms

/**
 * Reads the SMS inbox to verify a pasted M-Pesa code — so "paste the code to confirm" can't be gamed
 * with a made-up code: the code must correspond to a REAL M-Pesa payment message already in the inbox.
 * Requires READ_SMS (granted from Settings). Returns null if the permission is missing or no match.
 */
object SmsInbox {

    /** Find a genuine M-Pesa payment SMS whose receipt equals [code] (scans recent inbox messages). */
    fun findMpesaByCode(context: Context, code: String): MpesaPaymentSms.Parsed? {
        val wanted = code.trim()
        if (wanted.isEmpty()) return null
        return try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
                null, null,
                "${Telephony.Sms.DATE} DESC",
            ) ?: return null
            cursor.use {
                val addrIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                var scanned = 0
                while (it.moveToNext() && scanned < 100) {
                    scanned++
                    val addr = if (addrIdx >= 0) it.getString(addrIdx) else null
                    if (!MpesaPaymentSms.isMpesaSender(addr)) continue
                    val body = (if (bodyIdx >= 0) it.getString(bodyIdx) else null) ?: continue
                    val parsed = MpesaPaymentSms.parse(body) ?: continue
                    if (parsed.receipt.equals(wanted, ignoreCase = true)) return parsed
                }
            }
            null
        } catch (e: SecurityException) {
            null // READ_SMS not granted
        } catch (e: Exception) {
            null
        }
    }
}
