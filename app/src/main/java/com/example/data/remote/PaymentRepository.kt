package com.example.data.remote

import android.util.Log
import com.example.data.remote.dto.StkPushRequest
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Drives one payment: start the STK push, then poll the status until success/fail/timeout.
 *
 * LOW-INTERNET HANDLING: the initial push is retried with backoff so a weak signal that recovers
 * within the window still goes through ("queue + auto-retry"). Polling tolerates dropped requests.
 * (A true fully-offline M-Pesa payment is impossible — for that the user uses a recovery code.)
 */
class PaymentRepository(
    private val api: PaymentApi,
    private val appKey: String
) {
    sealed interface PayResult {
        data class Success(val amount: Int) : PayResult
        data class Failed(val message: String) : PayResult
        object Timeout : PayResult
    }

    /**
     * @param accountReference "save" or "goal" — becomes the M-Pesa till account reference.
     * @param onStkSent invoked once the prompt has been sent (move UI to "waiting for PIN").
     */
    suspend fun pay(phone: String, amount: Int, accountReference: String, onStkSent: () -> Unit): PayResult {
        val push = try {
            withIoRetry { api.stkPush(appKey, StkPushRequest(phone, amount, accountReference)) }
        } catch (e: Exception) {
            Log.w("PaymentRepo", "STK push failed after retries: ${e.message}")
            return PayResult.Timeout
        }

        onStkSent()

        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val status = try {
                api.stkStatus(appKey, push.checkoutRequestId)
            } catch (e: Exception) {
                null // transient network drop — keep polling
            }
            when (status?.status) {
                "SUCCESS" -> return PayResult.Success(status.amount ?: amount)
                "FAILED" -> return PayResult.Failed(status.resultDesc ?: "Transaction was not completed")
                else -> { /* PENDING / NOT_FOUND / null -> keep waiting */ }
            }
        }
        return PayResult.Timeout
    }

    private suspend fun <T> withIoRetry(block: suspend () -> T): T {
        var last: Exception? = null
        repeat(IO_RETRIES) { attempt ->
            try {
                return block()
            } catch (e: IOException) {
                last = e
                delay(RETRY_BACKOFF_MS * (attempt + 1))
            }
        }
        throw last ?: IOException("network unavailable")
    }

    companion object {
        private const val POLL_TIMEOUT_MS = 60_000L
        private const val POLL_INTERVAL_MS = 3_000L
        private const val IO_RETRIES = 3
        private const val RETRY_BACKOFF_MS = 2_000L
    }
}
