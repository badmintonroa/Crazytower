package crazytower.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TenantTest {
    @Test
    fun reachableRoomsGetRented() {
        val sim = Simulation(seed = 11, startMoney = 10_000_000)
        val office = sim.build(Catalog.OFFICE, 0, 10).room!!
        sim.build(Catalog.LOBBY, 0, 20)
        val unreachable = sim.build(Catalog.HOME, 1, 20).room!!
        sim.step(8 * 60.0)
        val tenant = assertNotNull(office.tenant)
        assertEquals(RoomRole.OFFICE, tenant.role)
        assertNull(unreachable.tenant, "nobody can get up there without stairs")
        assertTrue(sim.population >= Catalog.OFFICE.capacity)
        assertTrue(sim.news.latest.any { tenant.name in it.text })
    }

    @Test
    fun specialTenantsOnlyComeWithStars() {
        val early = Tenants.candidates(RoomRole.OFFICE, stars = 1, stage = 1).map { it.id }
        assertTrue("vampire" !in early)
        assertTrue("clown" !in early, "the clowns only come through events")
        val late = Tenants.candidates(RoomRole.OFFICE, stars = 5, stage = 5).map { it.id }
        assertTrue("alien" in late)
    }
}
