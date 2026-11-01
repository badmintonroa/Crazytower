package crazytower.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UndoTest {
    private fun towerWithLobby(): Simulation = officeSim(startMoney = 1_000_000).apply {
        eventsEnabled = false
        build(planLobby(10, 33))
    }

    @Test
    fun aBuildComesBackWithItsWholePrice() {
        val sim = towerWithLobby()
        val before = sim.money
        val result = sim.undoableBuild { sim.build(Catalog.OFFICE, 1, 10) }
        assertTrue(result.ok)
        assertTrue(sim.money < before)
        assertNull(sim.undoBlocked())
        assertTrue(sim.undo().ok)
        assertEquals(before, sim.money)
        assertNull(sim.tower.room(result.room!!.id))
        assertNotNull(sim.undoBlocked(), "only one step can be taken back")
    }

    @Test
    fun aWholeShaftComesBackInOneStep() {
        val sim = towerWithLobby()
        val before = sim.money
        assertTrue(sim.undoableBuild { sim.build(sim.planColumn(Catalog.ELEVATOR, 20, GROUND_FLOOR, 4)) }.ok)
        assertEquals(5, sim.tower.allRooms.count { it.type == Catalog.ELEVATOR })
        assertTrue(sim.undo().ok)
        assertEquals(0, sim.tower.allRooms.count { it.type == Catalog.ELEVATOR })
        assertEquals(before, sim.money)
    }

    @Test
    fun undoEndsAfterAGameHour() {
        val sim = towerWithLobby()
        sim.undoableBuild { sim.build(Catalog.OFFICE, 1, 10) }
        sim.step(Undo.MINUTES + 1)
        assertFalse(sim.undo().ok)
        assertEquals(1, sim.tower.allRooms.count { it.type == Catalog.OFFICE })
    }

    @Test
    fun aRoomWithTenantsStaysBuilt() {
        val sim = towerWithLobby()
        val room = sim.undoableBuild { sim.build(Catalog.OFFICE, 1, 10) }.room!!
        sim.moveIn(room, Tenants.byId("accountant"), announce = false)
        assertEquals("Dort ist schon jemand eingezogen", sim.undoBlocked())
        assertFalse(sim.undo().ok)
    }

    @Test
    fun somethingBuiltOnTopBlocksTheUndo() {
        val sim = towerWithLobby()
        sim.undoableBuild { sim.build(Catalog.OFFICE, 1, 10) }
        // built without undo, so the office below is still the last step
        assertTrue(sim.build(Catalog.OFFICE, 2, 10).ok)
        assertEquals("Darauf steht schon etwas Neues", sim.undoBlocked())
    }

    @Test
    fun anEmptyRoomComesBackAndCostsTheRefundAgain() {
        val sim = towerWithLobby()
        val room = sim.build(Catalog.OFFICE, 1, 10).room!!
        val before = sim.money
        val refund = sim.undoableDemolish(listOf(room))
        assertEquals(before + refund, sim.money)
        assertTrue(sim.undo().ok)
        assertEquals(before, sim.money)
        val back = sim.tower.allRooms.single { it.type == Catalog.OFFICE }
        assertEquals(1 to 10, back.floor to back.x)
    }

    @Test
    fun aRentedRoomCantBeUndemolished() {
        val sim = towerWithLobby()
        val room = sim.build(Catalog.OFFICE, 1, 10).room!!
        sim.moveIn(room, Tenants.byId("accountant"), announce = false)
        sim.undoableDemolish(listOf(room))
        assertNull(sim.undoStep)
        assertFalse(sim.undo().ok)
    }

    @Test
    fun lobbiesAreNotTakenBack() {
        val sim = towerWithLobby()
        assertTrue(sim.undoableBuild { sim.build(sim.planLobby(34, 40)) }.ok)
        assertNull(sim.undoStep)
    }
}
