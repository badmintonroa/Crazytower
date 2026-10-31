package crazytower.core

/** A placed room. Its left edge is at segment [x] on [floor]; only stretchable rooms differ from the type's width. */
class Room(val id: Int, val type: RoomType, val floor: Int, val x: Int, val width: Int = type.width) {
    val right: Int get() = x + width
    val centerX: Float get() = x + width / 2f
    val center: Loc get() = Loc(floor, centerX)
    fun covers(floor: Int, segment: Int) = floor == this.floor && segment >= x && segment < right

    /** Who rents the room, null while it is empty. Only for rentable roles. */
    var tenant: TenantKind? = null

    /** Game minute the tenant moved in, or the room became empty. */
    var since: Double = 0.0

    /** Offices and restaurants: end of the last working day the rent was paid for. */
    var paidUntil: Double = 0.0

    /** Homes: what the residents paid for the flat; half of it goes back when they leave unhappy. */
    var salePaid: Long = 0

    /** What the room cost when it was built, for rooms whose price changes over time; 0 for the list price. */
    var paid: Long = 0

    /** Homes: the flat was sold once. Later residents buy it from the previous ones, not from the tower. */
    var sold: Boolean = false

    /** Hotel rooms: the last guests have left and nobody has cleaned up yet. */
    var dirty: Boolean = false

    /** Elevators: cabins in the shaft (every module of a shaft carries the same number). */
    var cabins: Int = 1

    /** Rentable rooms: how far the room was done up, 0 to [Upgrades.MAX_LEVEL], see [Upgrades]. */
    var upgrade: Int = 0

    /** Elevators: tuning level of the shaft, 0 to [Tuning.MAX_LEVEL] (every module carries the same). */
    var tuning: Int = 0
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
 *
 * There are two layers, as in the original: rooms, and in front of them the ways (stairs
 * and elevators). A shaft may run through an office; the office keeps working behind it.
 */
class Tower(val width: Int = DEFAULT_WIDTH) {
    private val rooms = LinkedHashMap<Int, Room>()
    private val floors = HashMap<Int, IntArray>()
    private val wayFloors = HashMap<Int, IntArray>()
    private var nextId = 1

    private fun layer(type: RoomType) = if (type.role.isWay) wayFloors else floors

    /** Increases on every change, so caches such as the navigation graph know when to rebuild. */
    var version: Int = 0
        private set

    /** Marks a change that is not a build or a demolition (an elevator got faster), so caches rebuild. */
    fun touch() {
        version++
    }

    /**
     * Increases only when something is taken away, so routes may lead through rooms that are
     * gone. Building only adds ways, and the old routes stay good.
     */
    var removedVersion: Int = 0
        private set

    val allRooms: Collection<Room> get() = rooms.values

    fun room(id: Int): Room? = rooms[id]

    /** The room (not a way) covering a segment. */
    fun roomAt(floor: Int, segment: Int): Room? = at(floors, floor, segment)

    /** The stairs or elevator module covering a segment. */
    fun wayAt(floor: Int, segment: Int): Room? = at(wayFloors, floor, segment)

    /** What the player sees at a segment: the way in front, else the room behind it. */
    fun topAt(floor: Int, segment: Int): Room? = wayAt(floor, segment) ?: roomAt(floor, segment)

    private fun at(layer: Map<Int, IntArray>, floor: Int, segment: Int): Room? {
        val row = layer[floor] ?: return null
        if (segment !in 0 until width) return null
        val id = row[segment]
        return if (id == 0) null else rooms[id]
    }

    fun isBuilt(floor: Int, segment: Int): Boolean = roomAt(floor, segment) != null || wayAt(floor, segment) != null

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

    val builtFloors: Set<Int> get() = (floors.filterValues { row -> row.any { it != 0 } }.keys +
        wayFloors.filterValues { row -> row.any { it != 0 } }.keys)

