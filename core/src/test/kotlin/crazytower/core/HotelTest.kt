package crazytower.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HotelTest {
    private fun stage3(): Simulation = officeSim(seed = 5, startMoney = 10_000_000).apply {
        eventsEnabled = false
        stage = 3
        stars = 3
    }

    @Test
    fun guestsStayOneNightPayAndLeaveAMess() {
        val sim = stage3()
        val hotel = sim.buildReachable(Catalog.HOTEL, 10)
        // the clock starts in the morning: nobody checks in before the afternoon
        sim.step(60.0)
        assertNull(hotel.tenant)
        while (hotel.tenant == null && sim.clock.day == 0) sim.step(10.0)
        val guests = assertNotNull(hotel.tenant, "guests check in on the first afternoon")
        assertEquals(RoomRole.HOTEL, guests.role)
        assertTrue(sim.clock.hour in Simulation.CHECK_IN_FROM until Simulation.CHECK_IN_UNTIL)
        while (hotel.tenant != null && sim.clock.day < 2) sim.step(10.0)
        assertNull(hotel.tenant, "they leave the next morning")
        assertEquals(1, sim.clock.day)
        assertTrue((sim.finance.day(1)[MoneyKind.HOTEL] ?: 0) > 0, "and pay for the night")
        assertTrue(hotel.dirty)
        // without cleaning staff the room stays empty
        sim.step(24 * 60.0)
        assertTrue(hotel.dirty)
        assertNull(hotel.tenant)
    }

    @Test
    fun theCleaningStaffWalksOverAndCleans() {
        val sim = stage3()
        val hotel = sim.buildReachable(Catalog.HOTEL, 10)
        sim.buildReachable(Catalog.HOUSEKEEPING, 20)
        hotel.dirty = true
        sim.step(12 * 60.0)
        val service = sim.tower.allRooms.first { it.type == Catalog.HOUSEKEEPING }
        assertNotNull(service.tenant, "a cleaning company moved in")
        assertFalse(hotel.dirty, "somebody came over and cleaned")
    }

    @Test
    fun luxuryOfficesOnlyGetLuxuryTenants() {
        val plain = Tenants.candidates(Catalog.OFFICE, stars = 5, stage = 5)
        val luxury = Tenants.candidates(Catalog.LUXURY_OFFICE, stars = 5, stage = 5)
        assertTrue(plain.none { it.luxury })
        assertTrue(luxury.isNotEmpty() && luxury.all { it.luxury })
        assertTrue(Tenants.candidates(Catalog.LUXURY_OFFICE, stars = 3, stage = 3).isEmpty())
        assertTrue(luxury.minOf { it.rentPerDay } > plain.maxOf { it.rentPerDay })
    }

    @Test
    fun everyHotelClassGetsItsOwnGuests() {
        val single = Tenants.candidates(Catalog.HOTEL_SINGLE, stars = 5, stage = 5)
        val double = Tenants.candidates(Catalog.HOTEL, stars = 5, stage = 5)
        val suite = Tenants.candidates(Catalog.HOTEL_SUITE, stars = 5, stage = 5)
        assertTrue(single.isNotEmpty() && single.all { it.hotelClass == HotelClass.SINGLE })
        assertTrue(double.isNotEmpty() && double.all { it.hotelClass == HotelClass.DOUBLE })
        assertTrue(suite.isNotEmpty() && suite.all { it.hotelClass == HotelClass.SUITE && it.luxury })
        // the posher the room, the dearer the night
        assertTrue(single.maxOf { it.rentPerDay } < double.minOf { it.rentPerDay })
        assertTrue(double.maxOf { it.rentPerDay } < suite.minOf { it.rentPerDay })
        assertTrue(Catalog.HOTEL_SINGLE.width < Catalog.HOTEL.width && Catalog.HOTEL.width < Catalog.HOTEL_SUITE.width)
        // suites come with the fourth star, single and double rooms with the hotel
        assertTrue(Tenants.candidates(Catalog.HOTEL_SUITE, stars = 3, stage = 3).isEmpty())
        assertTrue(Tenants.candidates(Catalog.HOTEL_SINGLE, stars = 3, stage = 3).isNotEmpty())
        assertEquals(Catalog.HOTEL.minStage, Catalog.HOTEL_SINGLE.minStage)
        assertEquals(4, Catalog.HOTEL_SUITE.minStars)
    }

    @Test
    fun suitesTakeLongerToCleanThanSingleRooms() {
        val sim = stage3()
        sim.stars = 4
        sim.bestStars = 4
        val single = sim.buildReachable(Catalog.HOTEL_SINGLE, 10)
        val suite = sim.buildReachable(Catalog.HOTEL_SUITE, 20)
        assertTrue(sim.cleanMinutes(single) < Simulation.CLEAN_MINUTES)
        assertEquals(Simulation.CLEAN_MINUTES * 2, sim.cleanMinutes(suite))
    }

    @Test
    fun theOfficialsOnlyBookDoubleRooms() {
        val sim = stage3()
        sim.stars = 4
        sim.bestStars = 4
        sim.stage = Mission.STAGE
        val single = sim.buildReachable(Catalog.HOTEL_SINGLE, 10)
        val suite = sim.buildReachable(Catalog.HOTEL_SUITE, 20)
        assertTrue(Tenants.candidates(single.type, 5, 5).none { it.id == "officials" })
        assertTrue(Tenants.candidates(suite.type, 5, 5).none { it.id == "officials" })
    }

    @Test
    fun dirtyRoomsSurviveSaving() {
        val sim = stage3()
        val hotel = sim.buildReachable(Catalog.HOTEL, 10)
        hotel.dirty = true
        val loaded = Simulation.load(SaveGame.fromJson(sim.toSave().toJson()))
        assertTrue(loaded.tower.room(hotel.id)!!.dirty)
    }
}
