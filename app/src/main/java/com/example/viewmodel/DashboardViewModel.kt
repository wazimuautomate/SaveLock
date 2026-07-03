package com.example.viewmodel

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
import com.example.util.PhoneUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface PaymentStatus {
    object Idle : PaymentStatus
    object Requesting : PaymentStatus
    object WaitingForSTK : PaymentStatus
    object Success : PaymentStatus
    data class Failed(val error: String) : PaymentStatus
    object Timeout : PaymentStatus
}

/** State of "paste your M-Pesa code to confirm" on the lock screen. */
sealed interface PasteStatus {
    object Idle : PasteStatus
    object Checking : PasteStatus
    object Success : PasteStatus
    data class Failed(val message: String) : PasteStatus
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
    val payAmount: Int,          // charge for "Save now" (period shortfall, or the full amount)
    val isFlexible: Boolean,     // true = user types the amount (>= minAmount)
    val minAmount: Int,          // per-period minimum (the amount the user set)
    // Goal-only stats, used by the rotating lock-screen copy (0 for savings plans).
    val goalDaysLeft: Int = 0,
    val goalAmountRemaining: Int = 0,
    val goalPercent: Int = 0
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
    val payIsFlexible: Boolean = false,      // flexible plan → the amount field is editable
    val payMinAmount: Int = 0,               // minimum for a flexible payment
    val payAmountText: String = "",          // what the user typed (flexible only)
    val paymentStatus: PaymentStatus = PaymentStatus.Idle,
    val paymentPhoneError: String? = null,
    val paymentAmountError: String? = null
)

