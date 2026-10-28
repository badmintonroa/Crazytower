package crazytower.core

/** Opening hours and meal times. All times are game minutes of the day. */
object Routine {
    /** True when [minuteOfDay] lies in the window from [startHour] to [endHour], wrapping over midnight. */
    fun inWindow(minuteOfDay: Int, startHour: Int, endHour: Int, offset: Int = 0): Boolean {
        val start = (startHour * 60 + offset).mod(GameClock.MINUTES_PER_DAY)
        val end = (endHour * 60 + offset).mod(GameClock.MINUTES_PER_DAY)
        return if (start <= end) minuteOfDay in start until end else minuteOfDay >= start || minuteOfDay < end
    }

    /**
     * Whether a member wants to be inside the tower. Employees come for their shift (offices
     * close on the weekend, restaurants never). Residents are out during their shift and home
     * the rest of the time.
     */
    fun wantsInside(role: RoomRole, resident: Boolean, shift: Shift, offset: Int, clock: GameClock): Boolean {
        val inShift = inWindow(clock.minuteOfDay, shift.startHour, shift.endHour, offset)
        if (resident) return !inShift || clock.weekday >= 5 && shift == Shift.DAY
        if (role == RoomRole.OFFICE && clock.weekday >= 5) return false
        return inShift
    }

    /** Whether it is time for a meal: lunch in the middle of the shift, dinner at home. */
    fun mealTime(resident: Boolean, shift: Shift, offset: Int, clock: GameClock): Boolean {
        val (from, to) = when {
            resident && shift == Shift.NIGHT -> 12 to 14
            resident -> 18 to 20
            shift == Shift.DAY -> 12 to 14
            shift == Shift.EVENING -> 18 to 20
            else -> 2 to 4
        }
        return inWindow(clock.minuteOfDay, from, to, offset / 2)
    }
}
