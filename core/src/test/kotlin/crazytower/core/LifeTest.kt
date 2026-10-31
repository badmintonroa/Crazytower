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
        val sim = officeSim(seed = 2, startMoney = 10_000_000)
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
        val sim = officeSim(seed = 3, startMoney = 10_000_000)
        sim.eventsEnabled = false // the clown event would turn the vampires into clowns
        val office = sim.buildReachable(Catalog.OFFICE, 20)
        sim.moveIn(office, Tenants.byId("vampire"))
        stepTo(sim, 12)
        with(sim.world) { sim.membersOf(office.id).forEach { assertTrue(it has Away) } }
        stepTo(sim, 1, day = 1)
        with(sim.world) { assertTrue(sim.membersOf(office.id).count { it hasNo Away } >= 4) }
    }

    @Test
    fun peopleGoForLunchAndPayForIt() {
        val sim = officeSim(seed = 8, startMoney = 10_000_000)
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
    fun paymentsAtTheSameTimeFromTheSamePayerAreSummed() {
        val sim = officeSim(seed = 9, startMoney = 1_000_000).apply { eventsEnabled = false }
        sim.build(sim.planLobby(20, 37))
        val offices = listOf(sim.buildReachable(Catalog.OFFICE, 20)) +
            listOf(26, 32).map { sim.build(Catalog.OFFICE, 1, it).room!! }
        val accountant = Tenants.byId("accountant")
        offices.forEach { sim.moveIn(it, accountant) }
        val entry = sim.upcoming().single { it.label == accountant.name.toString() }
        assertEquals(3, entry.count)
        assertEquals(3 * accountant.rentPerDay, entry.amount)
    }

    @Test
    fun officesPayAfterWork() {
        val sim = officeSim(seed = 9, startMoney = 1_000_000).apply { eventsEnabled = false }
        val office = sim.buildReachable(Catalog.OFFICE, 20)
        sim.moveIn(office, Tenants.byId("accountant"))
        val rent = Tenants.byId("accountant").rentPerDay
        assertEquals(17 * 60, sim.upcoming().first { it.label == office.tenant!!.name.toString() }.minute)
        stepTo(sim, 16)
        assertEquals(null, sim.finance.day(0)[MoneyKind.RENT], "nothing before closing time")
        stepTo(sim, 18)
        assertEquals(rent, sim.finance.day(0)[MoneyKind.RENT])
        stepTo(sim, 1, day = 1)
        assertTrue(sim.news.latest.any { it.text.startsWith("Tagesabschluss") })
    }

    @Test
    fun vampiresPayInTheMorning() {
        val sim = officeSim(seed = 9, startMoney = 1_000_000).apply { eventsEnabled = false }
        val office = sim.buildReachable(Catalog.OFFICE, 20)
        sim.moveIn(office, Tenants.byId("vampire"))
        stepTo(sim, 23)
        assertEquals(null, sim.finance.day(0)[MoneyKind.RENT])
        stepTo(sim, 5, day = 1)
        assertTrue(sim.finance.day(1)[MoneyKind.RENT] == Tenants.byId("vampire").rentPerDay)
    }

    @Test
    fun onlyAFewFlatsFindBuyersEachDay() {
        val sim = officeSim(seed = 4, startMoney = 1_000_000).apply { eventsEnabled = false }
        sim.build(sim.planLobby(10, 45))
        val flats = listOf(sim.buildReachable(Catalog.HOME, 10)) + (16..40 step 6).map { sim.build(Catalog.HOME, 1, it).room!! }
        stepTo(sim, 23)
        assertEquals(sim.flatBuyersPerDay, flats.count { it.tenant != null }, "two buyers on the first day")
        stepTo(sim, 23, day = 1)
        assertEquals(2 * sim.flatBuyersPerDay, flats.count { it.tenant != null }, "two more the next day")
    }

    @Test
    fun flatsAreSoldOnceAndPayFeesEveryDay() {
        val sim = officeSim(seed = 9, startMoney = 1_000_000).apply { eventsEnabled = false }
        val home = sim.buildReachable(Catalog.HOME, 20)
        val before = sim.money
        sim.moveIn(home, Tenants.byId("family"))
        assertEquals(before + Tenants.byId("family").salePrice, sim.money)
        stepTo(sim, 1, day = 4)
        assertEquals(Simulation.HOME_FEE, sim.finance.day(3)[MoneyKind.FEES])
        // leaving unhappy costs half of the price
        val now = sim.money
        sim.moveOut(home)
        assertEquals(now - Tenants.byId("family").salePrice / 2, sim.money)
    }

    @Test
    fun residentsLeaveForWorkOnWeekdays() {
        val sim = officeSim(seed = 6, startMoney = 10_000_000)
        val home = sim.buildReachable(Catalog.HOME, 20)
        sim.moveIn(home, Tenants.byId("family"))
        stepTo(sim, 6, day = 1)
        with(sim.world) { assertTrue(sim.membersOf(home.id).all { it hasNo Away }) }
        stepTo(sim, 13, day = 1)
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
        val sim = officeSim(seed = 1, startMoney = 10_000_000)
        val office = sim.buildReachable(Catalog.OFFICE, 20)
        sim.moveIn(office, Tenants.byId("startup"))
        sim.moveOut(office)
        assertEquals(0, sim.world.family { all(Member) }.numEntities)
    }
}
