package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AmountType
import com.example.data.local.entity.PeriodType
import com.example.data.local.entity.PlanType
import com.example.ui.theme.SaveLockPrimary
import com.example.ui.theme.SaveLockRed
import com.example.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatePlanScreen(
    viewModel: DashboardViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    editingPlanId: Long = -1L
) {
    val scroll = rememberScrollState()
    val isEditing = editingPlanId >= 0

    var type by remember { mutableStateOf(PlanType.SAVINGS) }
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var amountType by remember { mutableStateOf(AmountType.FIXED) }
    var period by remember { mutableStateOf(PeriodType.DAILY) }
    var nText by remember { mutableStateOf("3") }
    var goalTotalText by remember { mutableStateOf("") }
    var goalDaysText by remember { mutableStateOf("") }

    // Prefill the form when editing an existing plan.
    LaunchedEffect(editingPlanId) {
        if (isEditing) {
            viewModel.getPlan(editingPlanId)?.let { p ->
                type = p.type
                name = p.name
                amountText = p.amount.toString()
                amountType = p.amountType
                period = p.period
                nText = p.periodValue.coerceAtLeast(1).toString()
                goalTotalText = if (p.goalTotal > 0) p.goalTotal.toString() else ""
                goalDaysText = if (p.goalDurationDays > 0) p.goalDurationDays.toString() else ""
            }
        }
    }

    val amount = amountText.toIntOrNull() ?: 0
    val n = nText.toIntOrNull() ?: 0
    val goalTotal = goalTotalText.toIntOrNull() ?: 0
    val needsN = period == PeriodType.EVERY_N_DAYS || period == PeriodType.EVERY_N_HOURS

    val amountValid = amount > 0
    val nValid = !needsN || n > 0
    val goalValid = type != PlanType.GOAL || goalTotal >= amount && goalTotal > 0
    val canCreate = amountValid && nValid && goalValid

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                IconButton(onClick = onDone) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isEditing) "Edit plan" else "New Savings or Goal",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
            }

            // Type selector
            SectionLabel("PLAN TYPE")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TypeChip(
                    selected = type == PlanType.SAVINGS,
                    label = "Savings",
                    subtitle = "Repeat forever",
                    icon = { Icon(Icons.Default.Savings, null, modifier = Modifier.size(18.dp)) },
                    onClick = { type = PlanType.SAVINGS },
                    modifier = Modifier.weight(1f)
                )
                TypeChip(
                    selected = type == PlanType.GOAL,
                    label = "Goal",
                    subtitle = "Reach a target",
                    icon = { Icon(Icons.Default.Flag, null, modifier = Modifier.size(18.dp)) },
                    onClick = { type = PlanType.GOAL },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("NAME")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(if (type == PlanType.GOAL) "e.g. New laptop" else "e.g. Daily discipline") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel(if (type == PlanType.GOAL) "AMOUNT TO SAVE EACH PERIOD" else "AMOUNT EACH PERIOD")
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                label = { Text("Amount (KES)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = amountText.isNotEmpty() && !amountValid,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SmallChip("Exact amount", amountType == AmountType.FIXED, Modifier.weight(1f)) { amountType = AmountType.FIXED }
                SmallChip("Minimum (pay more OK)", amountType == AmountType.FLEXIBLE, Modifier.weight(1f)) { amountType = AmountType.FLEXIBLE }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("HOW OFTEN IS IT DUE?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodOption("Daily", period == PeriodType.DAILY) { period = PeriodType.DAILY }
                PeriodOption("Every 2 days", period == PeriodType.EVERY_2_DAYS) { period = PeriodType.EVERY_2_DAYS }
                PeriodOption("Weekly", period == PeriodType.WEEKLY) { period = PeriodType.WEEKLY }
                PeriodOption("Monthly", period == PeriodType.MONTHLY) { period = PeriodType.MONTHLY }
                PeriodOption("Every N days", period == PeriodType.EVERY_N_DAYS) { period = PeriodType.EVERY_N_DAYS }
                PeriodOption("Every N hours", period == PeriodType.EVERY_N_HOURS) { period = PeriodType.EVERY_N_HOURS }
            }
            if (needsN) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = nText,
                    onValueChange = { nText = it.filter { c -> c.isDigit() } },
                    label = { Text(if (period == PeriodType.EVERY_N_HOURS) "Number of hours (N)" else "Number of days (N)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = nText.isNotEmpty() && !nValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (type == PlanType.GOAL) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("GOAL TARGET")
                OutlinedTextField(
                    value = goalTotalText,
                    onValueChange = { goalTotalText = it.filter { c -> c.isDigit() } },
                    label = { Text("Total to reach (KES)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = goalTotalText.isNotEmpty() && !goalValid,
                    supportingText = { Text("Must be at least the per-period amount") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = goalDaysText,
                    onValueChange = { goalDaysText = it.filter { c -> c.isDigit() } },
                    label = { Text("Deadline in days (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Leave blank for no deadline") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val pv = if (needsN) n else 1
                    val gt = if (type == PlanType.GOAL) goalTotal else 0
                    val gd = if (type == PlanType.GOAL) (goalDaysText.toIntOrNull() ?: 0) else 0
                    if (isEditing) {
                        viewModel.updatePlan(editingPlanId, type, name, amountType, amount, period, pv, gt, gd)
                    } else {
                        viewModel.createPlan(type, name, amountType, amount, period, pv, gt, gd)
                    }
                    onDone()
                },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SaveLockPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (isEditing) "Save changes" else "Create plan",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Once created, your phone locks each period until you pay this plan (or use a recovery code). Plans run in parallel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = SaveLockPrimary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun TypeChip(
    selected: Boolean,
    label: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (selected) SaveLockPrimary else MaterialTheme.colorScheme.outlineVariant
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) SaveLockPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon()
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SmallChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SaveLockPrimary.copy(alpha = 0.2f),
            selectedLabelColor = SaveLockPrimary
        )
    )
}

@Composable
private fun PeriodOption(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SaveLockPrimary.copy(alpha = 0.2f),
            selectedLabelColor = SaveLockPrimary
        )
    )
}
