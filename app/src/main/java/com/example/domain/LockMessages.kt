package com.example.domain

/** A provoking lock-screen message: a bold [title] plus a supporting [subtitle]. */
data class LockMessage(val title: String, val subtitle: String)

/** The values a GOAL message can weave in (see the `{...}` placeholders below). */
data class GoalVars(
    val goalName: String,
    val daysLeft: Int,
    val amountRemaining: Int,
    val percent: Int
)

/**
 * The deliberately aggressive, pain-pointed lock-screen copy the owner asked for. One message is
 * shown per day and it ROTATES daily through the pool, so a different provocation appears each day.
 *
 * Two pools: [SAVINGS] plans and [GOAL] plans. Goal copy can reference the goal name, days left,
 * amount remaining and percent complete via the `{GOAL}`, `{DAYS_LEFT}`, `{AMOUNT_REMAINING}` and
 * `{PERCENT}` placeholders — these are filled in per plan at display time.
 *
 * (The owner's list had a couple of exact duplicates; those were dropped so no message repeats
 * back-to-back within a rotation.)
 */
object LockMessages {

    private val SAVINGS = listOf(
        LockMessage(
            "If you can't save today, you'll struggle tomorrow.",
            "Complete today's savings to continue."
        ),
        LockMessage(
            "You always say you'll save tomorrow. Today is tomorrow.",
            "Save now to regain access."
        ),
        LockMessage(
            "Ksh 50 feels small — until you need it.",
            "Save it for tomorrow."
        ),
        LockMessage(
            "You can scroll for hours but can't save even 1 bob?",
            "Prove yourself wrong."
        ),
        LockMessage(
            "Today's savings protect tomorrow's problems.",
            "Don't skip the protection."
        ),
        LockMessage(
            "No more negotiations.",
            "Save. Unlock. Continue."
        )
    )

    private val GOALS = listOf(
        LockMessage(
            "Your {GOAL} wasn't cancelled. You just chose to delay it.",
            "Make today's payment to continue."
        ),
        LockMessage(
            "If {GOAL} matters, prove it.",
            "Pay today's amount or remain locked."
        ),
        LockMessage(
            "Today's excuse becomes tomorrow's regret.",
            "Save now to regain access."
        ),
        LockMessage(
            "{DAYS_LEFT} days remaining to reach {GOAL}.",
            "Missing today means working harder tomorrow."
        ),
        LockMessage(
            "You're only {AMOUNT_REMAINING} away from {GOAL}.",
            "Don't stop now."
        ),
        LockMessage(
            "Goal Progress: {PERCENT}%",
            "Don't let today's decision erase yesterday's progress."
        ),
        LockMessage(
            "You asked this app to stop you from lying to yourself.",
            "Pay or remain locked."
        ),
        LockMessage(
            "Today's contribution toward {GOAL} is overdue.",
            "Make today's payment to continue."
        )
    )

    /** The SAVINGS message for a given day (rotates once per day). */
    fun forSavings(dayIndex: Long): LockMessage =
        SAVINGS[Math.floorMod(dayIndex, SAVINGS.size.toLong()).toInt()]

    /** The GOAL message for a given day, with its placeholders filled from [vars]. */
    fun forGoal(dayIndex: Long, vars: GoalVars): LockMessage {
        val raw = GOALS[Math.floorMod(dayIndex, GOALS.size.toLong()).toInt()]
        return LockMessage(fill(raw.title, vars), fill(raw.subtitle, vars))
    }

    private fun fill(text: String, v: GoalVars): String =
        text.replace("{GOAL}", v.goalName)
            .replace("{DAYS_LEFT}", v.daysLeft.toString())
            .replace("{AMOUNT_REMAINING}", "Ksh %,d".format(v.amountRemaining))
            .replace("{PERCENT}", v.percent.toString())
}
