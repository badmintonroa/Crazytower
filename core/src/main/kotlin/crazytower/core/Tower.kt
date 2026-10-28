package crazytower.core

/** A placed room. Its left edge is at segment [x] on [floor]. */
class Room(val id: Int, val type: RoomType, val floor: Int, val x: Int) {
    val width: Int get() = type.width
    val right: Int get() = x + width
    val centerX: Float get() = x + width / 2f
    val center: Loc get() = Loc(floor, centerX)
    fun covers(floor: Int, segment: Int) = floor == this.floor && segment >= x && segment < right

    /** Who rents the room, null while it is empty. Only for rentable roles. */
    var tenant: TenantKind? = null

    /** Game minute the tenant moved in, or the room became empty. */
    var since: Double = 0.0
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
        if (floor < MIN_FLOOR) return PlaceCheck.no("Tiefer geht es nicht")
        if (type.role == RoomRole.LOBBY && floor != GROUND_FLOOR) return PlaceCheck.no("Lobby nur im Erdgeschoss")
        for (s in x until x + type.width) {
            if (isBuilt(floor, s)) return PlaceCheck.no("Hier steht schon etwas")
        }
        val support = supportFloor(floor)
        if (support != null && (x until x + type.width).any { !isBuilt(support, it) }) {
            return PlaceCheck.no(if (floor > 0) "Darunter fehlt der Unterbau" else "Darüber fehlt die Decke")
        }
        if (type.role == RoomRole.ELEVATOR) {
            val shaft = shaftRange(floor, x, extraFloor = floor)
            if (shaft.last - shaft.first + 1 > MAX_SHAFT_FLOORS) {
                return PlaceCheck.no("Aufzug höchstens $MAX_SHAFT_FLOORS Etagen")
            }
        }
        return PlaceCheck.OK
    }

    /**
     * The floor a room on [floor] rests on: the one below above ground, the one above in the
     * basement. Ground floor and the first basement need nothing.
     */
    private fun supportFloor(floor: Int): Int? = when {
        floor > GROUND_FLOOR -> floor - 1
        floor < -1 -> floor + 1
        else -> null
    }

    /** Rooms resting on [room], which would fall down if it was removed. */
    fun dependents(room: Room): List<Room> {
        val resting = if (room.floor >= GROUND_FLOOR) room.floor + 1 else room.floor - 1
        return (room.x until room.right).mapNotNull { roomAt(resting, it) }.distinct()
    }

    fun canRemove(room: Room): PlaceCheck =
        if (dependents(room).isEmpty()) PlaceCheck.OK else PlaceCheck.no("Darauf steht noch etwas")

    /**
     * Floor range of the elevator shaft through segment [x] on [floor], counting stacked
     * elevator modules at the same x. [extraFloor] is treated as a module (for placement checks).
     */
    fun shaftRange(floor: Int, x: Int, extraFloor: Int? = null): IntRange {
        fun isModule(f: Int): Boolean {
            if (f == extraFloor) return true
            val r = roomAt(f, x) ?: return false
            return r.type.role == RoomRole.ELEVATOR && r.x == x
        }
        var bottom = floor
        while (isModule(bottom - 1)) bottom--
        var top = floor
        while (isModule(top + 1)) top++
        return bottom..top
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
        const val MIN_FLOOR = -3
        const val MAX_SHAFT_FLOORS = 30
    }
}
