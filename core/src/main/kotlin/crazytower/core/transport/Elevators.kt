package crazytower.core.transport

import com.github.quillraven.fleks.Entity
import crazytower.core.GROUND_FLOOR
import crazytower.core.Loc
import crazytower.core.RoomRole
import crazytower.core.Tower
import crazytower.core.nav.Leg
import crazytower.core.nav.Link
import crazytower.core.nav.LinkProvider
import crazytower.core.nav.NavCosts
import kotlin.math.abs

/** One cabin in a shaft. A shaft can run several, like in SimTower. */
class Cabin(var position: Float) {
    /** +1 up, -1 down, 0 idle. */
    var direction: Int = 0

    /** Minutes the doors stay open. */
    var doorTimer: Float = 0f
    val riders = ArrayList<Entity>()
}

/**
 * One elevator shaft with its cabins. A shaft is a stack of elevator modules at the same x;
 * it keeps its [id] while it grows or shrinks, so routes stay valid.
 */
class Car(val id: Int, val x: Int, var bottom: Int, var top: Int, val express: Boolean = false) {
    val cabins = arrayListOf(Cabin(bottom.toFloat()))

    /** Height of the first cabin, for news and events. */
    val position: Float get() = cabins.first().position

    /** Everybody riding in any cabin. */
    val riders: List<Entity> get() = cabins.flatMap { it.riders }

    /** The shaft doesn't move before this game minute (weddings, blackouts). */
    var blockedUntil: Double = 0.0

    /** The cabins crawl until this game minute (karaoke). */
    var slowUntil: Double = 0.0

    /** People waiting in front of the doors, per floor. Refreshed every tick by the elevator system. */
    val waiting = HashMap<Int, MutableList<Entity>>()

    /**
     * People who reached the doors during the last tick's movement. The elevator system sorts
     * them into [waiting] at the start of its tick, so it doesn't have to look at everybody who walks.
     */
    val arrived = ArrayList<Entity>()

    /** Game minute each waiting person arrived at the doors. */
    val waitingSince = HashMap<Entity, Double>()

    val centerX: Float get() = x + 1f
    val floors: IntRange get() = bottom..top

    /**
     * Floors the doors open on. An express shaft skips everything but every fifth floor above
     * ground; in the basement it picks up on every floor, for the people from the parking spaces.
     */
    val stops: List<Int> get() = if (express) floors.filter { it <= GROUND_FLOOR || it % Elevators.EXPRESS_EVERY == 0 } else floors.toList()

    /** Minutes a cabin needs per floor. */
    val minutesPerFloor: Float get() = if (express) NavCosts.ELEVATOR_PER_FLOOR / 3 else NavCosts.ELEVATOR_PER_FLOOR

    /** People per cabin. */
    val capacity: Int get() = if (express) Elevators.EXPRESS_CAPACITY else Elevators.CAPACITY
    val waitingCount: Int get() = waiting.values.sumOf { it.size }

    /** Rough minutes until a new passenger is picked up, used as path cost. */
    fun waitEstimate(): Float = estimate

    /**
     * The path search asks for the wait on every elevator link it looks at, so it is worked
     * out once per tick by the elevator system instead of counting the queues each time.
     */
    private var estimate: Float = Elevators.WAIT_BASE

    fun refreshEstimate() {
        val riding = cabins.sumOf { it.riders.size }
        estimate = (Elevators.WAIT_BASE + waitingCount * 0.15f / cabins.size + riding * 0.1f / cabins.size) / cabins.size.coerceAtMost(3)
    }

    /**
     * Adds or removes cabins until there are [count]. New cabins start spread over the shaft;
     * only empty cabins are taken away.
     */
    fun setCabins(count: Int) {
        val want = count.coerceIn(1, Elevators.MAX_CABINS)
        while (cabins.size < want) {
            val i = cabins.size
            val span = (top - bottom).coerceAtLeast(0)
            cabins += Cabin((bottom + span * i / want).toFloat())
        }
        while (cabins.size > want) {
            val empty = cabins.lastOrNull { it.riders.isEmpty() } ?: return
            cabins.remove(empty)
        }
    }
}

/** Keeps one [Car] per elevator shaft in sync with the tower and offers rides to the navigation graph. */
class Elevators(private val tower: Tower) : LinkProvider {
    private var syncedVersion = -1
    private var nextId = 1
    private val byId = LinkedHashMap<Int, Car>()

    /** All cars, up to date with the tower. */
    val cars: Map<Int, Car>
        get() {
            sync()
            return byId
        }

