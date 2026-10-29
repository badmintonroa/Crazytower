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
        assertFalse(sim.addCabin(car).ok, "one star: one cabin")
        sim.stars = 3
        val before = sim.money
        assertTrue(sim.addCabin(car).ok)
        assertTrue(sim.addCabin(car).ok)
        assertFalse(sim.addCabin(car).ok, "three stars: three cabins")
        assertEquals(3, car.cabins.size)
        assertEquals(before - 2 * crazytower.core.transport.Elevators.CABIN_COST, sim.money)
        // the shaft remembers its cabins in the save game
        val loaded = Simulation.load(SaveGame.fromJson(sim.toSave().toJson()), seed = 1)
        assertEquals(3, loaded.elevators.cars.values.single().cabins.size)
        assertTrue(sim.removeCabin(car))
        assertEquals(2, car.cabins.size)
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
