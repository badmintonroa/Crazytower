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
        val sim = officeSim(seed = 21, startMoney = 50_000_000)
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
        val sim = officeSim()
        assertFalse(Stages.reached(sim, 2))
        assertTrue(Stages.progress(sim) == 0.0)
        assertEquals("lobby_stage3_wolkenkratzer", Stages.info(3).lobbyArt)
    }

    @Test
    fun starsAndStagesAreOneLadder() {
        val sim = officeSim()
        sim.stars = 3
        assertEquals(3, sim.stage)
        assertEquals(50, Stages.info(sim.stage).maxFloor)
        assertEquals(Rating.population(3), Stages.info(3).population)
        // older saves kept the two apart: the higher one counts
        val old = sim.toSave().copy(stars = 1, stage = 3)
        val loaded = Simulation.load(old)
        assertEquals(3, loaded.stars)
        assertEquals(3, loaded.stage)
    }

    @Test
    fun clownsTakeOverAnOffice() {
        val sim = officeSim(seed = 5, startMoney = 1_000_000)
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
        val sim = officeSim(seed = 5)
        for (event in Events.all) event.apply(sim)
        repeat(3 * 24) { Events.roll(sim); sim.step(60.0) }
    }

    @Test
    fun oldSavesWithTeleportersLoadWithCorridorAndTheMoneyBack() {
        val sim = officeSim(seed = 5, startMoney = 2_000_000)
        sim.build(sim.planLobby(0, 11))
        sim.build(Catalog.CORRIDOR, 1, 0)
        sim.build(Catalog.OFFICE, 2, 0) // stands on floor 1, where the old pad was too
        sim.endgame.research += "coffee"
        sim.endgame.research += Simulation.OLD_TELEPORT_STUDY
        val save = sim.toSave()
        val pads = listOf(
            SavedRoom(id = 900, type = Catalog.OLD_TELEPORTER, floor = 0, x = 6, since = 0.0),
            SavedRoom(id = 901, type = Catalog.OLD_TELEPORTER, floor = 1, x = 6, since = 0.0),
        )
        // the pad on the ground took the place of part of the lobby
        val rooms = save.rooms.filter { it.floor != GROUND_FLOOR } + SavedRoom(id = 902, type = "lobby", floor = 0, x = 0, since = 0.0, width = 6) + pads
        val old = SaveGame.fromJson(save.copy(rooms = rooms).toJson())
        val loaded = Simulation.load(old)
        assertTrue(loaded.tower.allRooms.none { it.type.id == Catalog.OLD_TELEPORTER })
        assertEquals(Catalog.FILLER, loaded.tower.roomAt(1, 6)?.type, "corridor where the pad stood")
        assertEquals(Catalog.FILLER, loaded.tower.roomAt(GROUND_FLOOR, 7)?.type)
        assertEquals(setOf("coffee"), loaded.endgame.research)
        // two pads, and the study was the second one
        assertEquals(sim.money + 2 * Catalog.OLD_TELEPORTER_COST + (Research.FIRST_COST shl 1), loaded.money)
        assertTrue(loaded.news.latest.first().text.contains("Teleporter"))
        loaded.step(60.0)
    }

    @Test
    fun saveAndLoadKeepTheTower() {
        val sim = officeSim(seed = 3, startMoney = 2_000_000)
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
        val sim = officeSim()
        assertEquals(0, sim.stage)
        assertEquals(0, sim.stars)
        assertEquals("Baustelle", Stages.info(0).name.toString())
        assertFalse(sim.checkPlace(Catalog.OFFICE, Stages.info(0).maxFloor + 1, 10).ok)
    }
}
