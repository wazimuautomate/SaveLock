package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AmountType
import com.example.data.local.entity.PeriodType
import com.example.data.local.entity.PlanType
import com.example.data.local.entity.SavingsPlanEntity
import com.example.data.remote.PaymentRepository
import com.example.data.repository.SaveLockRepository
import com.example.di.ServiceLocator
import com.example.domain.PlanLogic
import com.example.scheduling.AlarmScheduler
import com.example.util.NotificationManagerHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PaymentStatus {
    object Idle : PaymentStatus
    object Requesting : PaymentStatus
    object WaitingForSTK : PaymentStatus
    object Success : PaymentStatus
    data class Failed(val error: String) : PaymentStatus
    object Timeout : PaymentStatus
}

/** One row on the Home screen: a Savings or Goal with its live progress + what to pay now. */
data class PlanRow(
    val id: Long,
    val name: String,
    val typeLabel: String,       // "Savings" or "Goal"
    val isGoal: Boolean,
    val detailLabel: String,     // e.g. "KES 500 daily" / "KES 500 min every 3 days"
    val progress: Float,         // 0..1 for the progress bar
    val progressLabel: String,   // e.g. "KES 200 / 500 this period" or "KES 1,200 / 5,000 saved"
    val statusLabel: String,     // "Pay now to unlock" / "Paid this period" / "Goal complete"
    val isLocking: Boolean,
    val isComplete: Boolean,
    val payAmount: Int           // charge for "Save now" (period shortfall, or the full amount)
)

data class DashboardUiState(
    val totalSaved: String = "KES 0",
    val isSavingEnabled: Boolean = true,
    val plans: List<PlanRow> = emptyList(),
    val hasPlans: Boolean = false,
    val activeLocks: Int = 0,
    val showDisablingConfirmation: Boolean = false,

    // Payment sheet state (targets one plan at a time)
    val payTargetName: String? = null,
    val mpesaNumber: String = "",
    val chargeAmount: String = "KES 0",
    val paymentStatus: PaymentStatus = PaymentStatus.Idle,
    val paymentPhoneError: String? = null
)

/**
 * Home + payment, backed by Room. The plan list, totals and lock status come from the repository;
 * the payment sheet and the disable-confirmation dialog are transient UI state.
 *
 * Payment uses the real Supabase backend when configured (app/savelock.properties filled in);
 * otherwise it falls back to a local demo flow so the app is usable before backend setup. Either
 * way a success records a real plan payment so progress bars and the lock update end-to-end.
 */
