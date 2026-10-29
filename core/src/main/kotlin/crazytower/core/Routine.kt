package crazytower.core

import kotlin.math.abs

/** Opening hours and meal times. All times are game minutes. */
object Routine {
    /** True when [minuteOfDay] lies in the window from [startHour] to [endHour], wrapping over midnight. */
    fun inWindow(minuteOfDay: Int, startHour: Int, endHour: Int, offset: Int = 0): Boolean {
        val start = (startHour * 60 + offset).mod(GameClock.MINUTES_PER_DAY)
        val end = (endHour * 60 + offset).mod(GameClock.MINUTES_PER_DAY)
        return if (start <= end) minuteOfDay in start until end else minuteOfDay >= start || minuteOfDay < end
    }

    /** How late a person with [seed] turns up today; the excuse agency is never on time. */
    fun lateness(plan: WorkPlan, seed: Int, day: Int): Int =
        if (plan.lateUpTo <= 0) 0 else (seed * 31 + day * 977).mod(plan.lateUpTo + 1)

    /**
     * Whether a member wants to be inside the tower. Employees come for the hours of their
     * tenant's plan; residents are out during theirs and at home the rest of the time.
     */
    fun wantsInside(resident: Boolean, plan: WorkPlan, crew: Int, seed: Int, offset: Int, clock: GameClock): Boolean {
        val busy = plan.isBusy(clock.totalMinutes - offset, crew, lateness(plan, seed, clock.day))
        return if (resident) !busy else busy
    }

    /** Whether it is time for a meal: lunch in the middle of the working hours, dinner at home. */
    fun mealTime(resident: Boolean, plan: WorkPlan, offset: Int, clock: GameClock): Boolean {
        val now = clock.totalMinutes - offset / 2
        if (resident) {
            val dinner = if (plan.isBusy(clock.day * GameClock.MINUTES_PER_DAY + 19.0 * 60)) 12 else 18
            return inWindow(clock.minuteOfDay, dinner, dinner + 2, offset / 2)
        }
        val middle = plan.middle(now) ?: return false
        return abs(now - middle) < 60
    }
}
