package crazytower.core

/** What money came in or went out for; the finance window groups by it. */
enum class MoneyKind(val label: String, val income: Boolean) {
    RENT("Büro-Miete", true),
    FOOD("Restaurant-Miete", true),
    SALE("Wohnungsverkauf", true),
    FEES("Hausgeld", true),
    GUESTS("Gäste", true),
    PARKING("Parkgebühren", true),
    LUCK("Glück", true),
    BUILD("Bauen", false),
    UPKEEP("Unterhalt", false),
    REFUND("Rückzahlung", false),
    FINE("Strafen", false),
}

/** One booking. [where] places the floating amount in the tower. */
data class Payment(val minute: Double, val amount: Long, val kind: MoneyKind, val label: String, val where: Loc? = null)

/**
 * A payment the tower expects soon, for the HUD and the finance window. Payments by the
 * same payer at the same minute are one entry: [count] payments summed into [amount].
 */
data class Upcoming(val minute: Int, val amount: Long, val label: String, val where: Loc? = null, val count: Int = 1)

/**
 * Keeps the books: every payment, summed per day and kind for the last days, plus a queue
 * of fresh payments the front end shows as floating amounts.
 */
class Finance {
    private val byDay = LinkedHashMap<Int, LinkedHashMap<MoneyKind, Long>>()

    /** Payments since the front end last looked; it drains this queue. */
    val fresh = ArrayDeque<Payment>()

    fun record(payment: Payment) {
        val day = (payment.minute / GameClock.MINUTES_PER_DAY).toInt()
        val sums = byDay.getOrPut(day) { LinkedHashMap() }
        sums[payment.kind] = (sums[payment.kind] ?: 0L) + payment.amount
        while (byDay.size > KEEP_DAYS) byDay.remove(byDay.keys.first())
        if (payment.kind != MoneyKind.BUILD) fresh += payment
        while (fresh.size > 50) fresh.removeFirst()
    }

    /** Sums per kind for [day]; empty when nothing happened or the day is too old. */
    fun day(day: Int): Map<MoneyKind, Long> = byDay[day].orEmpty()

    fun income(day: Int): Long = day(day).filterKeys { it.income }.values.sum()
    fun expenses(day: Int): Long = -day(day).filterKeys { !it.income }.values.sum()

    companion object {
        const val KEEP_DAYS = 8
    }
}
