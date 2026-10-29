package crazytower.core

import crazytower.core.transport.Elevators
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestTowerTest {
    @Test
    fun everyStageHasAFullTowerThatRuns() {
        for (stage in 1..Stages.last) {
            val sim = TestTower.build(stage)
            assertEquals(stage, sim.stage)
            assertEquals(stage, sim.stars)
            assertEquals(TestTower.MONEY, sim.money)
            val floors = TestTower.FLOORS[stage]
            assertTrue(sim.tower.allRooms.any { it.floor == floors }, "stage $stage reaches floor $floors")
            val shafts = sim.elevators.cars.values
            assertTrue(shafts.isNotEmpty() && shafts.all { it.cabins.size == Elevators.maxCabins(stage) }, "stage $stage cabins")
            assertTrue(sim.population > 30 * floors, "stage $stage: ${sim.population} people")
            sim.step(60.0)
            assertEquals(stage, sim.stage, "no step back")
        }
    }

    @Test
    fun theMaximumTowerIsFullAndKeepsRunning() {
        val sim = TestTower.buildMaximum()
        val top = Stages.info(Stages.last).maxFloor - 1
        assertTrue(sim.tower.allRooms.any { it.floor == top }, "up to the top floor")
        assertTrue(sim.tower.allRooms.any { it.floor == Tower.MIN_FLOOR }, "down to the deepest basement")
        for (type in listOf(Catalog.COPYROOM, Catalog.SECURITY, Catalog.TELEPORTER, Catalog.UFO_PAD, Catalog.PARKING, Catalog.EXPRESS, Catalog.FOOD)) {
            assertTrue(sim.tower.allRooms.any { it.type == type }, "has ${type.name}")
        }
        val rentable = sim.tower.allRooms.filter { it.type.capacity > 0 }
        assertTrue(rentable.all { it.tenant != null }, "every room is rented")
        assertTrue(sim.population > 8_000, "${sim.population} people")
        val started = System.nanoTime()
        sim.step(60.0)
        println("Maximum: ${sim.population} people, ${sim.tower.allRooms.size} rooms, one hour in ${(System.nanoTime() - started) / 1_000_000} ms")
        assertEquals(5, sim.stars)
    }
}