class DashboardViewModel(
    private val repository: SaveLockRepository,
    private val paymentRepository: PaymentRepository? = null
) : ViewModel() {

    private data class PayState(
        val paymentStatus: PaymentStatus = PaymentStatus.Idle,
        val targetPlanId: Long? = null,
        val phoneText: String? = null,           // in-progress edit; null => persisted mpesa number
        val phoneError: String? = null,
        val showDisablingConfirmation: Boolean = false
    )

    private val payState = MutableStateFlow(PayState())

    // Emits every minute so period progress / lock status stays fresh even with no interaction.
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val uiState: StateFlow<DashboardUiState> =
        combine(
            repository.config,
            repository.activePlans,
            repository.allPayments,
            ticker,
            payState
        ) { cfg, plans, payments, _, pay ->
            val now = System.currentTimeMillis()
            val rows = plans.map { plan -> buildRow(plan, payments, now) }
            val total = payments.filter { !it.viaRecovery }.sumOf { it.amount }
            val target = pay.targetPlanId?.let { id -> rows.firstOrNull { it.id == id } }
            DashboardUiState(
                totalSaved = "KES %,d".format(total),
                isSavingEnabled = cfg.savingEnabled,
                plans = rows,
                hasPlans = rows.isNotEmpty(),
                activeLocks = rows.count { it.isLocking },
                showDisablingConfirmation = pay.showDisablingConfirmation,
                payTargetName = target?.name,
                mpesaNumber = pay.phoneText ?: cfg.mpesaNumber,
                chargeAmount = "KES %,d".format(target?.payAmount ?: 0),
                paymentStatus = pay.paymentStatus,
                paymentPhoneError = pay.phoneError
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private fun buildRow(
        plan: SavingsPlanEntity,
        payments: List<com.example.data.local.entity.PlanPaymentEntity>,
        now: Long
    ): PlanRow {
        val required = PlanLogic.requiredAmount(plan)
        val paidThisPeriod = PlanLogic.currentPeriodPaid(plan, payments, now)
        val locking = PlanLogic.isLockingNow(plan, payments, now)
        val complete = PlanLogic.isGoalCompleted(plan, payments, now)
        val totalForPlan = payments.filter { it.planId == plan.id }.sumOf { it.amount }
        val shortfall = required - paidThisPeriod
        val payAmount = if (shortfall > 0) shortfall else required

        val periodText = periodLabel(plan)
        val minText = if (plan.amountType == AmountType.FLEXIBLE) " min" else ""
        val detail = "KES %,d%s %s".format(required, minText, periodText)

        val progressLabel = if (plan.type == PlanType.GOAL) {
            "KES %,d / %,d saved".format(totalForPlan, plan.goalTotal)
        } else {
            "KES %,d / %,d this period".format(paidThisPeriod.coerceAtMost(required), required)
        }

        val status = when {
            complete -> "Goal complete 🎉"
            locking -> "Pay now to unlock"
            else -> "Paid — up to date"
        }

        return PlanRow(
            id = plan.id,
            name = plan.name,
            typeLabel = if (plan.type == PlanType.GOAL) "Goal" else "Savings",
            isGoal = plan.type == PlanType.GOAL,
            detailLabel = detail,
            progress = PlanLogic.progressFraction(plan, payments, now),
            progressLabel = progressLabel,
            statusLabel = status,
            isLocking = locking,
            isComplete = complete,
            payAmount = payAmount
        )
    }

    private fun periodLabel(plan: SavingsPlanEntity): String = when (plan.period) {
        PeriodType.DAILY -> "daily"
        PeriodType.EVERY_2_DAYS -> "every 2 days"
        PeriodType.WEEKLY -> "weekly"
        PeriodType.MONTHLY -> "monthly"
        PeriodType.EVERY_N_DAYS -> "every ${plan.periodValue} days"
        PeriodType.EVERY_N_HOURS -> "every ${plan.periodValue} hours"
    }

    // ---- Plan management -------------------------------------------------------------------------

    /** Create a plan. [createdAt] anchors the period math to "now". */
    fun createPlan(
        type: PlanType,
        name: String,
        amountType: AmountType,
        amount: Int,
        period: PeriodType,
        periodValue: Int,
        goalTotal: Int,
        goalDurationDays: Int
    ) {
        viewModelScope.launch {
            repository.createPlan(
                SavingsPlanEntity(
                    type = type,
                    name = name.trim().ifBlank { if (type == PlanType.GOAL) "My goal" else "My savings" },
                    amountType = amountType,
                    amount = amount,
                    period = period,
                    periodValue = periodValue,
                    goalTotal = goalTotal,
                    goalDurationDays = goalDurationDays,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deletePlan(id: Long) {
        viewModelScope.launch { repository.deletePlan(id) }
    }

    // ---- Master switch ---------------------------------------------------------------------------

    fun toggleSavingEnabled(enabled: Boolean) {
        if (!enabled) {
            payState.update { it.copy(showDisablingConfirmation = true) }
        } else {
            viewModelScope.launch { repository.setSavingEnabled(true) }
        }
    }

    fun confirmDisableSaving(confirm: Boolean) {
        payState.update { it.copy(showDisablingConfirmation = false) }
        if (confirm) viewModelScope.launch { repository.setSavingEnabled(false) }
    }

    // ---- Payment ---------------------------------------------------------------------------------

    /** Open the payment sheet aimed at a specific plan. */
    fun openPaymentForPlan(planId: Long) {
        payState.update { it.copy(targetPlanId = planId, paymentStatus = PaymentStatus.Idle) }
    }

    fun updateMpesaNumber(number: String) {
        val valid = validateMpesa(number)
        // Pay-FROM number for THIS payment only; does not change the saved primary number.
        payState.update {
            it.copy(phoneText = number, phoneError = if (valid) null else "Format must be 2547XXXXXXXX")
        }
    }

    fun triggerPayment() {
        viewModelScope.launch {
            val planId = payState.value.targetPlanId ?: return@launch
            val cfg = repository.getConfig()
            val phone = payState.value.phoneText ?: cfg.mpesaNumber
            if (!validateMpesa(phone)) {
                payState.update { it.copy(phoneError = "Format must be 2547XXXXXXXX") }
                return@launch
            }
            payState.update { it.copy(phoneError = null) }

            val amount = repository.amountDueNow(planId)
            val backend = paymentRepository
            if (backend != null) {
                realPayment(backend, phone, amount, planId)
            } else {
                demoPayment(amount, planId)
            }
        }
    }

    private suspend fun realPayment(backend: PaymentRepository, phone: String, amount: Int, planId: Long) {
        payState.update { it.copy(paymentStatus = PaymentStatus.Requesting) }
        val result = backend.pay(phone, amount, onStkSent = {
            payState.update { it.copy(paymentStatus = PaymentStatus.WaitingForSTK) }
        })
        when (result) {
            is PaymentRepository.PayResult.Success -> onPaymentSucceeded(planId, result.amount)
            is PaymentRepository.PayResult.Failed ->
                payState.update { it.copy(paymentStatus = PaymentStatus.Failed(result.message)) }
            PaymentRepository.PayResult.Timeout ->
                payState.update { it.copy(paymentStatus = PaymentStatus.Timeout) }
        }
    }

    /** Local demo flow used only until the Supabase backend is configured. */
    private suspend fun demoPayment(amount: Int, planId: Long) {
        Log.d("DashboardVM", "Backend not configured — running demo payment flow")
        payState.update { it.copy(paymentStatus = PaymentStatus.Requesting) }
        delay(1500)
        payState.update { it.copy(paymentStatus = PaymentStatus.WaitingForSTK) }
        delay(2500)
        onPaymentSucceeded(planId, amount)
    }

    private suspend fun onPaymentSucceeded(planId: Long, amount: Int) {
        repository.recordPlanPayment(planId, amount, checkoutRequestId = null)
        val ctx = ServiceLocator.applicationContext
        NotificationManagerHelper.showPaymentResult(ctx, success = true, amount = amount)
        // Only clear the persistent lock notification if nothing else is still locking.
        if (!ServiceLocator.lockStateManager.recompute()) {
            NotificationManagerHelper.clearLockActive(ctx)
            AlarmScheduler.cancelReminders(ctx)
        }
        payState.update { it.copy(paymentStatus = PaymentStatus.Success) }
    }

    fun resetPaymentState() {
        payState.update { it.copy(paymentStatus = PaymentStatus.Idle) }
    }

    private fun validateMpesa(phone: String): Boolean = Regex("^2547\\d{8}$").matches(phone)
}
