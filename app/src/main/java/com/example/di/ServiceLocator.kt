package com.example.di

import android.content.Context
import com.example.BuildConfig
import com.example.data.local.SaveLockDatabase
import com.example.data.remote.PaymentApi
import com.example.data.remote.PaymentRepository
import com.example.data.repository.SaveLockRepository
import com.example.domain.LockStateManager
import com.example.domain.RecoveryCodeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Ultra-light manual dependency container. We avoid Hilt/Dagger to keep the app small and the build
 * fast. Call [init] once from [com.example.SaveLockApplication] before anything else uses it.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    /** Public application context for non-Android components (notifications, alarms). */
    val applicationContext: Context get() = appContext

    /** Long-lived scope for app-wide reactive state (e.g. LockStateManager collectors). */
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** True once the owner has filled in app/savelock.properties with a real backend URL + key. */
    val isBackendConfigured: Boolean by lazy {
        val url = BuildConfig.SUPABASE_FUNCTIONS_URL
        url.isNotBlank() &&
            !url.contains("YOUR-PROJECT-REF") &&
            BuildConfig.APP_BACKEND_KEY.isNotBlank() &&
            !BuildConfig.APP_BACKEND_KEY.contains("paste")
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Generous timeouts so weak 2G/3G still completes.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val paymentApi: PaymentApi? by lazy {
        if (!isBackendConfigured) return@lazy null
        val base = BuildConfig.SUPABASE_FUNCTIONS_URL.trimEnd('/') + "/"
        Retrofit.Builder()
            .baseUrl(base)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(PaymentApi::class.java)
    }

    /** Null until the backend is configured — the ViewModel falls back to a demo flow in that case. */
    val paymentRepository: PaymentRepository? by lazy {
        paymentApi?.let { PaymentRepository(it, BuildConfig.APP_BACKEND_KEY) }
    }

    val database: SaveLockDatabase by lazy { SaveLockDatabase.get(appContext) }

    val recoveryCodeManager: RecoveryCodeManager by lazy { RecoveryCodeManager() }

    val repository: SaveLockRepository by lazy {
        SaveLockRepository(
            configDao = database.configDao(),
            logDao = database.logDao(),
            recoveryDao = database.recoveryDao(),
            recoveryCodeManager = recoveryCodeManager
        )
    }

    val lockStateManager: LockStateManager by lazy {
        LockStateManager(repository, appScope)
    }
}
