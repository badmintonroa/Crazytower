package crazytower.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The loop over the stars: what is unlocked when, drags that build what the money pays for, and the mix of offices and homes. */
class StarPathsTest {
    @Test
    fun withoutStarsThereAreOnlyFlats() {
        val sim = Simulation(startMoney = 10_000_000)
        assertTrue(sim.unlocked(Catalog.HOME))
        assertFalse(sim.unlocked(Catalog.OFFICE))
        assertFalse(sim.unlocked(Catalog.FOOD))
        sim.build(sim.planLobby(0, 11))
        assertEquals("Erst ab 1 Sternen", sim.checkPlace(Catalog.OFFICE, 1, 0).reason)
        assertTrue(sim.checkPlace(Catalog.HOME, 1, 0).ok)
        sim.stars = 1
        assertTrue(sim.checkPlace(Catalog.OFFICE, 1, 0).ok)
    }

    @Test
    fun newRoomTypesAreMarkedUntilSeen() {
        val sim = Simulation()
        assertFalse(sim.isNew(Catalog.HOME), "what a new tower starts with is not new")
        assertFalse(sim.isNew(Catalog.OFFICE), "locked types are not shown at all")
        sim.stars = 1
        assertTrue(sim.isNew(Catalog.OFFICE))
        assertTrue(sim.isNew(Catalog.FOOD))
        sim.seenTypes += Catalog.OFFICE.id
        val loaded = Simulation.load(SaveGame.fromJson(sim.toSave().toJson()))
        assertFalse(loaded.isNew(Catalog.OFFICE))
        assertTrue(loaded.isNew(Catalog.FOOD), "still not seen after loading")
    }

    @Test
    fun oldSavesCountWhatIsUnlockedAsSeen() {
        val sim = Simulation()
        sim.stars = 3
        val old = sim.toSave().copy(seenTypes = null)
        val loaded = Simulation.load(old)
        assertTrue(Catalog.roomTypes.filter { loaded.unlocked(it) }.none { loaded.isNew(it) })
    }

    @Test
    fun anElevatorDraggedTooFarBuildsWhatTheMoneyPays() {
        val sim = officeSim(startMoney = 1_000_000)
        sim.build(sim.planLobby(0, 11))
        for (f in 1..4) sim.build(Catalog.CORRIDOR, f, 0)
        val price = sim.priceAt(Catalog.ELEVATOR, 0, 2)
        sim.money = 3 * price + price / 2
        val plan = sim.planColumn(Catalog.ELEVATOR, 2, GROUND_FLOOR, 3)
        assertTrue(plan.check.ok, plan.check.reason)
        assertEquals(listOf(0, 1, 2), plan.pieces.map { it.floor })
        assertEquals(listOf(3), plan.blocked.map { it.floor })
        assertEquals("Zu wenig Geld", plan.blockedReason)
        assertTrue(sim.build(plan).ok)
        assertEquals(3, sim.tower.allRooms.count { it.type == Catalog.ELEVATOR })
        assertEquals(price / 2, sim.money)
    }

    @Test
    fun anElevatorDraggedPastTheFloorLimitStopsThere() {
        val sim = officeSim(startMoney = 10_000_000)
        sim.build(sim.planLobby(0, 11))
        val plan = sim.planColumn(Catalog.ELEVATOR, 2, GROUND_FLOOR, 8)
        val limit = Stages.info(sim.stage).maxFloor
        assertEquals((0 until limit).toList(), plan.pieces.map { it.floor })
        assertEquals((limit..8).toList(), plan.blocked.map { it.floor })
        assertTrue(plan.check.ok)
    }

