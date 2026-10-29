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
        val office = sim.buildReachable(Catalog.OFFICE, 10)
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

    @Test
    fun crewsLookDifferent() {
        val sim = Simulation(seed = 11, startMoney = 10_000_000)
        sim.eventsEnabled = false
        val room = sim.buildReachable(Catalog.OFFICE, 10)
        sim.moveIn(room, Tenants.byId("clown"), announce = false)
        val looks = HashSet<String>()
        sim.world.family { all(crazytower.core.ecs.Member, crazytower.core.ecs.Person) }.forEach { e ->
            if (e[crazytower.core.ecs.Member].roomId == room.id) looks += e[crazytower.core.ecs.Person].look
        }
        assertEquals(minOf(5, Catalog.OFFICE.capacity), looks.size, "every clown of the crew shows up: $looks")
        assertTrue(looks.all { it.startsWith("npc_clown") })
        assertEquals("npc_retiree", Tenants.byId("retirees").lookFor(3), "a tenant without crew always looks the same")
    }
}
