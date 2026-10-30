package crazytower.core

/**
 * The simulation clock. Time is measured in game minutes; the simulation is advanced
 * in fractions of a minute so movement stays smooth at every game speed.
 */
class GameClock(startMinutes: Double = 7.0 * 60) {
    var totalMinutes: Double = startMinutes
        private set

    val day: Int get() = (totalMinutes / MINUTES_PER_DAY).toInt()
    val minuteOfDay: Int get() = (totalMinutes % MINUTES_PER_DAY).toInt()
    val hour: Int get() = minuteOfDay / 60
    val minute: Int get() = minuteOfDay % 60
    val weekday: Int get() = day % 7
    val isNight: Boolean get() = hour < 6 || hour >= 21

    fun advance(minutes: Double) {
        totalMinutes += minutes
    }

    /** Sets the clock, used when loading a save game. */
    fun set(minutes: Double) {
        totalMinutes = minutes
    }

    fun format(): String = "${WEEKDAYS[weekday]} ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        const val MINUTES_PER_DAY = 1440
        private val WEEKDAYS_DE = listOf("MO", "DI", "MI", "DO", "FR", "SA", "SO")
        private val WEEKDAYS_EN = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

        /** Short weekday names in the current language, Monday first. */
        val WEEKDAYS: List<String> get() = if (I18n.english) WEEKDAYS_EN else WEEKDAYS_DE
    }
}
