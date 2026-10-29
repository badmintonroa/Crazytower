package crazytower.core

import crazytower.core.ecs.Goal
import crazytower.core.ecs.Person
import crazytower.core.ecs.Position
import crazytower.core.ecs.Riding
import crazytower.core.ecs.Stuck
import crazytower.core.nav.Leg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransportTest {
    private fun walker(sim: Simulation, target: Loc) = sim.world.entity {
        it += Position(0, 1f)
        it += Person("Test", "casual")
        it += Goal(target)
    }

    @Test
    fun upperFloorsNeedSomethingBelow() {
        val sim = Simulation(startMoney = 10_000_000)
        assertEquals("Darunter fehlt der Unterbau", sim.build(Catalog.OFFICE, 1, 10).reason)
        sim.build(Catalog.LOBBY, 0, 10)
        assertTrue(sim.build(Catalog.OFFICE, 1, 10).ok)
        assertFalse(sim.build(Catalog.OFFICE, 2, 13).ok)
        sim.stars = Lot.BASEMENT_STARS
        assertTrue(sim.build(Catalog.OFFICE, -1, 30).ok, "the first basement can be dug anywhere")
        assertFalse(sim.build(Catalog.OFFICE, -2, 40).ok)
    }

    @Test
    fun cannotDemolishWhatCarriesARoom() {
        val sim = Simulation(startMoney = 10_000_000)
        val lobby = sim.build(Catalog.LOBBY, 0, 10).room!!
        sim.build(Catalog.OFFICE, 1, 10)
        assertFalse(sim.checkDemolish(lobby).ok)
        assertEquals(0, sim.demolish(lobby.id))
        assertNotNull(sim.tower.room(lobby.id))
    }

    @Test
    fun stairsLeadOneFloorUp() {
        val sim = Simulation(startMoney = 10_000_000)
        sim.build(Catalog.LOBBY, 0, 10)
        assertTrue(sim.build(Catalog.STAIRS, 0, 16).ok)
        sim.build(Catalog.LOBBY, 0, 18)
        sim.build(Catalog.CORRIDOR, 1, 10)
        sim.build(Catalog.CORRIDOR, 1, 16)
        val path = sim.nav.findPath(Loc(0, 2f), Loc(1, 12f))
        assertNotNull(path)
        assertEquals(1, path.count { it is Leg.Stairs })
    }

    private fun tallCorridor(sim: Simulation, top: Int) {
        sim.build(Catalog.LOBBY, 0, 10)
        for (f in 1..top) assertTrue(sim.build(Catalog.CORRIDOR, f, 10).ok, "corridor $f")
    }

    @Test
    fun nobodyClimbsMoreThanFiveFloorsOnFoot() {
        val sim = Simulation(startMoney = 10_000_000)
        sim.stage = 1
        tallCorridor(sim, 8)
        assertTrue(sim.build(sim.planColumn(Catalog.STAIRS, 12, GROUND_FLOOR, 8)).ok)
        assertEquals(5, sim.nav.findPath(Loc(0, 2f), Loc(5, 14f))?.count { it is Leg.Stairs })
        assertEquals(null, sim.nav.findPath(Loc(0, 2f), Loc(8, 14f)))
    }

    @Test
    fun theLastFloorsAfterTheElevatorAreWalked() {
        val sim = Simulation(startMoney = 10_000_000)
        sim.stage = 1
        tallCorridor(sim, 9)
        for (f in 0..6) assertTrue(sim.build(Catalog.ELEVATOR, f, 16).ok, "elevator $f")
        assertTrue(sim.build(sim.planColumn(Catalog.STAIRS, 12, 6, 9)).ok)
        val path = sim.nav.findPath(Loc(0, 2f), Loc(9, 14f))
        assertNotNull(path)
        assertEquals(1, path.count { it is Leg.Ride })
        assertEquals(3, path.count { it is Leg.Stairs })
    }

    @Test
    fun expressElevatorsOnlyStopOnEveryFifthFloor() {
        val sim = Simulation(startMoney = 10_000_000)
        sim.stage = 2
        tallCorridor(sim, 10)
        assertTrue(sim.build(sim.planColumn(Catalog.EXPRESS, 16, GROUND_FLOOR, 10)).ok)
        val car = sim.elevators.cars.values.single()
        assertTrue(car.express)
        assertEquals(listOf(0, 5, 10), car.stops)
        assertEquals(listOf(Leg.Ride(car.id, 0, 10, car.centerX)), sim.nav.findPath(Loc(0, 17f), Loc(10, 17f)))
        // floor 7 has no stop: ride to 5, walk the rest
        assertTrue(sim.build(sim.planColumn(Catalog.STAIRS, 12, 5, 7)).ok)
        val path = assertNotNull(sim.nav.findPath(Loc(0, 17f), Loc(7, 14f)))
        assertEquals(Leg.Ride(car.id, 0, 5, car.centerX), path.first())
        assertEquals(2, path.count { it is Leg.Stairs })
    }

    @Test
    fun expressAndNormalShaftsDoNotMix() {
        val sim = Simulation(startMoney = 10_000_000)
        sim.stage = 2
        tallCorridor(sim, 6)
        assertTrue(sim.build(sim.planColumn(Catalog.ELEVATOR, 16, GROUND_FLOOR, 3)).ok)
        assertFalse(sim.checkPlace(Catalog.EXPRESS, 4, 16).ok)
    }

    @Test
    fun skyLobbyEvery25FloorsBeforeBuildingHigher() {
        val sim = Simulation(startMoney = 100_000_000)
        sim.stage = 3
        tallCorridor(sim, 24)
        sim.build(Catalog.LOBBY, 0, 16)
        for (f in 1..24) assertTrue(sim.build(Catalog.CORRIDOR, f, 16).ok)
        assertFalse(sim.checkPlace(Catalog.LOBBY, 10, 10).ok, "no sky lobby on floor 10")
        assertTrue(sim.build(Catalog.CORRIDOR, 25, 10).ok, "floor 25 itself may be built on")
        sim.demolish(sim.tower.roomAt(25, 10)!!.id)
        assertTrue(sim.build(sim.planLobby(10, 15, 25)).ok)
        assertTrue(sim.build(sim.planLobby(16, 21, 25)).ok)
        val sky = sim.tower.allRooms.filter { it.type.role == RoomRole.LOBBY && it.floor == 25 }
        assertEquals(1, sky.size, "touching sky lobby pieces are joined")
        assertEquals(12, sky.single().width)
        assertTrue(sim.build(Catalog.CORRIDOR, 26, 10).ok)
    }

    @Test
    fun nothingAboveAMissingSkyLobby() {
        val sim = Simulation(startMoney = 100_000_000)
        sim.stage = 3
        tallCorridor(sim, 25)
        assertEquals("Erst eine Sky-Lobby auf der 25. Etage bauen", sim.checkPlace(Catalog.CORRIDOR, 26, 10).reason)
        assertFalse(sim.planColumn(Catalog.ELEVATOR, 16, 20, 27).check.ok)
    }

    @Test
    fun everyFurtherShaftCostsMore() {
        val sim = Simulation(startMoney = 10_000_000)
        assertTrue(sim.build(sim.planLobby(0, 59)).ok)
        assertEquals(6_000, sim.priceAt(Catalog.ELEVATOR, GROUND_FLOOR, 10))
        assertTrue(sim.build(Catalog.ELEVATOR, GROUND_FLOOR, 10).ok)
        assertEquals(6_000, sim.priceAt(Catalog.ELEVATOR, 1, 10), "growing a shaft stays at the normal price")
        assertEquals(9_000, sim.priceAt(Catalog.ELEVATOR, GROUND_FLOOR, 30))
        val before = sim.money
        assertTrue(sim.build(Catalog.ELEVATOR, GROUND_FLOOR, 30).ok)
        assertEquals(9_000, before - sim.money)
        assertEquals(12_000, sim.priceAt(Catalog.ELEVATOR, GROUND_FLOOR, 50))
    }

    @Test
    fun theTowerCanChangeWithMoreThan2048People() {
        // Fleks keeps 2048 slots per component type; removing a component somebody beyond that
        // never had used to throw while routes were refreshed, and the elevator crashed after it
        val sim = Simulation(seed = 5, startMoney = 100_000_000)
        sim.stage = 1
        tallCorridor(sim, 8)
        assertTrue(sim.build(sim.planColumn(Catalog.ELEVATOR, 16, GROUND_FLOOR, 8)).ok)
        // somebody gets stuck early, so the storage for being stuck exists before the crowd comes
        walker(sim, Loc(20, 12f))
        sim.step(1.0)
        val people = (0 until 2100).map { walker(sim, Loc(1 + it % 8, 12f)) }
        repeat(5) { sim.step(1.0) }
        assertTrue(sim.build(sim.planLobby(30, 31)).ok, "extending the lobby makes everybody plan again")
        repeat(30) { sim.step(1.0) }
        with(sim.world) { assertTrue(people.last() has Position) }
    }

    @Test
    fun buildingKeepsRoutesAndRemovingReplansAFewAtATime() {
        // with thousands of people, planning everybody again in one tick froze the game
        val sim = Simulation(seed = 5, startMoney = 100_000_000)
        sim.stage = 1
        tallCorridor(sim, 8)
        assertTrue(sim.build(sim.planColumn(Catalog.ELEVATOR, 16, GROUND_FLOOR, 8)).ok)
        assertTrue(sim.build(sim.planLobby(30, 35)).ok)
        assertTrue(sim.build(Catalog.CORRIDOR, 1, 30).ok)
        val people = (0 until 900).map { walker(sim, Loc(8, 12f)) }
        sim.step(0.3)
        fun routes() = with(sim.world) { people.filter { it has crazytower.core.ecs.Route && it hasNo Riding }.associateWith { it[crazytower.core.ecs.Route] } }
        fun replanned(before: Map<com.github.quillraven.fleks.Entity, crazytower.core.ecs.Route>) =
            with(sim.world) { before.count { (e, r) -> e has crazytower.core.ecs.Route && e[crazytower.core.ecs.Route] !== r } }

        val before = routes()
        assertTrue(before.size > 500)
        assertTrue(sim.build(sim.planLobby(40, 45)).ok)
        sim.step(0.1)
        assertEquals(0, replanned(before), "a new room leaves the routes alone")

        val beforeRemoval = routes()
        sim.demolish(sim.tower.roomAt(1, 30)!!.id)
        sim.step(0.1)
        val firstTick = replanned(beforeRemoval)
        assertTrue(firstTick in 1..crazytower.core.ecs.RouteRefreshSystem.REPLAN_PER_TICK, "$firstTick replanned in the first tick")
        repeat(10) { sim.step(0.1) }
        assertTrue(replanned(beforeRemoval) > crazytower.core.ecs.RouteRefreshSystem.REPLAN_PER_TICK, "the rest follow over the next ticks")
    }

    @Test
    fun peopleRideTheElevatorToTheTop() {
        val sim = Simulation(seed = 5, startMoney = 10_000_000)
        sim.stage = 1
        sim.build(Catalog.LOBBY, 0, 10)
        assertTrue(sim.build(Catalog.ELEVATOR, 0, 16).ok)
        for (f in 1..8) {
            assertTrue(sim.build(Catalog.CORRIDOR, f, 10).ok)
            assertTrue(sim.build(Catalog.ELEVATOR, f, 16).ok, "elevator $f")
        }
        assertEquals(1, sim.elevators.cars.size)
        val people = (0 until 12).map { walker(sim, Loc(8, 12f)) }
        var rode = false
        repeat(120) {
            sim.step(1.0)
            people.forEach { e -> with(sim.world) { if (e has Riding) rode = true } }
        }
        assertTrue(rode, "somebody should have used the elevator")
        with(sim.world) {
            people.forEach { e ->
                assertFalse(e has Stuck)
                assertFalse(e has Goal, "still on the way at ${e[Position]}")
                assertEquals(8, e[Position].floor)
            }
        }
    }

    @Test
    fun shaftsAreLimitedInHeight() {
        val tower = Tower()
        tower.place(Catalog.ELEVATOR, 0, 0)
        for (f in 1 until Tower.MAX_SHAFT_FLOORS) tower.place(Catalog.ELEVATOR, f, 0)
        assertEquals("Aufzug höchstens ${Tower.MAX_SHAFT_FLOORS} Etagen", tower.canPlace(Catalog.ELEVATOR, Tower.MAX_SHAFT_FLOORS, 0).reason)
    }

    private fun shaft(sim: Simulation, floors: Int) {
        sim.stage = 1 // taller than the building site allows
        sim.build(Catalog.LOBBY, 0, 10)
        sim.build(Catalog.ELEVATOR, 0, 16)
        for (f in 1..floors) {
            sim.build(Catalog.CORRIDOR, f, 10)
            sim.build(Catalog.ELEVATOR, f, 16)
        }
    }

    @Test
    fun moreCabinsComeWithStars() {
        val sim = Simulation(seed = 5, startMoney = 10_000_000)
        shaft(sim, 6)
        val car = sim.elevators.cars.values.single()
        assertEquals(1, car.cabins.size)
        assertTrue(sim.addCabin(car).ok)
        assertFalse(sim.addCabin(car).ok, "no stars yet: two cabins")
        sim.stars = 3
        val before = sim.money
        repeat(4) { assertTrue(sim.addCabin(car).ok) }
        assertFalse(sim.addCabin(car).ok, "three stars: six cabins")
        assertEquals(6, car.cabins.size)
        assertEquals(before - 4 * crazytower.core.transport.Elevators.CABIN_COST, sim.money)
        // the shaft remembers its cabins in the save game
        val loaded = Simulation.load(SaveGame.fromJson(sim.toSave().toJson()), seed = 1)
        assertEquals(6, loaded.elevators.cars.values.single().cabins.size)
        assertTrue(sim.removeCabin(car))
        assertEquals(5, car.cabins.size)
    }

    @Test
    fun severalCabinsCarryACrowdFaster() {
        fun minutesFor(cabins: Int): Int {
            val sim = Simulation(seed = 5, startMoney = 10_000_000)
            sim.eventsEnabled = false
            sim.stars = 5
            shaft(sim, 8)
            val car = sim.elevators.cars.values.single()
            repeat(cabins - 1) { assertTrue(sim.addCabin(car).ok) }
            val people = (0 until 40).map { i -> walker(sim, Loc(1 + i % 8, 12f)) }
            var minutes = 0
            while (minutes < 600 && people.any { e -> with(sim.world) { e has Goal } }) {
                sim.step(1.0)
                minutes++
            }
            return minutes
        }
        val one = minutesFor(1)
        val three = minutesFor(3)
        assertTrue(three < one, "three cabins ($three min) should beat one ($one min)")
    }

    @Test
    fun cabinsStayBuyableWhenAStarIsLost() {
        val sim = Simulation(seed = 5, startMoney = 10_000_000)
        shaft(sim, 5)
        sim.stars = 3
        sim.stars = 1
        val car = sim.elevators.cars.values.single()
        assertTrue(sim.addCabin(car).ok, "one star less, still three cabins")
        assertTrue(sim.addCabin(car).ok)
        assertEquals(3, car.cabins.size)
        val loaded = Simulation.load(SaveGame.fromJson(sim.toSave().toJson()))
        assertEquals(3, loaded.bestStars)
    }
}
