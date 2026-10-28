package crazytower.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TowerTest {
    @Test
    fun lobbyOnlyOnGroundFloor() {
        val tower = Tower()
        assertTrue(tower.canPlace(Catalog.LOBBY, 0, 10).ok)
        assertEquals("Lobby nur im Erdgeschoss", tower.canPlace(Catalog.LOBBY, 1, 10).reason)
    }

    @Test
    fun roomsCannotOverlapOrLeaveTheLot() {
        val tower = Tower(width = 20)
        tower.place(Catalog.LOBBY, 0, 4)
        assertFalse(tower.canPlace(Catalog.LOBBY, 0, 8).ok)
        assertFalse(tower.canPlace(Catalog.LOBBY, 0, 16).ok)
        assertTrue(tower.canPlace(Catalog.LOBBY, 0, 10).ok)
    }

    @Test
    fun removingFreesTheSegments() {
        val tower = Tower()
        val room = tower.place(Catalog.LOBBY, 0, 0)
        val version = tower.version
        tower.remove(room.id)
        assertEquals(null, tower.roomAt(0, 2))
        assertTrue(tower.version > version)
    }

    @Test
    fun groundFloorIsAlwaysWalkable() {
        val tower = Tower(width = 10)
        assertEquals(listOf(0 until 10), tower.walkableSpans(0))
        assertEquals(emptyList(), tower.walkableSpans(1))
    }
}
