package crazytower.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParkingTest {
    /** Four stars, stage 4, a lobby and [offices] rented offices on floor 1 (three cars each). */
    private fun tower(offices: Int = 0): Simulation {
        val sim = Simulation(startMoney = 50_000_000)
        sim.stage = 4
        sim.stars = 4
        sim.build(sim.planLobby(0, 63))
        repeat(offices) { i ->
            val office = sim.build(Catalog.OFFICE, 1, i * 6).room!!
            sim.moveIn(office, Tenants.candidates(Catalog.OFFICE, sim.stars, sim.stage).first(), announce = false)
        }
        return sim
    }

    @Test
    fun draggingBuildsOneSpaceForEveryCarThatLooksForOne() {
        val sim = tower(offices = 2) // six cars
        val plan = sim.planRow(Catalog.PARKING, -1, 0, 40)
        assertTrue(plan.check.ok, plan.check.reason)
        assertEquals(6, plan.pieces.size, "one space per car, however far the drag goes")
        assertEquals(listOf(0, 2, 4, 6, 8, 10), plan.pieces.map { it.x })
        assertEquals(6 * Catalog.PARKING.cost, plan.cost)
        assertTrue(sim.build(plan).ok)
        assertEquals(6, sim.parkingSpaces)
        assertEquals(0, sim.carsWithoutSpace)
        assertFalse(sim.planRow(Catalog.PARKING, -1, 20, 30).check.ok, "every car has its space")
    }

    @Test
    fun aDraggedRowStepsOverWhatIsBuilt() {
        val sim = tower(offices = 2)
        sim.build(Catalog.PARKING, -1, 2)
        val plan = sim.planRow(Catalog.PARKING, -1, 0, 9)
        assertEquals(listOf(0, 4, 6, 8), plan.pieces.map { it.x })
    }

    @Test
    fun luxuryOfficesNeedTheirOwnParkingSpaces() {
        val sim = tower()
        val luxury = sim.build(Catalog.LUXURY_OFFICE, 1, 0).room!!
        assertTrue(sim.build(sim.planColumn(Catalog.STAIRS, 4, GROUND_FLOOR, 1)).ok)
        assertFalse(sim.hasParking(luxury))
        assertEquals("No parking", RoomProblems.of(sim, luxury)?.title?.en)
        // an empty luxury office already wants its spaces, so they can be built in advance
        assertEquals(Simulation.LUXURY_SPACES, sim.carsWithoutSpace)
        assertTrue(sim.build(sim.planRow(Catalog.PARKING, -1, 0, 20)).ok)
        assertTrue(sim.hasParking(luxury))
        assertNull(RoomProblems.of(sim, luxury))
        assertEquals(0, sim.parkedCars, "kept spaces earn nothing until somebody moves in")
        sim.moveIn(luxury, Tenants.candidates(Catalog.LUXURY_OFFICE, sim.stars, sim.stage).first(), announce = false)
        assertEquals(Simulation.LUXURY_SPACES, sim.parkedCars)
    }

    @Test
    fun luxuryTenantsWithoutParkingPayHalf() {
        val sim = tower()
        val luxury = sim.build(Catalog.LUXURY_OFFICE, 1, 0).room!!
        val tenant = Tenants.candidates(Catalog.LUXURY_OFFICE, sim.stars, sim.stage).first()
        sim.moveIn(luxury, tenant, announce = false)
        val full = (tenant.rentPerDay * sim.starBonus).toLong()
        assertEquals(full / 2, sim.upcoming(200).first { it.label == tenant.name.toString() }.amount)
        sim.build(sim.planRow(Catalog.PARKING, -1, 0, 20))
        assertEquals(full, sim.upcoming(200).first { it.label == tenant.name.toString() }.amount)
    }

    @Test
    fun luxuryOfficesGetTheirSpacesBeforeEverybodyElse() {
        val sim = tower(offices = 2)
        sim.build(sim.planRow(Catalog.PARKING, -1, 0, 20))
        val luxury = sim.build(Catalog.LUXURY_OFFICE, 1, 12).room!!
        assertTrue(sim.hasParking(luxury), "four of the offices' six spaces go to the luxury office")
        assertEquals(2, sim.parkedCars, "and the spaces it keeps earn nothing yet")
    }

    @Test
    fun expressElevatorsStopOnEveryBasementFloor() {
        val sim = tower()
        assertTrue(sim.build(sim.planColumn(Catalog.EXPRESS, 30, GROUND_FLOOR, Tower.MIN_FLOOR)).ok)
        assertTrue(sim.build(sim.planColumn(Catalog.EXPRESS, 30, GROUND_FLOOR, 5)).ok)
        val car = sim.elevators.cars.values.single()
        assertEquals(listOf(-3, -2, -1, 0, 5), car.stops)
    }

    @Test
    fun oldCarParksBecomeSingleSpaces() {
        val sim = tower(offices = 1)
        val save = sim.toSave().let { it.copy(rooms = it.rooms + SavedRoom(999, Catalog.OLD_CAR_PARK, -1, 6, 0.0)) }
        val loaded = Simulation.load(save)
        val spaces = loaded.tower.allRooms.filter { it.type == Catalog.PARKING }.sortedBy { it.x }
        assertEquals(listOf(6, 8, 10), spaces.map { it.x })
    }
}
