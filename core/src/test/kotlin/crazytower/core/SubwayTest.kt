package crazytower.core

import crazytower.core.ecs.Away
import crazytower.core.ecs.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubwayTest {
    /** An office on floor 1 above a lobby, and a subway station under it; [linked] with stairs down. */
    private fun tower(linked: Boolean): Pair<Simulation, Room> {
        val sim = officeSim(seed = 4, startMoney = 50_000_000)
        sim.eventsEnabled = false
        sim.stage = 3
        sim.stars = 3
        val office = sim.buildReachable(Catalog.OFFICE, 20)
        sim.tower.placeUnchecked(Catalog.SUBWAY, -1, 14)
        if (linked) sim.build(sim.planColumn(Catalog.STAIRS, 22, -1, GROUND_FLOOR)).let { assertTrue(it.ok, it.reason) }
        sim.moveIn(office, Tenants.byId("accountant"), announce = false)
        return sim to office
    }

    /** Steps through one day and counts the staff seen in the basement while they are not away. */
    private fun seenUnderground(sim: Simulation, office: Room): Int {
        val staff = sim.membersOf(office.id)
        val seen = HashSet<Int>()
        repeat(24 * 12) {
            sim.step(5.0)
            with(sim.world) { staff.filter { it hasNo Away && it[Position].floor == -1 }.forEach { seen += it.id } }
        }
        return seen.size
    }

    @Test
    fun commutersComeOutOfTheSubwayStation() {
        val (sim, office) = tower(linked = true)
        assertTrue(seenUnderground(sim, office) > 0, "some of the staff come and go by train")
    }

    @Test
    fun withoutAWayDownEverybodyTakesTheStreet() {
        val (sim, office) = tower(linked = false)
        assertEquals(0, seenUnderground(sim, office))
    }
}
