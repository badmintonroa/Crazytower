package crazytower.core

import crazytower.core.nav.Leg
import crazytower.core.nav.Link
import crazytower.core.nav.LinkProvider
import crazytower.core.nav.NavGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavGraphTest {
    private val floorType = Catalog.LOBBY.copy(id = "test-floor", role = RoomRole.LOBBY, width = 6)

    /** A tower with rooms on floor 1 and 2 placed directly into the grid (no placement rules). */
    private fun towerWithFloors(): Tower {
        val tower = Tower(width = 30)
        tower.place(Catalog.LOBBY, 0, 0)
        return tower
    }

    @Test
    fun walksStraightOnTheSameFloor() {
        val nav = NavGraph(towerWithFloors())
        assertEquals(listOf(Leg.Walk(0, 12f)), nav.findPath(Loc(0, 2f), Loc(0, 12f)))
    }

    @Test
    fun standingOnTheGoalNeedsNoLegs() {
        val nav = NavGraph(towerWithFloors())
        assertEquals(emptyList(), nav.findPath(Loc(0, 2f), Loc(0, 2f)))
    }

    @Test
    fun unreachableFloorGivesNull() {
        val nav = NavGraph(towerWithFloors())
        assertNull(nav.findPath(Loc(0, 2f), Loc(3, 2f)))
    }

    @Test
    fun usesTheCheapestLinkBetweenFloors() {
        val tower = Tower(width = 30)
        // Floor 1 is made walkable by a wide fake room; placement rules don't matter here.
        val wide = floorType.copy(width = 30)
        val upper = TowerTestAccess.forcePlace(tower, wide, 1, 0)
        check(upper.width == 30)
        val slow = Leg.Stairs(0, 1, 2f)
        val fast = Leg.Stairs(0, 1, 20f)
        val nav = NavGraph(tower, listOf(LinkProvider {
            listOf(
                Link(Loc(0, 2f), Loc(1, 2f), slow) { 50f },
                Link(Loc(0, 20f), Loc(1, 20f), fast) { 1f },
            )
        }))
        val path = nav.findPath(Loc(0, 4f), Loc(1, 22f))
        assertEquals(listOf(Leg.Walk(0, 20f), fast, Leg.Walk(1, 22f)), path)
    }
}

/** Places a room without the gameplay rules, for navigation tests. */
object TowerTestAccess {
    fun forcePlace(tower: Tower, type: RoomType, floor: Int, x: Int): Room = tower.placeUnchecked(type, floor, x)
}
