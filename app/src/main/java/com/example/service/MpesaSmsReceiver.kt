package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.di.ServiceLocator
import com.example.domain.MpesaPaymentSms
import com.example.util.NotificationManagerHelper
import kotlinx.coroutines.launch

/**
 * OFFLINE auto-unlock. Listens for Safaricom's M-Pesa confirmation SMS. When the user pays their own
 * till directly from the M-Pesa menu (works on GSM with NO mobile data), Safaricom texts the receipt;
 * we read it, verify it's a payment to the user's configured till, and credit the due plans locally —
 * so the phone unlocks with zero internet.
 *
 * SAFETY: only reacts to messages whose sender is "MPESA" AND whose payee matches the user's own till
 * name, and only when the owner has explicitly enabled SMS auto-unlock. Every payment is deduped by
 * its M-Pesa code, so it can never double-credit (nor collide with the online C2B reconcile poller).
 */
class MpesaSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages.first().originatingAddress
        if (!MpesaPaymentSms.isMpesaSender(sender)) return
        // A long SMS is split into parts — join them into the full body before parsing.
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val parsed = MpesaPaymentSms.parse(body) ?: return

        // DB + repository work must outlive onReceive → keep the process alive briefly.
        val pending = goAsync()
        ServiceLocator.appScope.launch {
            try {
                val cfg = ServiceLocator.repository.getConfig()
                if (!cfg.smsAutoUnlockEnabled || cfg.tillName.isBlank()) return@launch
                if (!MpesaPaymentSms.matchesTill(parsed, cfg.tillName)) return@launch

                val credited = ServiceLocator.repository.applyExternalPayment(parsed.receipt, parsed.amount)
                if (credited) {
                    ServiceLocator.lockStateManager.refreshNow()
                    NotificationManagerHelper.clearLockActive(context)
                    NotificationManagerHelper.showPaymentResult(context, success = true, amount = parsed.amount)
                    Log.i("MpesaSmsReceiver", "Auto-unlocked from M-Pesa SMS (${parsed.receipt}, KES ${parsed.amount}).")
                }
            } catch (e: Exception) {
                Log.w("MpesaSmsReceiver", "Failed to process M-Pesa SMS: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