    /** Cars whose shaft was torn down since the last call; the elevator system drops their riders. */
    val removed = ArrayList<Car>()

    /** The cars by id once more, asked for by everybody at the doors, so without boxing. */
    private var byIndex = arrayOfNulls<Car>(0)

    fun car(id: Int): Car? {
        sync()
        return byIndex.getOrNull(id)
    }

    fun sync() {
        if (syncedVersion == tower.version) return
        syncedVersion = tower.version
        val shafts = ArrayList<Triple<Int, IntRange, Boolean>>()
        val seen = HashSet<Int>()
        for (room in tower.allRooms) {
            if (room.type.role != RoomRole.ELEVATOR || room.id in seen) continue
            val range = tower.shaftRange(room.floor, room.x, room.type)
            for (f in range) tower.wayAt(f, room.x)?.let { seen += it.id }
            shafts += Triple(room.x, range, room.type.express)
        }
        val old = byId.values.toMutableList()
        byId.clear()
        for ((x, range, express) in shafts.sortedBy { it.first }) {
            val match = old.firstOrNull { it.x == x && it.express == express && it.bottom <= range.last && it.top >= range.first }
            val car = if (match != null) {
                old.remove(match)
                match.bottom = range.first
                match.top = range.last
                for (cabin in match.cabins) cabin.position = cabin.position.coerceIn(range.first.toFloat(), range.last.toFloat())
                match
            } else {
                Car(nextId++, x, range.first, range.last, express)
            }
            car.setCabins(range.maxOf { tower.wayAt(it, x)?.cabins ?: 1 })
            byId[car.id] = car
        }
        byIndex = arrayOfNulls(nextId)
        for (car in byId.values) byIndex[car.id] = car
        removed += old
    }

    override fun links(tower: Tower): List<Link> {
        sync()
        val links = ArrayList<Link>()
        for (car in byId.values) {
            val stops = car.stops
            for (a in stops) for (b in stops) {
                if (a == b) continue
                val ride = abs(a - b) * car.minutesPerFloor
                links += Link(Loc(a, car.centerX), Loc(b, car.centerX), Leg.Ride(car.id, a, b, car.centerX)) {
                    car.waitEstimate() + ride
                }
            }
        }
        return links
    }

    companion object {
        const val WAIT_BASE = 1.5f
        /** People per cabin. Cabins are big and slow, like in the original. */
        const val CAPACITY = 16

        /** Express cabins are packed tight. */
        const val EXPRESS_CAPACITY = 40

        /** An express elevator stops on floors divisible by this. */
        const val EXPRESS_EVERY = 5
        const val DOOR_MINUTES = 0.5f
        const val MAX_CABINS = 12
        const val CABIN_COST = 4_000L

        /** Karaoke makes the cabins this many times slower. */
        const val KARAOKE_SLOWDOWN = 3f

        /** How many cabins a shaft may have at a star rating: two more per star, twelve at five stars. */
        fun maxCabins(stars: Int): Int = if (stars >= 5) MAX_CABINS else (stars * 2).coerceIn(2, 8)
    }
}

/** Stairs connect their floor with the one above, if there is walkable floor up there. */
object StairsLinks : LinkProvider {
    override fun links(tower: Tower): List<Link> = tower.allRooms
        .filter { it.type.role == RoomRole.STAIRS }
        .filter { tower.isWalkable(it.floor + 1, it.x) && tower.isWalkable(it.floor + 1, it.x + 1) }
        .flatMap { room ->
            val x = room.centerX
            val cost = { NavCosts.STAIRS_PER_FLOOR }
            listOf(
                Link(Loc(room.floor, x), Loc(room.floor + 1, x), Leg.Stairs(room.floor, room.floor + 1, x), cost),
                Link(Loc(room.floor + 1, x), Loc(room.floor, x), Leg.Stairs(room.floor + 1, room.floor, x), cost),
            )
        }
}

/** Every teleporter links to every other one. Fast, but it costs nerves (and sometimes the floor). */
object TeleporterLinks : LinkProvider {
    override fun links(tower: Tower): List<Link> {
        val pads = tower.allRooms.filter { it.type.role == RoomRole.TELEPORTER }
        val links = ArrayList<Link>()
        for (a in pads) for (b in pads) {
            if (a === b) continue
            links += Link(a.center, b.center, Leg.Teleport(a.floor, b.floor, b.centerX)) { NavCosts.TELEPORT }
        }
        return links
    }
}
