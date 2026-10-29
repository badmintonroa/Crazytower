package crazytower.core

import crazytower.core.ecs.Away
import crazytower.core.ecs.Member
import crazytower.core.ecs.Position
import crazytower.core.ecs.Schedule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LifeTest {
    private fun stepTo(sim: Simulation, hour: Int, day: Int = 0) {
        val target = day * 1440.0 + hour * 60
        sim.step(target - sim.clock.totalMinutes)
    }

    @Test
    fun officeWorkersComeInTheMorningAndLeaveInTheEvening() {
        val sim = Simulation(seed = 2, startMoney = 10_000_000)
        val office = sim.buildReachable(Catalog.OFFICE, 20)
        sim.moveIn(office, Tenants.byId("accountant"))
        val staff = sim.membersOf(office.id)
        assertEquals(Catalog.OFFICE.capacity, staff.size)

        stepTo(sim, 11)
        with(sim.world) {
            staff.forEach { e ->
                assertTrue(e hasNo Away, "${e[Schedule].activity}")
                assertEquals(office.floor, e[Position].floor)
            }
        }
        stepTo(sim, 20)
        with(sim.world) { staff.forEach { e -> assertTrue(e has Away, "${e[Schedule].activity}") } }
    }

    @Test
    fun nightShiftWorksAtNight() {
        // a seed without the clown event, which would turn the vampires into clowns
        val sim = Simulation(seed = 3, startMoney = 10_000_000)
        val office = sim.buildReachable(Catalog.OFFICE, 20)
        sim.moveIn(office, Tenants.byId("vampire"))
        stepTo(sim, 12)
        with(sim.world) { sim.membersOf(office.id).forEach { assertTrue(it has Away) } }
        stepTo(sim, 1, day = 1)
        with(sim.world) { assertTrue(sim.membersOf(office.id).count { it hasNo Away } >= 4) }
    }

    @Test
    fun peopleGoForLunchAndPayForIt() {
        val sim = Simulation(seed = 8, startMoney = 10_000_000)
        val office = sim.buildReachable(Catalog.OFFICE, 20)
        val food = sim.buildReachable(Catalog.FOOD, 30)
        sim.moveIn(office, Tenants.byId("accountant"))
        sim.moveIn(food, Tenants.byId("imbiss"))
        val before = sim.money
        stepTo(sim, 15)
        assertTrue(sim.earnedToday >= Simulation.MEAL_PRICE * 4, "earned ${sim.earnedToday}")
        assertTrue(sim.money > before)
    }

    @Test
    fun midnightPaysTheRent() {
        val sim = Simulation(seed = 9, startMoney = 1_000_000)
        val office = sim.buildReachable(Catalog.OFFICE, 20)
        sim.moveIn(office, Tenants.byId("accountant"))
        val before = sim.money
        stepTo(sim, 1, day = 1)
        assertTrue(sim.money - before >= Tenants.byId("accountant").rentPerDay / 2)
        assertTrue(sim.news.latest.any { it.text.startsWith("Tagesabschluss") })
    }

    @Test
    fun residentsLeaveForWorkOnWeekdays() {
        val sim = Simulation(seed = 6, startMoney = 10_000_000)
        val home = sim.buildReachable(Catalog.HOME, 20)
        sim.moveIn(home, Tenants.byId("family"))
        stepTo(sim, 8)
        with(sim.world) { assertTrue(sim.membersOf(home.id).all { it hasNo Away }) }
        stepTo(sim, 13)
        with(sim.world) { assertTrue(sim.membersOf(home.id).all { it has Away }) }
    }

    @Test
    fun starsNeedPeopleMoodAndFood() {
        assertEquals(1, Rating.stars(10, 90f, true))
        assertEquals(2, Rating.stars(200, 60f, false))
        assertEquals(3, Rating.stars(200, 60f, true))
        assertEquals(2, Rating.stars(200, 52f, true))
    }

    @Test
    fun moveOutRemovesThePeople() {
        val sim = Simulation(seed = 1, startMoney = 10_000_000)
        val office = sim.buildReachable(Catalog.OFFICE, 20)
        sim.moveIn(office, Tenants.byId("startup"))
        sim.moveOut(office)
        assertEquals(0, sim.world.family { all(Member) }.numEntities)
    }
}
