package crazytower.core

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Plays 30 days like a sensible player: a lobby, an elevator through the middle, then every
 * hour the next room as soon as there is money for it, and more cabins when the stars allow. Checks that the start money runs out
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
            // a sensible player adds elevator cabins as soon as the stars allow
            sim.elevators.cars.values.firstOrNull()?.let { car ->
                if (sim.money >= crazytower.core.transport.Elevators.CABIN_COST + RESERVE && sim.checkCabin(car).ok) sim.addCabin(car)
            }
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

    private fun play(days: Int, seed: Long): Pair<Player, List<String>> {
        val sim = Simulation(seed = seed).apply { eventsEnabled = false }
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
        // tenants move in by chance, so a single game can be lucky or unlucky: judge the average of three
        val games = listOf(42L, 7L, 11L).map { seed -> play(30, seed).also { println("Seed $seed\n" + it.second.joinToString("\n")) }.first }
        for (player in games) {
            val firstDay = player.built.count { it == 0 }
            assertTrue(firstDay in 3..10, "the start money buys a handful of rooms, built $firstDay")
            val full = player.built.size == 36
            // flats pay for themselves when sold, offices after about a week
            assertTrue(full && player.built.last() in 7..15, "the first ten floors fill up in one to two weeks, last room on day ${player.built.lastOrNull()}")
        }
        val firstWeek = games.map { p -> p.built.count { it in 1..7 } }
        assertTrue(firstWeek.average() >= 10, "then the rent pays for more than a room a day, built $firstWeek in days 1 to 7")
    }

    companion object {
        const val RESERVE = 5_000L
    }
}
