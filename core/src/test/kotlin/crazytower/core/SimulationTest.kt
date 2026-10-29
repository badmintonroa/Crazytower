package crazytower.core

import crazytower.core.ecs.Person
import crazytower.core.ecs.Position
import crazytower.core.ecs.Stuck
import crazytower.core.ecs.Visitor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimulationTest {
    @Test
    fun buildingCostsMoney() {
        val sim = Simulation()
        val before = sim.money
        assertTrue(sim.build(Catalog.LOBBY, 0, 10).ok)
        assertEquals(before - Catalog.LOBBY.price(), sim.money)
    }

    @Test
    fun cannotBuildWithoutMoney() {
        val sim = Simulation(startMoney = 100)
        assertEquals("Zu wenig Geld", sim.build(Catalog.LOBBY, 0, 10).reason)
    }

    @Test
    fun visitorsComeWalkAroundAndLeave() {
        val sim = Simulation(seed = 7)
        sim.build(Catalog.LOBBY, 0, 20)
        sim.build(Catalog.LOBBY, 0, 26)
        val people = sim.world.family { all(Person, Position) }
        var maxSeen = 0
        repeat(6 * 60) {
            sim.step(1.0)
            maxSeen = maxOf(maxSeen, people.numEntities)
            people.forEach { e ->
                val pos = e[Position]
                assertEquals(0, pos.floor)
                assertTrue(pos.x >= 0f && pos.x < sim.tower.width, "x=${pos.x}")
                assertFalse(e has Stuck)
            }
        }
        // an empty tower only draws a few curious people
        assertTrue(maxSeen in 1..crazytower.core.ecs.VisitorSpawnSystem.BASE_VISITORS, "a few visitors should arrive, saw $maxSeen")
        // After closing time everybody has gone home.
        sim.step(16 * 60.0)
        assertEquals(0, sim.world.family { all(Visitor) }.numEntities)
    }

    @Test
    fun visitorsGrowWithRentedRooms() {
        val sim = Simulation(seed = 7, startMoney = 10_000_000)
        sim.eventsEnabled = false
        sim.build(sim.planLobby(10, 33))
        assertEquals(crazytower.core.ecs.VisitorSpawnSystem.BASE_VISITORS, crazytower.core.ecs.VisitorSpawnSystem.maxVisitors(sim, 4))
        repeat(3) { i ->
            val room = sim.buildReachable(Catalog.OFFICE, 10 + i * 6)
            sim.moveIn(room, Tenants.byId("accountant"), announce = false)
        }
        assertEquals(crazytower.core.ecs.VisitorSpawnSystem.BASE_VISITORS + 3 * crazytower.core.ecs.VisitorSpawnSystem.VISITORS_PER_TENANT,
            crazytower.core.ecs.VisitorSpawnSystem.maxVisitors(sim, 4))
    }
}
