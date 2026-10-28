package crazytower.core

/** A placed room. Its left edge is at segment [x] on [floor]. */
class Room(val id: Int, val type: RoomType, val floor: Int, val x: Int) {
    val width: Int get() = type.width
    val right: Int get() = x + width
    val centerX: Float get() = x + width / 2f
    fun covers(floor: Int, segment: Int) = floor == this.floor && segment >= x && segment < right
}

/** Result of a placement check. [reason] is shown to the player when the spot is invalid. */
data class PlaceCheck(val ok: Boolean, val reason: String = "") {
    companion object {
        val OK = PlaceCheck(true)
        fun no(reason: String) = PlaceCheck(false, reason)
    }
}

/**
 * The building grid. Floor 0 is street level: the sidewalk there is always walkable,
 * every other floor is only walkable where something has been built.
 */
class Tower(val width: Int = DEFAULT_WIDTH) {
    private val rooms = LinkedHashMap<Int, Room>()
    private val floors = HashMap<Int, IntArray>()
    private var nextId = 1

    /** Increases on every change, so caches such as the navigation graph know when to rebuild. */
    var version: Int = 0
        private set

    val allRooms: Collection<Room> get() = rooms.values

    fun room(id: Int): Room? = rooms[id]

    fun roomAt(floor: Int, segment: Int): Room? {
        val row = floors[floor] ?: return null
        if (segment !in 0 until width) return null
        val id = row[segment]
        return if (id == 0) null else rooms[id]
    }

    fun isBuilt(floor: Int, segment: Int): Boolean = roomAt(floor, segment) != null

    fun isWalkable(floor: Int, segment: Int): Boolean =
        segment in 0 until width && (floor == GROUND_FLOOR || isBuilt(floor, segment))

    /** Contiguous walkable ranges of a floor, in segments. */
    fun walkableSpans(floor: Int): List<IntRange> {
        if (floor == GROUND_FLOOR) return listOf(0 until width)
        val spans = ArrayList<IntRange>()
        var start = -1
        for (s in 0..width) {
            val walkable = s < width && isWalkable(floor, s)
            if (walkable && start < 0) start = s
            if (!walkable && start >= 0) {
                spans += start until s
                start = -1
            }
        }
        return spans
    }

    val builtFloors: Set<Int> get() = floors.filterValues { row -> row.any { it != 0 } }.keys

    fun canPlace(type: RoomType, floor: Int, x: Int): PlaceCheck {
        if (x < 0 || x + type.width > width) return PlaceCheck.no("Außerhalb des Grundstücks")
        if (type.role == RoomRole.LOBBY && floor != GROUND_FLOOR) return PlaceCheck.no("Lobby nur im Erdgeschoss")
        for (s in x until x + type.width) {
            if (isBuilt(floor, s)) return PlaceCheck.no("Hier steht schon etwas")
        }
        return PlaceCheck.OK
    }

    fun place(type: RoomType, floor: Int, x: Int): Room {
        val check = canPlace(type, floor, x)
        require(check.ok) { check.reason }
        return placeUnchecked(type, floor, x)
    }

    /** Places a room without checking the rules; used when loading saves and in tests. */
    internal fun placeUnchecked(type: RoomType, floor: Int, x: Int, id: Int = nextId): Room {
        val room = Room(id, type, floor, x)
        nextId = maxOf(nextId, id + 1)
        rooms[room.id] = room
        val row = floors.getOrPut(floor) { IntArray(width) }
        for (s in room.x until room.right) row[s] = room.id
        version++
        return room
    }

    fun remove(id: Int): Room? {
        val room = rooms.remove(id) ?: return null
        val row = floors.getValue(room.floor)
        for (s in room.x until room.right) row[s] = 0
        version++
        return room
    }

    companion object {
        const val DEFAULT_WIDTH = 64
    }
}