    @Test
    fun aShaftExtendedDownwardsKeepsTheFloorsNextToIt() {
        val sim = officeSim(startMoney = 10_000_000)
        sim.stage = 1
        sim.build(sim.planLobby(0, 11))
        for (f in 1..6) sim.build(Catalog.CORRIDOR, f, 0)
        sim.build(sim.planColumn(Catalog.ELEVATOR, 2, 5, 6))
        val price = sim.priceAt(Catalog.ELEVATOR, 4, 2)
        sim.money = 2 * price
        // grabbed at the bottom end on floor 5 and dragged down to floor 1: floors 4 and 3 join the shaft
        val plan = sim.planColumn(Catalog.ELEVATOR, 2, 5, 1)
        assertEquals(listOf(4, 3), plan.pieces.map { it.floor })
        assertTrue(sim.build(plan).ok)
        assertEquals(3..6, sim.tower.shaftRange(5, 2, Catalog.ELEVATOR))
    }

    @Test
    fun parkingRowsStopWhereTheMoneyEnds() {
        val sim = officeSim(startMoney = 10_000_000)
        sim.stars = Lot.BASEMENT_STARS
        // cars that want a space: ten flats with residents
        for (i in 0 until 10) sim.moveIn(sim.tower.placeUnchecked(Catalog.HOME, 1 + i / 10, (i % 10) * 6), Tenants.byId("family"))
        sim.money = Catalog.PARKING.cost * 3
        val plan = sim.planRow(Catalog.PARKING, -1, 0, 19)
        assertEquals(3, plan.pieces.size)
        assertTrue(plan.blocked.isNotEmpty())
        assertTrue(plan.check.ok)
    }

    private fun threeStars(): Simulation = officeSim(seed = 4, startMoney = 50_000_000).apply {
        eventsEnabled = false
        stage = 3
        stars = 3
    }

    /** Puts [count] rooms of [type] with [tenant] into the tower, ten per floor from [floor] up. */
    private fun Simulation.fill(type: RoomType, tenant: String, count: Int, floor: Int): List<Room> = (0 until count).map { i ->
        tower.placeUnchecked(type, floor + i / 10, (i % 10) * 6).also { moveIn(it, Tenants.byId(tenant), announce = false) }
    }

    @Test
    fun fromThreeStarsOfficesNeedWorkers() {
        val sim = threeStars()
        assertEquals(Simulation.commuters(3), sim.workforce)
        sim.fill(Catalog.OFFICE, "accountant", Simulation.commuters(3) / 6, 1)
        assertFalse(sim.hasWorkersFor(6), "the commuters all have a job")
        val empty = sim.tower.placeUnchecked(Catalog.OFFICE, 20, 0)
        assertEquals("Keine Fachkräfte", RoomProblems.of(sim, empty, minutes = 5f)?.title?.toString())
        sim.fill(Catalog.HOME, "family", 2, 30)
        assertTrue(sim.hasWorkersFor(6), "six residents can take the jobs")
        assertNull(RoomProblems.of(sim, empty, minutes = 5f))
    }

    @Test
    fun belowThreeStarsOfficesFindPeopleAnyway() {
        val sim = threeStars().apply { stars = 2 }
        sim.fill(Catalog.OFFICE, "accountant", 100, 1)
        assertTrue(sim.hasWorkersFor(6))
    }

    @Test
    fun theSubwayBringsCommuters() {
        val sim = threeStars().apply { stars = 4 }
        val before = sim.workforce
        sim.tower.placeUnchecked(Catalog.SUBWAY, -1, 0)
        assertEquals(before + Simulation.SUBWAY_COMMUTERS, sim.workforce)
    }

    @Test
    fun studentsNeedSideJobs() {
        val sim = threeStars()
        assertFalse(sim.hasSideJobsFor(6), "no offices, no side jobs")
        sim.fill(Catalog.OFFICE, "accountant", 2, 1)
        assertTrue(sim.hasSideJobsFor(6), "twelve office workers, six students")
        sim.fill(Catalog.DORM, "dorm_chaos", 1, 5)
        assertFalse(sim.hasSideJobsFor(6))
        val empty = sim.tower.placeUnchecked(Catalog.DORM, 5, 30)
        assertEquals("Keine Nebenjobs", RoomProblems.of(sim, empty, minutes = 5f)?.title?.toString())
        assertEquals(6, sim.students)
        assertEquals(6, sim.residents)
        assertEquals(Simulation.commuters(3) + 6, sim.workforce, "students take jobs as well")
    }

