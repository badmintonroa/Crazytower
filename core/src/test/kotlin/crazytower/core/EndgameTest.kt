package crazytower.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EndgameTest {
    private fun rich(stage: Int = 4): Simulation = officeSim(seed = 3, startMoney = 500_000_000).apply {
        eventsEnabled = false
        this.stage = stage
        stars = stage.coerceAtMost(5)
    }

    private fun Simulation.spent(kind: MoneyKind): Long = -(finance.day(clock.day)[kind] ?: 0L)

    @Test
    fun upgradesRaiseRentAndMoodAndStopAtThree() {
        val sim = rich()
        val office = sim.buildReachable(Catalog.OFFICE, 0)
        sim.moveIn(office, Tenants.byId("accountant"))
        val tenant = office.tenant!!
        val before = sim.rentFor(office, tenant)
        val moodBefore = sim.moodTarget(crazytower.core.ecs.Schedule(tenant.plan, 0), resident = false, room = office)
        assertTrue(sim.upgradeRoom(office).ok)
        assertEquals(1, office.upgrade)
        assertEquals(Upgrades.COSTS[0], sim.spent(MoneyKind.UPGRADE))
        assertTrue(sim.rentFor(office, tenant) > before * 1.1, "more rent after the upgrade")
        assertEquals(moodBefore + Upgrades.MOOD_BONUS, sim.moodTarget(crazytower.core.ecs.Schedule(tenant.plan, 0), resident = false, room = office))
        assertEquals("Taschenrechner mit Goldtasten", Upgrades.name(office, 1))
        assertTrue(sim.upgradeRoom(office).ok)
        assertTrue(sim.upgradeRoom(office).ok)
        assertFalse(sim.upgradeRoom(office).ok, "three levels at most")
        assertEquals(Upgrades.COSTS.sum(), sim.spent(MoneyKind.UPGRADE))
    }

    @Test
    fun nothingToSpendBeforeStageFour() {
        val sim = rich(stage = 3)
        val office = sim.buildReachable(Catalog.OFFICE, 0)
        sim.moveIn(office, Tenants.byId("accountant"))
        assertNotNull(sim.upgradeBlocked(office))
        assertNotNull(sim.campaignBlocked())
        assertNotNull(sim.gateBlocked())
        assertNotNull(sim.toggleInsurance("pigeons"))
    }

    @Test
    fun tuningMakesTheShaftFasterAndBiggerAndIsSaved() {
        val sim = rich()
        sim.build(sim.planLobby(0, 11))
        for (f in 1..5) sim.build(Catalog.CORRIDOR, f, 0)
        check(sim.build(sim.planColumn(Catalog.ELEVATOR, 2, GROUND_FLOOR, 5)).ok)
        val car = sim.elevators.cars.values.single()
        val slow = car.minutesPerFloor
        val seats = car.capacity
        repeat(3) { assertTrue(sim.tuneElevator(car).ok) }
        assertEquals(Tuning.MAX_LEVEL, car.tuning)
        assertTrue(car.minutesPerFloor < slow)
        assertTrue(car.capacity > seats)
        assertFalse(sim.tuneElevator(car).ok)
        assertEquals(Tuning.COSTS.sum(), sim.spent(MoneyKind.TUNING))
        val office = sim.buildReachable(Catalog.OFFICE, 6)
        sim.moveIn(office, Tenants.byId("accountant"))
        sim.upgradeRoom(office)
        val loaded = Simulation.load(SaveGame.fromJson(sim.toSave().toJson()))
        assertEquals(Tuning.MAX_LEVEL, loaded.elevators.cars.values.single().tuning)
        assertEquals(1, loaded.tower.allRooms.first { it.type == Catalog.OFFICE }.upgrade)
    }

    @Test
    fun insuranceCostsAShareOfTheIncomeEveryDay() {
        val sim = rich()
        assertNull(sim.toggleInsurance("pigeons"))
        assertNotNull(sim.insured("pigeons"))
        assertNull(sim.insured("ghost"))
        sim.payInsurances()
        assertEquals(Insurances.MIN_FEE, sim.spent(MoneyKind.INSURANCE))
        assertNull(sim.toggleInsurance("pigeons"))
        assertNull(sim.insured("pigeons"), "cancelled")
    }

    @Test
    fun theTowerPartyLiftsTheMoodAndWaitsAWeek() {
        val sim = rich()
        val schedule = crazytower.core.ecs.Schedule(Tenants.byId("accountant").plan, 0)
        val before = sim.moodTarget(schedule, resident = false)
        assertTrue(sim.throwParty().ok)
        assertEquals(before + Promotion.PARTY_MOOD, sim.moodTarget(schedule, resident = false))
        assertTrue(sim.spent(MoneyKind.PROMOTION) >= Promotion.PARTY_MIN)
        assertFalse(sim.throwParty().ok)
        assertTrue(sim.startCampaign().ok)
        assertTrue(sim.campaignRunning)
        assertFalse(sim.startCampaign().ok, "one campaign at a time")
    }

    @Test
    fun sideEntrancesBringTheStreetCloser() {
        val sim = rich().apply { bestStars = 5; stars = 5 }
        val middle = Lot.maxWidth / 2f
        assertEquals(0f, sim.streetExit(middle - 2).x)
        assertTrue(sim.buyGate().ok)
        assertEquals(middle, sim.streetExit(middle - 2).x)
        assertEquals(3, sim.entrances.size)
        assertTrue(sim.buyGate().ok)
        assertTrue(sim.buyGate().ok)
        assertNotNull(sim.gateBlocked(), "Granny Hildegard has no more beds to sell")
    }

    @Test
    fun researchGetsDearerAndWorks() {
        val sim = rich()
        val first = sim.researchCost()
        assertTrue(sim.doResearch(Research.byId("patience")!!).ok)
        assertEquals(first * 2, sim.researchCost())
        assertTrue(sim.researched("patience"))
        assertFalse(sim.doResearch(Research.byId("patience")!!).ok)
    }

    @Test
    fun recruitingPicksTheTenant() {
        val sim = rich()
        val office = sim.buildReachable(Catalog.OFFICE, 0)
        sim.step(15.0) // the tenant system looks at the way in
        val clowns = Tenants.byId("clown")
        assertTrue(clowns in sim.recruitable(office))
        assertTrue(sim.recruit(office, clowns).ok)
        assertEquals("clown", office.tenant?.id)
        assertTrue(sim.spent(MoneyKind.EXTRAS) >= Recruiting.MIN)
        assertFalse(sim.recruit(office, clowns).ok, "rented already")
    }

    @Test
    fun theCountdownNeedsTheTasksAndMovesOnWithEveryPart() {
        val sim = rich(stage = 5)
        val ramp = Mission.parts[0]
        assertEquals(ramp, sim.currentPart)
        assertNotNull(sim.partBlocked(ramp.type, Mission.FLOOR))
        assertNotNull(sim.partBlocked(Mission.TANK, Mission.FLOOR), "parts go in order")
        // the officials only count when they liked the room
        repeat(Mission.SUITES.goal.toInt()) { sim.addProgress(Mission.SUITES) }
        assertTrue(sim.taskDone(Mission.SUITES))
        sim.addProgress(Mission.PERMIT, Mission.PERMIT.goal)
        assertNull(sim.partBlocked(ramp.type, Mission.FLOOR))
        assertNotNull(sim.partBlocked(ramp.type, Mission.FLOOR - 1), "top floor only")
        val room = sim.tower.placeUnchecked(ramp.type, Mission.FLOOR, 0)
        sim.partBuilt(room)
        assertEquals(1, sim.endgame.parts)
        assertEquals(Mission.parts[1], sim.currentPart)
        // coffee only counts while it is needed
        sim.mealServed()
        assertEquals(1, sim.taskProgress(Mission.COFFEE))
        assertEquals(0, sim.taskProgress(Mission.ASTRONAUTS))
        assertTrue(sim.spectatorsPerDay > Mission.FIRST_SPECTATORS)
    }

    @Test
    fun zorgSignsOnlyWhenHappyAndLiftOffEndsTheCountdown() {
        val sim = rich(stage = 5)
        sim.endgame.parts = 3
        sim.vipJudged("zorg", happy = false, where = Loc(0, 0f))
        assertFalse(sim.taskDone(Mission.ZORG))
        sim.vipJudged("zorg", happy = true, where = Loc(0, 0f))
        assertTrue(sim.taskDone(Mission.ZORG))
        repeat(3) { sim.addProgress(Mission.SABOTAGE) }
        val lift = sim.tower.placeUnchecked(Mission.LIFT, Mission.FLOOR, 24)
        sim.partBuilt(lift)
        assertTrue(sim.liftedOff)
        assertNull(sim.currentPart)
        assertNotNull(sim.endgame.liftOffDay)
    }

    @Test
    fun grumblingSpectatorsHoldUpTheCountdownAndTheStateIsSaved() {
        val sim = rich(stage = 5)
        repeat(20) { sim.spectatorGaveUp() }
        sim.missionMidnight()
        assertTrue(sim.endgame.delayedUntil > sim.clock.totalMinutes)
        sim.endgame.insurances += "garlic"
        sim.endgame.decor += "duck"
        sim.endgame.progress["suites"] = 7
        val loaded = Simulation.load(SaveGame.fromJson(sim.toSave().toJson()))
        assertEquals(7, loaded.taskProgress(Mission.SUITES))
        assertTrue("garlic" in loaded.endgame.insurances)
        assertTrue("duck" in loaded.endgame.decor)
        assertEquals(sim.endgame.delayedUntil, loaded.endgame.delayedUntil)
        // an old save without the block starts fresh
        val old = SaveGame.fromJson(sim.toSave().copy(endgame = null).toJson())
        assertEquals(0, Simulation.load(old).endgame.parts)
    }
}
