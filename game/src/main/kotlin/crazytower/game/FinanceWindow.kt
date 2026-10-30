package crazytower.game

import crazytower.core.GameClock
import crazytower.core.MoneyKind
import crazytower.core.Simulation
import crazytower.game.ui.Panel
import crazytower.game.ui.TextButton
import crazytower.game.ui.formatMoney
import crazytower.game.ui.fitWidth
import crazytower.game.ui.label
import crazytower.core.tr
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
        label(tr("FINANZEN", "FINANCES"), 20.0, Theme.TEXT, Theme.bodyBold).xy(18.0, 14.0)
        val hint = label(tr("F oder Esc schließt", "F or Esc closes"), 12.0, Theme.TEXT_DIM)
        hint.xy(panelWidth - 22.0 - hint.width, 20.0)
        TextButton(110.0, 36.0, tr("Schließen", "Close")) { onClose() }.addTo(this).xy(panelWidth - 128.0, panelHeight - 50.0)
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
        body.label(tr("Nächste Zahlungen", "Next payments"), 15.0, Theme.TEXT, Theme.bodyBold).xy(18.0, 52.0).fitWidth(AMOUNT_RIGHT - 18.0)
        val today = sim.clock.day
        var y = 80.0
        val all = sim.upcoming(36)
        val list = all.take(ROWS)
        if (list.isEmpty()) body.label(tr("Noch keine Mieter, noch kein Geld.", "No tenants yet, no money yet."), 13.0, Theme.TEXT_DIM).xy(18.0, y).fitWidth(AMOUNT_RIGHT - 18.0)
        for (item in list) {
            val day = item.minute / GameClock.MINUTES_PER_DAY
            val m = item.minute % GameClock.MINUTES_PER_DAY
            val time = (if (day == today) "" else "${GameClock.WEEKDAYS[day % 7]} ") +
                "%02d:%02d".format(m / 60, m % 60)
            body.label(time, 12.0, Theme.TEXT_DIM, Theme.bodyBold).xy(18.0, y)
            val amount = body.label(signed(item.amount), 13.0, if (item.amount >= 0) Theme.GOOD else Theme.ALARM, Theme.bodyBold)
            amount.xy(AMOUNT_RIGHT - amount.width, y - 1)
            val count = if (item.count > 1) " ×${item.count}" else ""
            nameLabel(item.label, count, AMOUNT_RIGHT - amount.width - 10 - NAME_X).xy(NAME_X, y - 1)
            y += 24.0
        }
        if (all.isNotEmpty()) {
            body.solidRect(AMOUNT_RIGHT - 18.0, 1.0, Theme.TEXT_DIM).xy(18.0, y + 2)
            val total = all.sumOf { it.amount }
            val sum = body.label(signed(total), 13.0, if (total >= 0) Theme.GOOD else Theme.ALARM, Theme.bodyBold)
            sum.xy(AMOUNT_RIGHT - sum.width, y + 8)
            body.label(tr("Summe nächste 36 Std.", "Total next 36 hours"), 13.0, Theme.TEXT, Theme.bodyBold).xy(18.0, y + 8)
                .fitWidth(AMOUNT_RIGHT - sum.width - 28.0)
        }
    }

    private fun signed(amount: Long): String = (if (amount > 0) "+" else "") + formatMoney(amount)

    /** The payer's name, shortened with "…" so that it and [suffix] fit into [width]. */
    private fun nameLabel(name: String, suffix: String, width: Double): Text {
        val label = body.label(name + suffix, 13.0, Theme.TEXT)
        var keep = name.length
        while (label.width > width && keep > 1) {
            keep--
            label.text = name.take(keep).trimEnd() + "…" + suffix
        }
        return label
    }

    private fun chart() {
        val left = 350.0
        val top = 80.0
        val height = 200.0
        val barWidth = 14.0
        body.label(tr("Letzte 7 Tage", "Last 7 days"), 15.0, Theme.TEXT, Theme.bodyBold).xy(left, 52.0)
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
            body.label(if (day == today) tr("heute", "today") else GameClock.WEEKDAYS[day % 7], 11.0, Theme.TEXT_DIM).xy(x, top + height + 6).fitWidth(34.0)
        }
        body.solidRect(250.0, 1.0, Theme.TEXT_DIM).xy(left, top + height)
        body.solidRect(10.0, 10.0, Theme.GOOD).xy(left, top + height + 30)
        body.label(tr("Einnahmen", "Income"), 12.0, Theme.TEXT_DIM).xy(left + 14, top + height + 27).fitWidth(82.0)
        body.solidRect(10.0, 10.0, Theme.ALARM).xy(left + 100, top + height + 30)
        body.label(tr("Ausgaben (mit Bauen)", "Expenses (incl. building)"), 12.0, Theme.TEXT_DIM).xy(left + 114, top + height + 27).fitWidth(panelWidth - left - 114 - 18.0)

        val sums = sim.finance.day(today)
        val parts = MoneyKind.entries.filter { it.income && (sums[it] ?: 0L) != 0L }
            .joinToString("  ·  ") { "${it.label} ${formatMoney(sums.getValue(it))}" }
        val todayLine = body.label(tr("Heute: ", "Today: ") + parts.ifEmpty { tr("noch nichts eingenommen", "nothing earned yet") }, 12.0, Theme.TEXT_DIM)
        todayLine.xy(18.0, panelHeight - 40.0)
        // keep clear of the close button
        todayLine.fitWidth(panelWidth - 128.0 - 12.0 - 18.0)
    }

    private companion object {
        const val ROWS = 8
        const val NAME_X = 88.0
        const val AMOUNT_RIGHT = 330.0
    }
}