    @Test
    fun dormsAreLoudShopsMakeResidentsHappy() = with(threeStars()) {
        val flat = fill(Catalog.HOME, "family", 1, 3).single()
        val schedule = crazytower.core.ecs.Schedule(Tenants.byId("family").plan, offset = 0)
        val quiet = moodTarget(schedule, resident = true, room = flat)
        tower.placeUnchecked(Catalog.DORM, 3, 30).also { moveIn(it, Tenants.byId("dorm_chaos"), announce = false) }
        assertEquals(quiet - Simulation.DORM_NOISE, moodTarget(schedule, resident = true, room = flat))
        tower.placeUnchecked(Catalog.SHOP, 4, 0).also { moveIn(it, Tenants.byId("spaeti"), announce = false) }
        assertEquals(quiet - Simulation.DORM_NOISE + Simulation.SHOP_MOOD, moodTarget(schedule, resident = true, room = flat))
    }

    @Test
    fun daycareAndJobsBringFlatBuyers() {
        val sim = threeStars()
        val base = sim.flatBuyersPerDay
        sim.tower.placeUnchecked(Catalog.KITA, 2, 0)
        assertEquals(base + Simulation.KITA_BUYERS, sim.flatBuyersPerDay)
        sim.fill(Catalog.OFFICE, "accountant", Simulation.JOBS_PER_BUYER / 6 + 1, 3)
        assertEquals(base + Simulation.KITA_BUYERS + 1, sim.flatBuyersPerDay)
    }

    @Test
    fun penthousesWantAView() {
        val sim = threeStars().apply { stars = 4; stage = 4 }
        assertEquals("Penthouse erst ab der ${Simulation.PENTHOUSE_FLOOR}. Etage", sim.checkPlace(Catalog.PENTHOUSE, 5, 0).reason)
        val penthouse = sim.fill(Catalog.PENTHOUSE, "heir", 1, Simulation.PENTHOUSE_FLOOR).single()
        assertTrue(Tenants.candidates(Catalog.PENTHOUSE, 4, 4).all { it.luxury && it.salePrice == Catalog.PENTHOUSE.cost })
        assertTrue(Tenants.candidates(Catalog.HOME, 5, 5).none { it.luxury }, "posh buyers stay in the penthouses")
        assertTrue(sim.hasParking(penthouse), "only luxury offices keep parking spaces of their own")
    }

    @Test
    fun theConferenceCentreMakesHotelNightsDearer() {
        val sim = threeStars().apply { stars = 4; stage = 4 }
        val hotel = sim.tower.placeUnchecked(Catalog.HOTEL, 2, 0)
        val guests = Tenants.byId("business")
        sim.moveIn(hotel, guests, announce = false)
        val plain = sim.rentFor(hotel, guests)
        sim.tower.placeUnchecked(Catalog.CONFERENCE, 3, 0)
        assertEquals((plain * (1 + Simulation.CONFERENCE_HOTEL_BONUS)).toLong(), sim.rentFor(hotel, guests))
    }

    @Test
    fun everyNewRoomHasArtNamesAndTenants() {
        for (type in listOf(Catalog.DORM, Catalog.SHOP, Catalog.PENTHOUSE)) {
            assertTrue(Tenants.candidates(type, 5, 5).isNotEmpty(), type.id)
        }
        val new = listOf(Catalog.DORM, Catalog.SHOP, Catalog.KITA, Catalog.PENTHOUSE, Catalog.SUBWAY, Catalog.CONFERENCE)
        assertTrue(new.all { it.minStars in 3..4 })
        assertNotNull(Catalog.type("subway"))
    }
}
