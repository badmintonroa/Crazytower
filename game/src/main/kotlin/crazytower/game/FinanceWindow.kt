package crazytower.game

import crazytower.core.GameClock
import crazytower.core.MoneyKind
import crazytower.core.Simulation
import crazytower.game.ui.Panel
import crazytower.game.ui.TextButton
import crazytower.game.ui.formatMoney
import crazytower.game.ui.label
import korlibs.korge.view.*

/**
 * The finance window: what is paid when in the next day and a half, and a bar chart of
 * income and expenses over the last seven days. Opened by clicking the money (or F).
 */
class FinanceWindow(private val sim: Simulation, private val onClose: () -> Unit) : Container() {
    val panelWidth = 620.0
    val panelHeight = 380.0
    private val bg = Panel(panelWidth, panelHeight).addTo(this)
    private val body = container()
    private var shownMinute = -1

    init {
        label("FINANZEN", 20.0, Theme.TEXT, Theme.bodyBold).xy(18.0, 14.0)
        label("F oder Esc schließt", 12.0, Theme.TEXT_DIM).xy(panelWidth - 140.0, 20.0)
        TextButton(110.0, 36.0, "Schließen") { onClose() }.addTo(this).xy(panelWidth - 128.0, panelHeight - 50.0)
    }

    fun sync() {
        val minute = sim.clock.totalMinutes.toInt()
        if (minute == shownMinute) return
        shownMinute = minute
        body.removeChildren()
        upcoming()
        chart()
    }

    private fun upcoming() {
        body.label("Nächste Zahlungen", 15.0, Theme.TEXT, Theme.bodyBold).xy(18.0, 52.0)
        val today = sim.clock.day
        var y = 80.0
        val list = sim.upcoming(36).take(10)
        if (list.isEmpty()) body.label("Noch keine Mieter, noch kein Geld.", 13.0, Theme.TEXT_DIM).xy(18.0, y)
        for (item in list) {
            val day = item.minute / GameClock.MINUTES_PER_DAY
            val m = item.minute % GameClock.MINUTES_PER_DAY
            val time = (if (day == today) "" else if (day == today + 1) "morgen " else "${GameClock.WEEKDAYS[day % 7]} ") +
                "%02d:%02d".format(m / 60, m % 60)
            body.label(time, 12.0, Theme.TEXT_DIM, Theme.bodyBold).xy(18.0, y)
            body.label(item.label.take(26), 13.0, Theme.TEXT).xy(110.0, y - 1)
            val amount = (if (item.amount > 0) "+" else "") + formatMoney(item.amount)
            val text = body.label(amount, 13.0, if (item.amount >= 0) Theme.GOOD else Theme.ALARM, Theme.bodyBold)
            text.xy(320.0 - text.width, y - 1)
            y += 26.0
        }
    }

    private fun chart() {
        val left = 350.0
        val top = 80.0
        val height = 200.0
        val barWidth = 14.0
        body.label("Letzte 7 Tage", 15.0, Theme.TEXT, Theme.bodyBold).xy(left, 52.0)
        val today = sim.clock.day
        val days = (today - 6..today).filter { it >= 0 }
        val values = days.map { sim.finance.income(it) to sim.finance.expenses(it) }
        val max = values.maxOfOrNull { maxOf(it.first, it.second) }?.coerceAtLeast(1) ?: 1
        days.forEachIndexed { i, day ->
            val (income, expenses) = values[i]
            val x = left + i * 36.0
            val hIn = height * income / max
            val hOut = height * expenses / max
            body.solidRect(barWidth, hIn.coerceAtLeast(1.0), Theme.GOOD).xy(x, top + height - hIn)
            body.solidRect(barWidth, hOut.coerceAtLeast(1.0), Theme.ALARM).xy(x + barWidth + 2, top + height - hOut)
            body.label(if (day == today) "heute" else GameClock.WEEKDAYS[day % 7], 11.0, Theme.TEXT_DIM).xy(x, top + height + 6)
        }
        body.solidRect(250.0, 1.0, Theme.TEXT_DIM).xy(left, top + height)
        body.solidRect(10.0, 10.0, Theme.GOOD).xy(left, top + height + 30)
        body.label("Einnahmen", 12.0, Theme.TEXT_DIM).xy(left + 14, top + height + 27)
        body.solidRect(10.0, 10.0, Theme.ALARM).xy(left + 100, top + height + 30)
        body.label("Ausgaben (mit Bauen)", 12.0, Theme.TEXT_DIM).xy(left + 114, top + height + 27)

        val sums = sim.finance.day(today)
        val parts = MoneyKind.entries.filter { it.income && (sums[it] ?: 0L) != 0L }
            .joinToString("  ·  ") { "${it.label} ${formatMoney(sums.getValue(it))}" }
        body.label("Heute: " + parts.ifEmpty { "noch nichts eingenommen" }, 12.0, Theme.TEXT_DIM).xy(18.0, panelHeight - 40.0)
    }
}