/**
 * Home + payment, backed by Room. The plan list, totals and lock status come from the repository;
 * the payment sheet and the disable-confirmation dialog are transient UI state.
 *
 * Payment is REAL — it calls the Supabase backend (STK Push) when configured (app/savelock.properties
 * filled in, injected in CI from GitHub secrets). If the backend isn't configured, payment fails with
 * a clear message rather than pretending to succeed. A confirmed payment records a real plan payment.
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
        val amountText: String? = null,          // flexible-plan amount the user typed
        val amountError: String? = null,
        val showDisablingConfirmation: Boolean = false
    )

    private val payState = MutableStateFlow(PayState())

    private val _pasteStatus = MutableStateFlow<PasteStatus>(PasteStatus.Idle)
    /** "Paste M-Pesa code" flow on the lock screen (separate from the STK payment sheet). */
    val pasteStatus: StateFlow<PasteStatus> = _pasteStatus

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
                payIsFlexible = target?.isFlexible == true,
                payMinAmount = target?.minAmount ?: 0,
                payAmountText = pay.amountText ?: "",
                paymentStatus = pay.paymentStatus,
                paymentPhoneError = pay.phoneError,
                paymentAmountError = pay.amountError
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

        // Goal stats for the rotating lock copy. Days left counts whole days remaining until the
        // goal's duration is up; amount remaining is what's still needed to hit the target.
        val goalDaysLeft = if (plan.type == PlanType.GOAL && plan.goalDurationDays > 0) {
            val endMs = plan.createdAt + plan.goalDurationDays.toLong() * 86_400_000L
            val remainingMs = endMs - now
            if (remainingMs <= 0) 0 else kotlin.math.ceil(remainingMs / 86_400_000.0).toInt()
        } else 0
        val goalAmountRemaining =
            if (plan.type == PlanType.GOAL) (plan.goalTotal - totalForPlan).coerceAtLeast(0) else 0
        val goalPercent = (PlanLogic.progressFraction(plan, payments, now) * 100).roundToInt()

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
            payAmount = payAmount,
            isFlexible = plan.amountType == AmountType.FLEXIBLE,
            minAmount = required,
            goalDaysLeft = goalDaysLeft,
            goalAmountRemaining = goalAmountRemaining,
            goalPercent = goalPercent
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

    /** Load a plan for the edit form to prefill (null if it's gone). */
    suspend fun getPlan(id: Long): SavingsPlanEntity? = repository.getPlan(id)

    /** Save edits to an existing plan, keeping its id/createdAt anchor and payments. */
    fun updatePlan(
        id: Long,
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
            val existing = repository.getPlan(id) ?: return@launch
            repository.updatePlan(
                existing.copy(
                    type = type,
                    name = name.trim().ifBlank { existing.name },
                    amountType = amountType,
                    amount = amount,
                    period = period,
                    periodValue = periodValue,
                    goalTotal = goalTotal,
                    goalDurationDays = goalDurationDays
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

    /** Open the payment sheet aimed at a specific plan. Clears any prior amount entry. */
    fun openPaymentForPlan(planId: Long) {
        payState.update {
            it.copy(targetPlanId = planId, paymentStatus = PaymentStatus.Idle, amountText = null, amountError = null)
        }
    }

    fun updateMpesaNumber(number: String) {
        val valid = PhoneUtils.isValid(number)
        // Pay-FROM number for THIS payment only; does not change the saved primary number.
        payState.update {
            it.copy(phoneText = number, phoneError = if (valid) null else "Enter e.g. 0712345678")
        }
    }

    /** For a flexible plan: the amount the user types (digits only). Validated on submit. */
    fun updatePayAmount(text: String) {
        payState.update { it.copy(amountText = text.filter { c -> c.isDigit() }, amountError = null) }
    }

    fun triggerPayment() {
        viewModelScope.launch {
            val planId = payState.value.targetPlanId ?: return@launch
            val plan = repository.getPlan(planId) ?: return@launch
            val cfg = repository.getConfig()

            val raw = payState.value.phoneText ?: cfg.mpesaNumber
            val phone = PhoneUtils.normalize(raw)
            if (phone == null) {
                payState.update { it.copy(phoneError = "Enter e.g. 0712345678") }
                return@launch
            }
            payState.update { it.copy(phoneError = null) }

            // Fixed plan: charge the period shortfall. Flexible: the user types the amount (>= minimum).
            val amount: Int = if (plan.amountType == AmountType.FLEXIBLE) {
                val minimum = PlanLogic.requiredAmount(plan)
                val entered = payState.value.amountText?.toIntOrNull()
                if (entered == null || entered < minimum) {
                    payState.update { it.copy(amountError = "Enter at least KES %,d".format(minimum)) }
                    return@launch
                }
                entered
            } else {
                repository.amountDueNow(planId)
            }
            payState.update { it.copy(amountError = null) }

            val reference = if (plan.type == PlanType.GOAL) "goal" else "save"
            val backend = paymentRepository
            if (backend != null) {
                realPayment(backend, phone, amount, reference, planId)
            } else {
                // No backend configured — do NOT fake a success. Tell the user plainly.
                payState.update {
                    it.copy(paymentStatus = PaymentStatus.Failed(
                        "M-Pesa isn't set up yet on this build. The backend (Supabase + Daraja) must be " +
                            "connected first. Use a recovery code to unlock for now."
                    ))
                }
            }
        }
    }

    private suspend fun realPayment(
        backend: PaymentRepository,
        phone: String,
        amount: Int,
        reference: String,
        planId: Long
    ) {
        payState.update { it.copy(paymentStatus = PaymentStatus.Requesting) }
        val result = backend.pay(phone, amount, reference, onStkSent = {
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

    private suspend fun onPaymentSucceeded(planId: Long, amount: Int) {
        repository.recordPlanPayment(planId, amount, checkoutRequestId = null)
        val ctx = ServiceLocator.applicationContext
        NotificationManagerHelper.showPaymentResult(ctx, success = true, amount = amount)
        // Only clear the persistent lock notification if nothing else is still locking.
        if (!ServiceLocator.lockStateManager.refreshNow()) {
            NotificationManagerHelper.clearLockActive(ctx)
            AlarmScheduler.cancelReminders(ctx)
        }
        payState.update { it.copy(paymentStatus = PaymentStatus.Success) }
    }

    fun resetPaymentState() {
        payState.update { it.copy(paymentStatus = PaymentStatus.Idle) }
    }

    /**
     * Confirm a payment by pasting its M-Pesa code (offline fallback when auto-SMS-unlock didn't fire).
     * The code is verified against the REAL M-Pesa message in the inbox — a made-up code won't work —
     * and, if a till name is set, must be a payment to that till. On success it credits the due plans.
     */
    fun confirmPastedMpesaCode(code: String) {
        viewModelScope.launch {
            val trimmed = code.trim()
            if (trimmed.isEmpty()) {
                _pasteStatus.value = PasteStatus.Failed("Enter the M-Pesa code from your message.")
                return@launch
            }
            _pasteStatus.value = PasteStatus.Checking
            val ctx = ServiceLocator.applicationContext
            val parsed = com.example.util.SmsInbox.findMpesaByCode(ctx, trimmed)
            if (parsed == null) {
                _pasteStatus.value = PasteStatus.Failed(
                    "No M-Pesa payment with that code was found in your Messages. Check the code and that SMS access is granted."
                )
                return@launch
            }
            val cfg = repository.getConfig()
            if (cfg.tillName.isNotBlank() &&
                !com.example.domain.MpesaPaymentSms.matchesTill(parsed, cfg.tillName)
            ) {
                _pasteStatus.value = PasteStatus.Failed("That payment wasn't to your till (${cfg.tillName}).")
                return@launch
            }
            val credited = repository.applyExternalPayment(parsed.receipt, parsed.amount)
            if (credited) {
                ServiceLocator.lockStateManager.refreshNow()
                NotificationManagerHelper.showPaymentResult(ctx, success = true, amount = parsed.amount)
                _pasteStatus.value = PasteStatus.Success
            } else {
                _pasteStatus.value = PasteStatus.Failed("That code was already applied, or no payment is currently due.")
            }
        }
    }

    fun resetPasteStatus() {
        _pasteStatus.value = PasteStatus.Idle
    }
}
