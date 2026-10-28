package crazytower.core.transport

import com.github.quillraven.fleks.Entity
import crazytower.core.Loc
import crazytower.core.RoomRole
import crazytower.core.Tower
import crazytower.core.nav.Leg
import crazytower.core.nav.Link
import crazytower.core.nav.LinkProvider
import crazytower.core.nav.NavCosts
import kotlin.math.abs

/**
 * The car of one elevator shaft. A shaft is a stack of elevator modules at the same x;
 * the car keeps its [id] while the shaft grows or shrinks, so routes stay valid.
 */
class Car(val id: Int, val x: Int, var bottom: Int, var top: Int) {
    /** Current height in floors; fractional while moving. */
    var position: Float = bottom.toFloat()

    /** +1 up, -1 down, 0 idle. */
    var direction: Int = 0

    /** Minutes the doors stay open. */
    var doorTimer: Float = 0f
    val riders = ArrayList<Entity>()

    /** People waiting in front of the doors, per floor. Refreshed every tick by the elevator system. */
    val waiting = HashMap<Int, MutableList<Entity>>()

    /** Game minute each waiting person arrived at the doors. */
    val waitingSince = HashMap<Entity, Double>()

    val centerX: Float get() = x + 1f
    val floors: IntRange get() = bottom..top
    val waitingCount: Int get() = waiting.values.sumOf { it.size }

    /** Rough minutes until a new passenger is picked up, used as path cost. */
    fun waitEstimate(): Float = Elevators.WAIT_BASE + waitingCount * 0.15f + riders.size * 0.1f
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

    fun car(id: Int): Car? {
        sync()
        return byId[id]
    }

    fun sync() {
        if (syncedVersion == tower.version) return
        syncedVersion = tower.version
        val shafts = ArrayList<Pair<Int, IntRange>>()
        val seen = HashSet<Int>()
        for (room in tower.allRooms) {
            if (room.type.role != RoomRole.ELEVATOR || room.id in seen) continue
            val range = tower.shaftRange(room.floor, room.x)
            for (f in range) tower.roomAt(f, room.x)?.let { seen += it.id }
            shafts += room.x to range
        }
        val old = byId.values.toMutableList()
        byId.clear()
        for ((x, range) in shafts.sortedBy { it.first }) {
            val match = old.firstOrNull { it.x == x && it.bottom <= range.last && it.top >= range.first }
            val car = if (match != null) {
                old.remove(match)
                match.bottom = range.first
                match.top = range.last
                match.position = match.position.coerceIn(range.first.toFloat(), range.last.toFloat())
                match
            } else {
                Car(nextId++, x, range.first, range.last)
            }
            byId[car.id] = car
        }
        removed += old
    }

    override fun links(tower: Tower): List<Link> {
        sync()
        val links = ArrayList<Link>()
        for (car in byId.values) {
            if (car.bottom == car.top) continue
            for (a in car.floors) for (b in car.floors) {
                if (a == b) continue
                val ride = abs(a - b) * NavCosts.ELEVATOR_PER_FLOOR
                links += Link(Loc(a, car.centerX), Loc(b, car.centerX), Leg.Ride(car.id, a, b, car.centerX)) {
                    car.waitEstimate() + ride
                }
            }
        }
        return links
    }

    companion object {
        const val WAIT_BASE = 1.5f
        const val CAPACITY = 10
        const val DOOR_MINUTES = 0.4f
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
