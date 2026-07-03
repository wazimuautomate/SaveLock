package com.example.data.remote

import com.example.data.remote.dto.StkPushRequest
import com.example.data.remote.dto.StkPushResponse
import com.example.data.remote.dto.StkStatusResponse
import com.example.data.remote.dto.TillPaymentsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit interface for the Supabase backend. The app never talks to Daraja directly. */
interface PaymentApi {

    @POST("stk-push")
    suspend fun stkPush(
        @Header("x-app-key") appKey: String,
        @Body body: StkPushRequest
    ): StkPushResponse

    @GET("stk-status/{id}")
    suspend fun stkStatus(
        @Header("x-app-key") appKey: String,
        @Path("id") checkoutRequestId: String
    ): StkStatusResponse

    /** Recent direct Till/Paybill (C2B) payments, so the app can reconcile + unlock when back online. */
    @GET("till-payments")
    suspend fun tillPayments(
        @Header("x-app-key") appKey: String,
        @Query("sinceMinutes") sinceMinutes: Int
    ): TillPaymentsResponse
}
