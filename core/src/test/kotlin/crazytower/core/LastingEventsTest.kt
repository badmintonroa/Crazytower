package crazytower.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LastingEventsTest {
    private fun event(id: String) = Events.all.first { it.id == id }

    private fun towerWithElevator(): Simulation {
        val sim = Simulation(seed = 4, startMoney = 2_000_000)
        sim.eventsEnabled = false
        sim.build(sim.planLobby(0, 11))
        sim.build(sim.planColumn(Catalog.ELEVATOR, 4, 0, 4))
        return sim
    }

    @Test
    fun theBouquetEndsTheWeddingEarly() {
        val sim = towerWithElevator()
        assertTrue(event("wedding").apply(sim))
        val car = sim.elevators.cars.values.single()
        assertTrue(car.blockedUntil > sim.clock.totalMinutes)
        val wedding = sim.activeEvents.single()
        assertTrue(sim.useRemedy(wedding).ok)
        assertTrue(sim.activeEvents.isEmpty())
        assertTrue(car.blockedUntil <= sim.clock.totalMinutes)
        assertFalse(event("wedding").apply(sim) && sim.activeEvents.size > 1)
    }

    @Test
    fun eventsRunOutByThemselves() {
        val sim = towerWithElevator()
        assertTrue(event("karaoke").apply(sim))
        assertTrue(sim.elevators.cars.values.single().slowUntil > sim.clock.totalMinutes)
        sim.step(181.0)
        assertTrue(sim.activeEvents.isEmpty())
        assertTrue(sim.news.latest.any { it.text.contains("Karaoke") })
    }

    @Test
    fun aRemedyCostsMoney() {
        val sim = Simulation(seed = 6, startMoney = 1_000_000)
        sim.eventsEnabled = false
        val office = sim.buildReachable(Catalog.OFFICE, 10)
        sim.moveIn(office, Tenants.byId("supervillain"))
        sim.stage = 4
        sim.stars = 3
        assertTrue(event("blackmail").apply(sim))
        val blackmail = sim.eventsAt(office).single()
        val before = sim.money
        assertTrue(sim.useRemedy(blackmail).ok)
        assertEquals(before - Lasting.BLACKMAIL, sim.money)
        assertNotNull(sim.useRemedy(blackmail).reason.takeIf { it.isNotEmpty() })
    }

    @Test
    fun aSnackBarStopsTheBloodDrive() {
        val sim = Simulation(seed = 7, startMoney = 2_000_000)
        sim.eventsEnabled = false
        val office = sim.buildReachable(Catalog.OFFICE, 10)
        sim.moveIn(office, Tenants.byId("vampire"))
        sim.step(16 * 60.0) // night
        assertTrue(event("blooddrive").apply(sim), "hour ${sim.clock.hour}, tenant ${office.tenant?.id}, food ${sim.tower.allRooms.filter { it.type.role == RoomRole.FOOD }.map { it.floor }}")
        sim.buildReachable(Catalog.FOOD, 20)
        sim.step(2.0)
        assertTrue(sim.activeEvents.isEmpty())
    }

    @Test
    fun socksStopTheRentBelow() {
        val sim = Simulation(seed = 8, startMoney = 2_000_000)
        sim.eventsEnabled = false
        val below = sim.buildReachable(Catalog.OFFICE, 10, floor = 1)
        val archive = sim.buildReachable(Catalog.OFFICE, 10, floor = 2)
        sim.moveIn(below, Tenants.byId("accountant"))
        sim.moveIn(archive, Tenants.byId("socks"))
        assertTrue(event("socks").apply(sim))
        assertTrue(sim.rentBlocked(below))
        assertFalse(sim.rentBlocked(archive))
        sim.step(121.0)
        assertFalse(sim.rentBlocked(below))
    }

    @Test
    fun termitesKeepTenantsOut() {
        val sim = Simulation(seed = 9, startMoney = 2_000_000)
        sim.eventsEnabled = false
        val office = sim.buildReachable(Catalog.OFFICE, 10)
        sim.step(10 * 1440.0)
        office.tenant?.let { sim.moveOut(office) }
        assertTrue(event("termites").apply(sim))
        sim.step(600.0)
        assertNull(office.tenant)
        assertTrue(sim.useRemedy(sim.eventsAt(office).single()).ok)
        assertFalse(sim.moveInBlocked(office))
    }

    @Test
    fun theAliensGiveTheFlatBack() {
        val sim = Simulation(seed = 10, startMoney = 2_000_000)
        sim.eventsEnabled = false
        sim.stage = 5
        val embassy = sim.buildReachable(Catalog.OFFICE, 10)
        val home = sim.buildReachable(Catalog.HOME, 10, floor = 2)
        sim.moveIn(embassy, Tenants.byId("alien"))
        sim.moveIn(home, Tenants.byId("family"))
        assertTrue(event("exchange").apply(sim))
        assertEquals("exchange", home.tenant?.id)
        sim.step(1441.0)
        assertEquals("family", home.tenant?.id)
    }

    @Test
    fun theFireAlarmEmptiesTheTower() {
        val sim = Simulation(seed = 11, startMoney = 2_000_000)
        sim.eventsEnabled = false
        val food = sim.buildReachable(Catalog.FOOD, 10)
        sim.moveIn(food, Tenants.byId("imbiss"))
        sim.step(5 * 60.0)
        assertTrue(event("firealarm").apply(sim))
        assertTrue(sim.evacuated)
        assertTrue(sim.visitorsStopped)
        sim.step(31.0)
        assertFalse(sim.evacuated)
    }

    @Test
    fun theOpenDayIsOncePerWeek() {
        val sim = Simulation(seed = 12, startMoney = 2_000_000)
        sim.eventsEnabled = false
        sim.build(sim.planLobby(0, 11))
        assertFalse(sim.startOpenDay().ok, "needs stage 3")
        sim.stage = 3
        sim.step(60.0) // 8 o'clock
        assertTrue(sim.startOpenDay().ok)
        assertEquals(2, sim.visitorBoost)
        assertFalse(sim.startOpenDay().ok)
        sim.step(1440.0)
        assertEquals(1, sim.visitorBoost)
        assertFalse(sim.startOpenDay().ok, "only once a week")
    }

    @Test
    fun runningEventsSurviveSaving() {
        val sim = towerWithElevator()
        assertTrue(event("wedding").apply(sim))
        sim.lastOpenDay = 123.0
        val loaded = Simulation.load(SaveGame.fromJson(sim.toSave().toJson()))
        assertEquals(sim.activeEvents, loaded.activeEvents)
        assertEquals(123.0, loaded.lastOpenDay)
        assertTrue(loaded.elevators.cars.values.single().blockedUntil > loaded.clock.totalMinutes)
    }

    @Test
    fun theTenantJokesFitTheirTenants() {
        val sim = Simulation(seed = 13, startMoney = 2_000_000)
        sim.eventsEnabled = false
        val office = sim.buildReachable(Catalog.OFFICE, 10)
        sim.moveIn(office, Tenants.byId("excuses"))
        assertFalse(event("ministry").apply(sim))
        assertTrue(event("excuses").apply(sim))
        val skipped = office.paidUntil
        assertTrue(skipped > sim.clock.totalMinutes)
    }

    @Test
    fun notTooManyEventsAtOnce() {
        val sim = towerWithElevator()
        sim.stage = 5
        assertTrue(event("wedding").apply(sim))
        sim.activeEvents += ActiveEvent("openday", sim.clock.totalMinutes, sim.clock.totalMinutes + 600)
        repeat(500) { Events.roll(sim) }
        assertEquals(2, sim.activeEvents.size)
    }
}
