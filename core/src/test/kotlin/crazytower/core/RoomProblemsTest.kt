package crazytower.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomProblemsTest {
    private fun sim() = officeSim(seed = 11, startMoney = 50_000_000).also { it.eventsEnabled = false }

    @Test
    fun reachableRoomsHaveNoProblem() {
        val sim = sim()
        val office = sim.buildReachable(Catalog.OFFICE, 10)
        sim.step(8 * 60.0)
        assertNotNull(office.tenant)
        assertTrue(sim.roomProblems.isEmpty(), "${sim.roomProblems}")
    }

    @Test
    fun roomWithoutWayGetsAnAngryFace() {
        val sim = sim()
        sim.build(sim.planLobby(20, 40))
        val home = sim.build(Catalog.HOME, 1, 20).room!!
        sim.step(3 * 60.0)
        assertNull(home.tenant)
        val problem = assertNotNull(sim.roomProblems[home.id])
        assertEquals("Kein Weg hierher", problem.title.de)
        assertTrue(problem.far)
    }

    @Test
    fun tooManyStairsAreNamed() {
        val sim = sim()
        sim.stage = 2 // taller than the building site allows at first
        val office = sim.buildReachable(Catalog.OFFICE, 10, floor = 7)
        sim.step(3 * 60.0)
        assertNull(office.tenant)
        assertEquals("Zu viele Treppen", sim.roomProblems[office.id]?.title?.de)
    }

    @Test
    fun longWaysFindNoTenants() {
        val sim = sim()
        val width = sim.lotWidth
        sim.build(sim.planLobby(0, width - 1))
        // the only stairs are at the far left, the office at the far right of the first floor
        val office = sim.build(Catalog.OFFICE, 1, (width - Catalog.OFFICE.width) / 6 * 6).room!!
        for (x in 0 until office.x step Catalog.CORRIDOR.width) check(sim.build(Catalog.CORRIDOR, 1, x).ok)
        check(sim.build(sim.planColumn(Catalog.STAIRS, 0, GROUND_FLOOR, 1)).ok)
        val minutes = assertNotNull(sim.nav.travelMinutes(sim.streetExit(office.centerX), office.center))
        assertTrue(minutes > RoomProblems.MAX_TRAVEL_MINUTES, "the way takes $minutes minutes")
        sim.step(8 * 60.0)
        assertNull(office.tenant)
        val problem = assertNotNull(sim.roomProblems[office.id])
        assertEquals("Zu weit weg", problem.title.de)
        assertTrue(problem.far)
    }
}
