package crazytower.core

import crazytower.core.nav.Leg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildDragTest {
    @Test
    fun groundFloorIsOnlyForTheLobby() {
        val sim = Simulation(startMoney = 10_000_000)
        assertEquals("Im Erdgeschoss ist nur Platz für die Lobby", sim.checkPlace(Catalog.OFFICE, GROUND_FLOOR, 10).reason)
        assertTrue(sim.checkPlace(Catalog.LOBBY, GROUND_FLOOR, 10).ok)
        assertTrue(sim.checkPlace(Catalog.ELEVATOR, GROUND_FLOOR, 10).ok)
        assertTrue(sim.checkPlace(Catalog.STAIRS, GROUND_FLOOR, 10).ok)
        assertTrue(sim.checkPlace(Catalog.OFFICE, -1, 10).ok, "the basement is free for anything")
    }

    @Test
    fun aDraggedLobbyCostsPerSegmentAndJoinsTheOldOne() {
        val sim = Simulation(startMoney = 1_000_000)
        assertTrue(sim.build(sim.planLobby(10, 15)).ok)
        val before = sim.money
        val plan = sim.planLobby(12, 29) // half over the old lobby
        assertEquals(listOf(BuildPlan.Piece(GROUND_FLOOR, 16, 14)), plan.pieces)
        assertEquals(Catalog.LOBBY.cost * 14, plan.cost)
        assertTrue(sim.build(plan).ok)
        assertEquals(before - plan.cost, sim.money)
        val lobbies = sim.tower.allRooms.filter { it.type.role == RoomRole.LOBBY }
        assertEquals(1, lobbies.size)
        assertEquals(10 to 20, lobbies.single().x to lobbies.single().width)
        assertFalse(sim.planLobby(11, 14).check.ok, "nothing new to build")
    }

    @Test
    fun theLobbyCannotBeDraggedThroughOtherRooms() {
        val sim = Simulation(startMoney = 1_000_000)
        sim.build(Catalog.LOBBY, GROUND_FLOOR, 0, 4)
        sim.build(Catalog.LOBBY, GROUND_FLOOR, 10, 4)
        // a shaft in between is fine, it is on its own layer
        assertTrue(sim.build(Catalog.ELEVATOR, GROUND_FLOOR, 6).ok)
        val plan = sim.planLobby(0, 13)
        assertTrue(plan.check.ok, plan.check.reason)
        assertTrue(sim.build(plan).ok)
        assertEquals(14, sim.tower.roomAt(GROUND_FLOOR, 5)!!.width)
    }

    @Test
    fun anElevatorIsDraggedUpThroughTheRooms() {
        val sim = Simulation(seed = 3, startMoney = 10_000_000)
        sim.build(sim.planLobby(10, 21))
        val office = sim.build(Catalog.OFFICE, 1, 10).room!!
        val home = sim.build(Catalog.HOME, 2, 10).room!!
        sim.moveIn(office, Tenants.byId("accountant"))
        val plan = sim.planColumn(Catalog.ELEVATOR, 12, GROUND_FLOOR, 4)
        assertEquals(listOf(0, 1, 2, 3, 4), plan.pieces.map { it.floor })
        assertEquals(Catalog.ELEVATOR.cost * 5, plan.cost)
        assertTrue(sim.build(plan).ok, plan.check.reason)
        assertEquals(0..4, sim.elevators.cars.values.single().floors)
        // the rooms behind the shaft are still there and still rented
        assertEquals(office, sim.tower.roomAt(1, 12))
        assertEquals(home, sim.tower.roomAt(2, 13))
        assertEquals("accountant", office.tenant?.id)
        assertEquals(Catalog.ELEVATOR, sim.tower.topAt(1, 12)?.type)
        // and they can be reached through it
        val path = assertNotNull(sim.nav.findPath(Loc(GROUND_FLOOR, 1f), Loc(2, 15f)))
        assertEquals(1, path.count { it is Leg.Ride })
    }

    @Test
    fun grabbingTheEndOfAShaftExtendsAndShortensIt() {
        val sim = Simulation(startMoney = 10_000_000)
        sim.build(sim.planLobby(10, 15))
        sim.build(sim.planColumn(Catalog.ELEVATOR, 10, GROUND_FLOOR, 3))
        val longer = sim.planColumn(Catalog.ELEVATOR, 10, 3, 6)
        assertEquals(listOf(4, 5, 6), longer.pieces.map { it.floor }, "existing floors are skipped")
        assertTrue(sim.build(longer).ok)
        assertEquals(0..6, sim.elevators.cars.values.single().floors)

        val top = sim.tower.wayAt(6, 10)!!
        val removed = sim.planRetract(top, 4)
        assertEquals(listOf(6, 5), removed.map { it.floor })
        sim.demolishAll(removed)
        assertEquals(0..4, sim.elevators.cars.values.single().floors)
        assertTrue(sim.planRetract(sim.tower.wayAt(2, 10)!!, 0).isEmpty(), "only the ends can be moved")
        assertEquals(4, sim.planRetract(sim.tower.wayAt(4, 10)!!, -5).size, "one module always stays")
    }

    @Test
    fun aShaftCannotBeTallerThanThirtyFloors() {
        // checked on the tower: stage 1 stops at ten floors anyway
        val tower = Tower()
        val planned = (0 until Tower.MAX_SHAFT_FLOORS).toSet()
        assertTrue(tower.canPlace(Catalog.ELEVATOR, Tower.MAX_SHAFT_FLOORS - 1, 10, assumed = planned - (Tower.MAX_SHAFT_FLOORS - 1)).ok)
        assertEquals(
            "Aufzug höchstens ${Tower.MAX_SHAFT_FLOORS} Etagen",
            tower.canPlace(Catalog.ELEVATOR, Tower.MAX_SHAFT_FLOORS, 10, assumed = planned).reason,
        )
    }

    @Test
    fun draggedStairsLeadUpToTheFloorWhereTheDragEnds() {
        val sim = Simulation(startMoney = 10_000_000)
        sim.build(sim.planLobby(10, 15))
        for (f in 1..3) sim.build(Catalog.CORRIDOR, f, 10)
        val plan = sim.planColumn(Catalog.STAIRS, 12, 3, GROUND_FLOOR)
        assertEquals(listOf(0, 1, 2), plan.pieces.map { it.floor })
        assertTrue(sim.build(plan).ok)
        val path = assertNotNull(sim.nav.findPath(Loc(GROUND_FLOOR, 1f), Loc(3, 11f)))
        assertEquals(3, path.count { it is Leg.Stairs })
    }

    @Test
    fun aRoomWithAShaftThroughItCanBeTornDown() {
        val sim = Simulation(startMoney = 10_000_000)
        sim.build(sim.planLobby(10, 15))
        val office = sim.build(Catalog.OFFICE, 1, 10).room!!
        sim.build(sim.planColumn(Catalog.ELEVATOR, 10, GROUND_FLOOR, 2))
        // the shaft module above rests on the shaft, not on the office
        assertTrue(sim.checkDemolish(office).ok)
        sim.demolish(office.id)
        assertEquals(0..2, sim.elevators.cars.values.single().floors)
    }

    @Test
    fun aStretchedLobbySurvivesSaving() {
        val sim = Simulation(startMoney = 1_000_000)
        sim.build(sim.planLobby(3, 25))
        val loaded = Simulation.load(SaveGame.fromJson(sim.toSave().toJson()))
        assertEquals(3 to 23, loaded.tower.roomAt(GROUND_FLOOR, 3)!!.let { it.x to it.width })
    }

    @Test
    fun oldSavesWithRoomsOnTheGroundFloorStillLoad() {
        val sim = Simulation(startMoney = 1_000_000)
        val office = TowerTestAccess.forcePlace(sim.tower, Catalog.OFFICE, GROUND_FLOOR, 10)
        val loaded = Simulation.load(SaveGame.fromJson(sim.toSave().toJson()))
        assertEquals(Catalog.OFFICE, loaded.tower.room(office.id)?.type)
    }
}
