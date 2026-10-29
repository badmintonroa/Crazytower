package crazytower.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LotTest {
    @Test
    fun theLotGrowsWithTheStars() {
        val sim = Simulation(startMoney = 10_000_000)
        assertEquals(64, sim.lotWidth)
        assertTrue(sim.checkPlace(Catalog.LOBBY, GROUND_FLOOR, 58).ok)
        assertEquals("Grundstück wird erst ab 3 Sternen breiter", sim.checkPlace(Catalog.LOBBY, GROUND_FLOOR, 60).reason)
        assertEquals(63, sim.planLobby(0, 200).pieces.single().let { it.x + it.width - 1 }, "a drag stops at the lot edge")
        sim.stars = 3
        assertEquals(96, sim.lotWidth)
        assertTrue(sim.checkPlace(Catalog.LOBBY, GROUND_FLOOR, 60).ok)
        sim.stars = 5
        assertEquals(Lot.maxWidth, sim.lotWidth)
        assertEquals(sim.tower.width, sim.lotWidth)
        assertEquals("Außerhalb des Grundstücks", sim.checkPlace(Catalog.LOBBY, GROUND_FLOOR, Lot.maxWidth - 2).reason)
    }

    @Test
    fun theBasementOpensAtFourStars() {
        val sim = Simulation(startMoney = 10_000_000)
        sim.stars = 3
        assertEquals("Keller erst ab 4 Sternen", sim.checkPlace(Catalog.OFFICE, -1, 10).reason)
        assertFalse(sim.planColumn(Catalog.ELEVATOR, 10, 0, -2).check.ok)
        sim.stars = 4
        assertTrue(sim.checkPlace(Catalog.OFFICE, -1, 10).ok)
        assertTrue(sim.planColumn(Catalog.ELEVATOR, 10, 0, -1).check.ok)
    }

    @Test
    fun aCarParkOnlyGoesUndergroundAndEarnsFees() {
        val sim = Simulation(startMoney = 10_000_000)
        assertEquals("Erst ab 4 Sternen", sim.checkPlace(Catalog.PARKING, -1, 10).reason)
        sim.stars = 4
        assertEquals("Parkhaus nur im Keller", sim.checkPlace(Catalog.PARKING, 1, 10).reason)
        assertEquals("Noch 20 Autos zu wenig für ein weiteres Parkhaus. Mehr Büros und Wohnungen!", sim.checkPlace(Catalog.PARKING, -1, 10).reason)
        rentOffices(sim, 7) // 21 cars
        assertTrue(sim.build(Catalog.PARKING, -1, 10).ok)
        assertEquals(21, sim.parkedCars)
        assertEquals((21 * Simulation.PARKING_FEE * sim.starBonus).toLong(), sim.parkingFees())
        assertFalse(sim.checkPlace(Catalog.PARKING, -1, 16).ok, "the first car park is far from full")
    }

    private fun rentOffices(sim: Simulation, count: Int) {
        sim.build(sim.planLobby(0, 6 * count))
        repeat(count) { i ->
            val office = sim.build(Catalog.OFFICE, 1, i * 6).room!!
            sim.moveIn(office, Tenants.candidates(RoomRole.OFFICE, sim.stars, sim.stage).first(), announce = false)
        }
    }

    @Test
    fun specialRoomsExistOnlyOnce() {
        val sim = Simulation(startMoney = 100_000_000)
        sim.stage = 5
        sim.build(sim.planLobby(0, 40))
        assertTrue(sim.build(Catalog.COPYROOM, 1, 0).ok)
        assertEquals("Kopierraum der Ewigkeit gibt es schon, nur einmal pro Turm", sim.checkPlace(Catalog.COPYROOM, 1, 6).reason)
        assertTrue(sim.build(Catalog.TELEPORTER, 1, 12).ok)
        assertTrue(sim.build(Catalog.TELEPORTER, 1, 20).ok)
        assertFalse(sim.checkPlace(Catalog.TELEPORTER, 1, 30).ok, "teleporters come as one pair")
        assertTrue(Catalog.roomTypes.filter { it.category == Category.SPECIAL }.all { it.effects.isNotEmpty() }, "every special says what it does")
    }

    @Test
    fun theGapFillerClosesTheWholeGap() {
        val sim = Simulation(startMoney = 10_000_000)
        sim.build(sim.planLobby(0, 40))
        assertTrue(sim.build(Catalog.OFFICE, 1, 0).ok)
        assertTrue(sim.build(Catalog.OFFICE, 1, 9).ok) // three segments left free by mistake
        val plan = sim.planGap(1, 7)
        assertTrue(plan.check.ok, plan.check.reason)
        assertEquals(listOf(BuildPlan.Piece(1, 6, 3)), plan.pieces)
        assertEquals(Catalog.FILLER.cost * 3, plan.cost)
        assertTrue(sim.build(plan).ok)
        assertTrue((0 until 15).all { sim.tower.isWalkable(1, it) })
        assertEquals("Hier ist keine Lücke", sim.planGap(1, 7).check.reason)
        assertEquals("Zu große Lücke, hier passt ein ganzer Raum", sim.planGap(1, 20).check.reason)
    }
}
