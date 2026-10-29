package crazytower.core

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Plays 30 days like a sensible player: a lobby, an elevator through the middle, then every
 * hour the next room as soon as there is money for it. Checks that the start money runs out
 * quickly but the tower keeps paying for new rooms.
 */
class BalanceTest {
    private class Player(val sim: Simulation) {
        val left = 20
        val pattern = listOf(Catalog.OFFICE, Catalog.HOME, Catalog.OFFICE, Catalog.HOME)
        var next = 0
        val built = ArrayList<Int>() // day of each room

        fun start() {
            check(sim.build(sim.planLobby(left, left + 23)).ok)
            check(sim.build(sim.planColumn(Catalog.ELEVATOR, left + 11, GROUND_FLOOR, 1)).ok)
        }

        fun tryBuild() {
            val floor = 1 + next / pattern.size
            if (floor >= Stages.info(sim.stage).maxFloor) return
            val slot = next % pattern.size
            val type = if (floor == 1 && slot == 3) Catalog.FOOD else pattern[slot]
            val shaft = if (sim.tower.wayAt(floor, left + 11) == null) Catalog.ELEVATOR.cost else 0
            if (sim.money < type.cost + shaft + RESERVE) return
            check(sim.build(type, floor, left + slot * 6).ok)
            if (shaft > 0) check(sim.build(sim.planColumn(Catalog.ELEVATOR, left + 11, floor - 1, floor)).ok)
            built += sim.clock.day
            next++
        }
    }

    private fun play(days: Int): Pair<Player, List<String>> {
        val sim = Simulation(seed = 42).apply { eventsEnabled = false }
        val player = Player(sim)
        player.start()
        val log = ArrayList<String>()
        for (hour in 0 until days * 24) {
            sim.step(60.0)
            player.tryBuild()
            if (sim.clock.hour == 23) {
                log += "Tag ${sim.clock.day}: ${sim.money} $, ${player.next} Räume, ${sim.population} Leute, ${sim.stars} Sterne, " +
                    "Einnahmen ${sim.finance.income(sim.clock.day)} $"
            }
        }
        return player to log
    }

    @Test
    fun theTowerKeepsPayingForItself() {
        val (player, log) = play(30)
        println(log.joinToString("\n"))
        val firstDay = player.built.count { it == 0 }
        val firstWeek = player.built.count { it in 1..7 }
        assertTrue(firstDay in 3..6, "the start money buys a handful of rooms, built $firstDay")
        assertTrue(firstWeek >= 10, "then the rent pays for more than a room a day, built $firstWeek in days 1 to 7")
        val full = player.built.size == 36
        assertTrue(full && player.built.last() <= 15, "the first ten floors are full within 15 days, last room on day ${player.built.lastOrNull()}")
    }

    companion object {
        const val RESERVE = 5_000L
    }
}
