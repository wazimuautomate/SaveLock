package com.example.data.remote.dto

import com.squareup.moshi.JsonClass

/** Body for POST /stk-push. [accountReference] is "save" or "goal" (M-Pesa till reference). */
@JsonClass(generateAdapter = true)
data class StkPushRequest(
    val phone: String,
    val amount: Int,
    val accountReference: String = "save"
)

/** Response from POST /stk-push. */
@JsonClass(generateAdapter = true)
data class StkPushResponse(
    val checkoutRequestId: String,
    val merchantRequestId: String? = null,
    val message: String? = null
)

/** Response from GET /stk-status/<id>. status = PENDING | SUCCESS | FAILED | NOT_FOUND. */
@JsonClass(generateAdapter = true)
data class StkStatusResponse(
    val checkoutRequestId: String,
    val status: String,
    val amount: Int? = null,
    val resultDesc: String? = null
)

/** Response from GET /till-payments — direct Till/Paybill (C2B) payments the backend has recorded. */
@JsonClass(generateAdapter = true)
data class TillPaymentsResponse(
    val payments: List<TillPaymentDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TillPaymentDto(
    val transId: String,
    val amount: Double? = null,
    val msisdn: String? = null,
    val billRef: String? = null,
    val createdAt: String? = null
)
