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
}
