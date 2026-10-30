package crazytower.core

import crazytower.core.transport.Elevators
import kotlin.test.Test

/**
 * Not a check but a measuring tape for the balance: plays towers with different shares of flats
 * and offices like [BalanceTest] does and prints income against costs, day by day. Run it with
 * `./gradlew :core:test --tests '*BalanceScenarios*' -i` and read the table.
 */
class BalanceScenarios {
    private class Player(val sim: Simulation, val homeShare: Double) {
        /** Ten rooms a floor fill the first lot; two shafts, the second one once the tower gets busy. */
        val perFloor = 10
        val shafts = listOf(17, 41)
        var rooms = 0
        var homes = 0
        var lastBuildDay = 0
        var skippedHours = 0
        var spentOnWays = 0L

        fun start() {
            check(sim.build(sim.planLobby(0, perFloor * 6 - 1)).ok)
            buildShaft(shafts[0], GROUND_FLOOR, 1)
        }

        private fun buildShaft(x: Int, from: Int, to: Int) {
            val before = sim.money
            check(sim.build(sim.planColumn(Catalog.ELEVATOR, x, from, to)).ok) { "shaft at $x $from..$to" }
            spentOnWays += before - sim.money
        }

        private fun activeShafts(floor: Int) = if (floor >= SECOND_SHAFT_FROM) shafts else shafts.take(1)

        fun tryBuild() {
            sim.elevators.cars.values.forEach { car ->
                if (sim.money >= Elevators.CABIN_COST + RESERVE && sim.checkCabin(car).ok) {
                    sim.addCabin(car)
                    spentOnWays += Elevators.CABIN_COST
                }
            }
            val floor = 1 + rooms / perFloor
            // a shaft reaches floor 24, the sky lobby on 25 is out of scope here
            if (floor >= minOf(Stages.info(sim.stage).maxFloor, Tower.MAX_SHAFT_FLOORS)) return
            val slot = rooms % perFloor
            val type = when {
                slot == perFloor - 1 && (floor == 1 || floor % 4 == 0) -> Catalog.FOOD
                homes < homeShare * (rooms + 1) -> Catalog.HOME
                else -> Catalog.OFFICE
            }
            val missing = activeShafts(floor).filter { sim.tower.wayAt(floor, it) == null }
            val shaftCost = missing.sumOf { x -> sim.priceAt(Catalog.ELEVATOR, floor, x) * (if (sim.tower.wayAt(floor - 1, x) == null) floor + 1 else 1) }
            if (sim.money < sim.priceAt(type, floor, slot * 6) + shaftCost + RESERVE) {
                skippedHours++
                return
            }
            check(sim.build(type, floor, slot * 6).ok) { "${type.name} on $floor slot $slot" }
            for (x in missing) buildShaft(x, if (sim.tower.wayAt(floor - 1, x) == null) GROUND_FLOOR else floor - 1, floor)
            if (type == Catalog.HOME) homes++
            rooms++
            lastBuildDay = sim.clock.day
        }
    }

    private fun play(homeShare: Double, days: Int, seed: Long): String {
        val sim = Simulation(seed = seed).apply { eventsEnabled = false }
        val player = Player(sim, homeShare)
        player.start()
        val out = StringBuilder("== ${(homeShare * 100).toInt()} % Wohnungen, Seed $seed ==\n")
        out.append("Tag | Geld | Räume (W) | Leute | ★ | Stufe | Laune | Miete | Imbiss | Verkauf | Hausgeld | Parken | Gäste | Bauen | Unterhalt | netto ohne Bauen\n")
        for (hour in 0 until days * 24) {
            sim.step(60.0)
            player.tryBuild()
            if (sim.clock.hour == 23) {
                val d = sim.finance.day(sim.clock.day)
                // upkeep, fees and parking are booked at midnight, so they show up on the next day
                fun k(kind: MoneyKind) = d[kind] ?: 0L
                val net = MoneyKind.entries.filter { it != MoneyKind.BUILD }.sumOf { d[it] ?: 0L }
                out.append("${sim.clock.day} | ${sim.money} | ${player.rooms} (${player.homes}) | ${sim.population} | ${sim.stars} | ${sim.stage} | ${sim.satisfaction.toInt()} | " +
                    "${k(MoneyKind.RENT)} | ${k(MoneyKind.FOOD)} | ${k(MoneyKind.SALE)} | ${k(MoneyKind.FEES)} | ${k(MoneyKind.PARKING)} | ${k(MoneyKind.GUESTS)} | " +
                    "${k(MoneyKind.BUILD)} | ${k(MoneyKind.UPKEEP)} | $net\n")
            }
        }
        out.append("letzter Bau an Tag ${player.lastBuildDay}, Stunden ohne Geld für den nächsten Raum: ${player.skippedHours}, " +
            "für Aufzüge ausgegeben: ${player.spentOnWays} $, Laune ${sim.satisfaction.toInt()} %\n")
        return out.toString()
    }

    @Test
    fun printScenarios() {
        val days = (System.getProperty("balance.days") ?: "40").toInt()
        for (share in listOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6)) {
            println(play(share, days, seed = 42L))
        }
    }

    /** A stage-3 test tower gets a floor of hotel rooms and one cleaning service: what do they earn? */
    @Test
    fun printHotelFloor() {
        val sim = TestTower.build(3, seed = 42L)
        sim.eventsEnabled = false
        val floor = sim.tower.allRooms.maxOf { it.floor } + 1
        val shaft = sim.elevators.cars.values.filter { !it.express }.maxBy { it.top }
        val hotels = (0 until 9).mapNotNull { i -> sim.build(Catalog.HOTEL, floor, i * 6).room }
        sim.build(Catalog.HOUSEKEEPING, floor, 54).also { check(it.ok) { it.reason } }
        sim.build(sim.planColumn(Catalog.ELEVATOR, shaft.x, shaft.top, floor)).also { check(it.ok) { "Schacht: " + it.reason } }
        val out = StringBuilder("== Hotel-Etage: ${hotels.size} Zimmer, 1 Reinigungsdienst, ${sim.stars} Sterne ==\n")
        out.append("Tag | Hotel-Einnahmen | belegt um 23 Uhr | schmutzig um 23 Uhr\n")
        repeat(7 * 24) {
            sim.step(60.0)
            if (sim.clock.hour == 23) {
                out.append("${sim.clock.day} | ${sim.finance.day(sim.clock.day)[MoneyKind.HOTEL] ?: 0} | ${hotels.count { it.tenant != null }} | ${hotels.count { it.dirty }}\n")
            }
        }
        val upkeep = hotels.size * Simulation.HOTEL_UPKEEP + Simulation.HOUSEKEEPING_UPKEEP
        out.append("Bau ${hotels.size * Catalog.HOTEL.cost + Catalog.HOUSEKEEPING.cost} \$, Unterhalt $upkeep \$ pro Tag\n")
        println(out)
    }

    companion object {
        const val RESERVE = 5_000L
        const val SECOND_SHAFT_FROM = 8
    }
}