    /**
     * Whether a room of [type] fits at [x] on [floor]. [assumed] lists floors where the same
     * column is treated as already built, so a whole dragged column can be checked before building.
     */
    fun canPlace(type: RoomType, floor: Int, x: Int, width: Int = type.width, assumed: Set<Int> = emptySet()): PlaceCheck {
        if (width < 1 || x < 0 || x + width > this.width) return PlaceCheck.no(tr("Außerhalb des Grundstücks", "Outside the lot"))
        if (floor < MIN_FLOOR) return PlaceCheck.no(tr("Tiefer geht es nicht", "Can't dig any deeper"))
        if (type.role == RoomRole.LOBBY && !isLobbyFloor(floor)) return PlaceCheck.no(tr("Lobby nur im Erdgeschoss und alle $SKY_LOBBY_EVERY Etagen", "Lobbies only on the ground floor and every $SKY_LOBBY_EVERY floors"))
        if (floor == GROUND_FLOOR && type.role != RoomRole.LOBBY && !type.role.isWay) {
            return PlaceCheck.no(tr("Im Erdgeschoss ist nur Platz für die Lobby", "The ground floor is for the lobby only"))
        }
        val layer = layer(type)
        for (s in x until x + width) {
            if (at(layer, floor, s) != null) return PlaceCheck.no(tr("Hier steht schon etwas", "Something is already here"))
        }
        val support = supportFloor(floor)
        if (support != null && support !in assumed && (x until x + width).any { !isBuilt(support, it) }) {
            return PlaceCheck.no(if (floor > 0) tr("Darunter fehlt der Unterbau", "Nothing to stand on below") else tr("Darüber fehlt die Decke", "No ceiling above"))
        }
        if (type.role == RoomRole.ELEVATOR) {
            val shaft = shaftRange(floor, x, type, extraFloors = assumed + floor)
            val maxFloors = if (type.express) MAX_EXPRESS_FLOORS else MAX_SHAFT_FLOORS
            if (shaft.last - shaft.first + 1 > maxFloors) {
                return PlaceCheck.no(tr("${type.name} höchstens $maxFloors Etagen", "${type.name}: $maxFloors floors at most"))
            }
            // shafts keep at least two rooms' width apart, on this floor and the ones next to it
            val crowded = (floor - 1..floor + 1).any { f ->
                (x - ELEVATOR_GAP until x + width + ELEVATOR_GAP).any { s ->
                    wayAt(f, s)?.let { it.type.role == RoomRole.ELEVATOR && (it.x != x || it.type != type) } == true
                }
            }
            if (crowded) return PlaceCheck.no(tr("Zu nah am nächsten Aufzug (mind. $ELEVATOR_GAP Felder Abstand)", "Too close to the next elevator (at least $ELEVATOR_GAP tiles apart)"))
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

    /**
     * Rooms resting on [room], which would fall down if it was removed. A segment that is
     * also carried by the other layer (a shaft running through the room) stays up.
     */
    fun dependents(room: Room): List<Room> {
        val resting = if (room.floor >= GROUND_FLOOR) room.floor + 1 else room.floor - 1
        val other = if (room.type.role.isWay) floors else wayFloors
        return (room.x until room.right)
            .filter { at(other, room.floor, it) == null }
            .flatMap { listOfNotNull(roomAt(resting, it), wayAt(resting, it)) }
            .distinct()
    }

    fun canRemove(room: Room): PlaceCheck =
        if (dependents(room).isEmpty()) PlaceCheck.OK else PlaceCheck.no(tr("Darauf steht noch etwas", "Something still stands on it"))

    /**
     * Floor range of the elevator shaft through segment [x] on [floor], counting stacked
     * modules of the same [type] at the same x. [extraFloors] are treated as modules (for placement checks).
     */
    fun shaftRange(floor: Int, x: Int, type: RoomType, extraFloors: Set<Int> = emptySet()): IntRange {
        fun isModule(f: Int): Boolean {
            if (f in extraFloors) return true
            val r = wayAt(f, x) ?: return false
            return r.type == type && r.x == x
        }
        var bottom = floor
        while (isModule(bottom - 1)) bottom--
        var top = floor
        while (isModule(top + 1)) top++
        return bottom..top
    }

    fun place(type: RoomType, floor: Int, x: Int, width: Int = type.width): Room {
        val check = canPlace(type, floor, x, width)
        require(check.ok) { check.reason }
        return placeUnchecked(type, floor, x, width = width)
    }

    /** Places a room without checking the rules; used when loading saves and in tests. */
    internal fun placeUnchecked(type: RoomType, floor: Int, x: Int, id: Int = nextId, width: Int = type.width): Room {
        val room = Room(id, type, floor, x, width)
        nextId = maxOf(nextId, id + 1)
        rooms[room.id] = room
        val row = layer(type).getOrPut(floor) { IntArray(this.width) }
        for (s in room.x until room.right) row[s] = room.id
        version++
        return room
    }

    fun remove(id: Int): Room? = remove(id, replaced = false)

    /** Takes a room away; a [replaced] room comes straight back bigger, so no route breaks. */
    private fun remove(id: Int, replaced: Boolean): Room? {
        val room = rooms.remove(id) ?: return null
        val row = layer(room.type).getValue(room.floor)
        for (s in room.x until room.right) row[s] = 0
        version++
        if (!replaced) removedVersion++
        return room
    }

    /**
     * Joins touching lobbies on the same floor into one, so a dragged lobby grows the old
     * one instead of standing next to it. Returns the joined lobby, or null if nothing changed.
     */
    fun mergeLobbies(): Room? {
        val lobbies = rooms.values.filter { it.type.role == RoomRole.LOBBY }.sortedWith(compareBy({ it.floor }, { it.x }))
        var joined: Room? = null
        var run = ArrayList<Room>()
        fun flush() {
            if (run.size > 1) {
                val first = run.first()
                run.forEach { remove(it.id, replaced = true) }
                joined = placeUnchecked(first.type, first.floor, first.x, first.id, run.last().right - first.x)
                    .also { it.since = first.since }
            }
            run = ArrayList()
        }
        for (lobby in lobbies) {
            if (run.isNotEmpty() && (run.last().floor != lobby.floor || run.last().right != lobby.x)) flush()
            run += lobby
        }
        flush()
        return joined
    }

    companion object {
        /** Laid out for the widest lot; the simulation limits building to the current [Lot]. */
        val DEFAULT_WIDTH = Lot.maxWidth
        const val MIN_FLOOR = -3
        const val MAX_SHAFT_FLOORS = 25
        const val MAX_EXPRESS_FLOORS = 100

        /**
         * Besides the ground floor, a sky lobby goes on every 25th floor, and it must stand
         * before anything is built above it.
         */
        const val SKY_LOBBY_EVERY = 25

        fun isLobbyFloor(floor: Int) = floor == GROUND_FLOOR || (floor > GROUND_FLOOR && floor % SKY_LOBBY_EVERY == 0)

        /** The sky lobby floors below [floor]: each needs a lobby before [floor] may be built on. */
        fun skyLobbiesBelow(floor: Int): IntProgression = SKY_LOBBY_EVERY until floor step SKY_LOBBY_EVERY

        /** Free segments needed between two elevator shafts. */
        const val ELEVATOR_GAP = 12
    }
}
