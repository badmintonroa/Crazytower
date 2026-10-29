package crazytower.core

import crazytower.core.ecs.Member
import crazytower.core.nav.Leg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StageEventSaveTest {
    private fun bigTower(): Simulation {
        val sim = Simulation(seed = 21, startMoney = 50_000_000)
        sim.stage = 1 // nine floors: past the building site
        for (i in 0 until 8) sim.build(Catalog.LOBBY, 0, i * 6)
        sim.build(Catalog.ELEVATOR, 0, 48)
        for (f in 1..9) {
            for (i in 0 until 8) assertTrue(sim.build(Catalog.HOME, f, i * 6).ok)
            sim.build(Catalog.ELEVATOR, f, 48)
        }
        return sim
    }

    @Test
    fun stageTwoNeedsThreeHundredResidents() {
        val sim = bigTower()
        assertEquals(1, sim.stage)
        assertFalse(sim.checkPlace(Catalog.COPYROOM, 0, 56).ok, "copy room needs stage 2")
        for (room in sim.tower.allRooms.filter { it.type == Catalog.HOME }) sim.moveIn(room, Tenants.byId("family"))
        // 72 homes × 3 = 216 residents: not enough yet
        assertEquals(72 * 3, sim.population)
        sim.step(120.0)
        assertEquals(1, sim.stage)
        assertTrue(sim.stageProgress > 0.5)
    }

    @Test
    fun stageRequirementsAddUp() {
        val sim = Simulation()
        assertFalse(Stages.reached(sim, 2))
        assertTrue(Stages.progress(sim) == 0.0)
        assertEquals("lobby_stage3_wolkenkratzer", Stages.info(3).lobbyArt)
    }

    @Test
    fun clownsTakeOverAnOffice() {
        val sim = Simulation(seed = 5, startMoney = 1_000_000)
        val office = sim.buildReachable(Catalog.OFFICE, 10)
        sim.moveIn(office, Tenants.byId("accountant"))
        val clown = Events.all.first { it.id == "clown" }
        assertTrue(clown.apply(sim))
        assertEquals("clown", office.tenant?.id)
        assertEquals(Catalog.OFFICE.capacity, sim.membersOf(office.id).size)
        assertTrue(sim.news.latest.first().text.contains("Clownsagentur"))
    }

    @Test
    fun everyEventCopesWithAnEmptyTower() {
        val sim = Simulation(seed = 5)
        for (event in Events.all) event.apply(sim)
        repeat(3 * 24) { Events.roll(sim); sim.step(60.0) }
    }

    @Test
    fun teleportersConnectFarFloors() {
        val sim = Simulation(seed = 5, startMoney = 50_000_000)
        sim.build(Catalog.LOBBY, 0, 0)
        sim.build(Catalog.TELEPORTER, 0, 6).let { assertFalse(it.ok, "needs stage 4") }
        val tower = sim.tower
        val pad = TowerTestAccess.forcePlace(tower, Catalog.TELEPORTER, 0, 6)
        val corridorTop = TowerTestAccess.forcePlace(tower, Catalog.CORRIDOR, 7, 0)
        val padTop = TowerTestAccess.forcePlace(tower, Catalog.TELEPORTER, 7, 6)
        check(pad.id != padTop.id && corridorTop.floor == 7)
        val path = assertNotNull(sim.nav.findPath(Loc(0, 1f), Loc(7, 2f)))
        assertEquals(1, path.count { it is Leg.Teleport })
    }

    @Test
    fun saveAndLoadKeepTheTower() {
        val sim = Simulation(seed = 3, startMoney = 2_000_000)
        val office = sim.buildReachable(Catalog.OFFICE, 10)
        sim.build(Catalog.LOBBY, 0, 16)
        sim.build(Catalog.CORRIDOR, 1, 16)
        sim.moveIn(office, Tenants.byId("vampire"))
        sim.step(90.0)
        val json = sim.toSave().toJson()
        val loaded = Simulation.load(SaveGame.fromJson(json))
        assertEquals(sim.money, loaded.money)
        assertEquals(sim.clock.totalMinutes, loaded.clock.totalMinutes)
        assertEquals(sim.tower.allRooms.map { it.id to it.type.id }, loaded.tower.allRooms.map { it.id to it.type.id })
        assertEquals(office.tenant?.id, loaded.tower.room(office.id)?.tenant?.id)
        assertEquals(Catalog.OFFICE.capacity, loaded.world.family { all(Member) }.numEntities)
        assertEquals(sim.news.latest.map { it.text }, loaded.news.latest.map { it.text })
        // the loaded tower keeps working
        val lobby = loaded.tower.roomAt(GROUND_FLOOR, 10)!!
        assertEquals(10 to 12, lobby.x to lobby.width, "the two lobbies were joined, and stay joined")
        assertTrue(loaded.build(Catalog.OFFICE, 1, 10).ok.not())
        assertTrue(loaded.build(Catalog.HOME, 2, 10).ok)
        loaded.step(60.0)
    }

    @Test
    fun aNewTowerStartsAtStageZeroWithoutStars() {
        val sim = Simulation()
        assertEquals(0, sim.stage)
        assertEquals(0, sim.stars)
        assertEquals("Baustelle", Stages.info(0).name)
        assertFalse(sim.checkPlace(Catalog.OFFICE, Stages.info(0).maxFloor + 1, 10).ok)
    }
}
