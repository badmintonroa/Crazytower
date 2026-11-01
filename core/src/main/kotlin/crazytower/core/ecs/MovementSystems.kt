package crazytower.core.ecs

import crazytower.core.researched

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.IteratingSystem
import com.github.quillraven.fleks.World.Companion.family
import com.github.quillraven.fleks.World.Companion.inject
import crazytower.core.Loc
import crazytower.core.Simulation
import crazytower.core.nav.Leg
import crazytower.core.nav.NavCosts
import kotlin.math.abs
import kotlin.math.sign

/**
 * Turns a [Goal] into a [Route]. Every person finds its own way through the tower. The
 * searches of a tick run side by side on the [crazytower.core.Workers]; the routes are handed
 * out afterwards in the usual order.
 */
class PathfindingSystem(
    private val sim: Simulation = inject(),
) : IteratingSystem(family { all(Position, Goal).none(Route, Stuck) }) {
    private var batch = arrayOfNulls<Entity>(64)
    private var froms = arrayOfNulls<Loc>(64)
    private var tos = arrayOfNulls<Loc>(64)
    private var results = arrayOfNulls<List<Leg>>(64)
    private var count = 0
    private var next = 0

    override fun onTick() {
        val n = family.numEntities
        if (n == 0) return
        if (batch.size < n) {
            val size = maxOf(n, batch.size * 2)
            batch = arrayOfNulls(size); froms = arrayOfNulls(size); tos = arrayOfNulls(size); results = arrayOfNulls(size)
        }
        count = 0
        family.forEach { entity ->
            val pos = entity[Position]
            batch[count] = entity
            froms[count] = Loc(pos.floor, pos.x)
            tos[count] = entity[Goal].target
            count++
        }
        sim.nav.findPaths(count, { froms[it]!! }, { tos[it]!! }, results)
        next = 0
        super.onTick() // hands out the routes in the family's order, which is the order searched in
        results.fill(null, 0, count)
    }

    override fun onTickEntity(entity: Entity) {
        // the family is the same as above: nothing changes the world between the search and here
        check(batch[next] == entity) { "the family changed between searching and handing out routes" }
        val legs = results[next++]
        entity.configure {
            if (legs == null) {
                it += Stuck(sim.clock.totalMinutes)
            } else {
                it += Route(ArrayDeque(legs))
            }
        }
    }
}

/** Moves people along their routes. Arriving removes [Route] and [Goal]. */
class MovementSystem(
    private val sim: Simulation = inject(),
) : IteratingSystem(family { all(Position, Route) }) {
    override fun onTickEntity(entity: Entity) {
        val pos = entity[Position]
        val route = entity[Route]
        var budget = deltaTime
        while (budget > 0f) {
            val leg = route.legs.firstOrNull() ?: break
            budget = when (leg) {
                is Leg.Walk -> walk(pos, leg, budget)
                is Leg.Stairs -> climb(pos, route, leg, budget)
                is Leg.Teleport -> teleport(entity, pos, route, leg, budget)
                is Leg.Ride -> {
                    // elevators move riders themselves; somebody at the doors joins the line
                    if (entity hasNo Riding) queueAt(entity, pos, leg)
                    break
                }
            }
            if (budget < 0f) {
                // teleporter misfire: the rest of the route is useless, plan again from here
                entity.configure { drop(it, Route) }
                return
            }
            if (legDone(pos, route, leg)) {
                route.legs.removeFirst()
                route.legProgress = 0f
            } else {
                break
            }
        }
        if (route.legs.isEmpty()) {
            entity.configure {
                drop(it, Route)
                drop(it, Goal)
            }
            sim.onArrived(entity)
        }
    }

    private fun queueAt(entity: Entity, pos: Position, leg: Leg.Ride) {
        val car = sim.elevators.car(leg.shaftId)
        if (car == null || leg.fromFloor !in car.floors || leg.toFloor !in car.floors) {
            entity.configure { drop(it, Route) } // plan again
            return
        }
        if (pos.floor == leg.fromFloor && abs(pos.x - leg.x) < 0.3f) car.arrived += entity
    }

    private fun walk(pos: Position, leg: Leg.Walk, budget: Float): Float {
        val distance = leg.toX - pos.x
        val step = NavCosts.WALK_SPEED * budget
        if (distance != 0f) pos.facingLeft = distance < 0f
        return if (abs(distance) <= step) {
            pos.x = leg.toX
            budget - abs(distance) / NavCosts.WALK_SPEED
        } else {
            pos.x += sign(distance) * step
            0f
        }
    }

    private fun teleport(entity: Entity, pos: Position, route: Route, leg: Leg.Teleport, budget: Float): Float {
        val needed = NavCosts.TELEPORT - route.legProgress
        if (budget < needed) {
            route.legProgress += budget
            return 0f
        }
        route.legProgress = NavCosts.TELEPORT
        val pads = sim.tower.allRooms.filter { it.type.role == crazytower.core.RoomRole.TELEPORTER }
        if (pads.size > 2 && !sim.researched("teleport") && sim.random.nextFloat() < MISFIRE_CHANCE) {
            // "Kommt meistens dort an, wo man hinwollte."
            val wrong = pads[sim.random.nextInt(pads.size)]
            pos.floor = wrong.floor
            pos.x = wrong.centerX
            sim.onMisfire(entity, wrong)
            return -1f
        }
        pos.floor = leg.toFloor
        pos.x = leg.toX
        return budget - needed
    }

    private fun climb(pos: Position, route: Route, leg: Leg.Stairs, budget: Float): Float {
        val total = NavCosts.STAIRS_PER_FLOOR * abs(leg.toFloor - leg.fromFloor)
        val needed = total - route.legProgress
        return if (budget >= needed) {
            route.legProgress = total
            pos.floor = leg.toFloor
            budget - needed
        } else {
            route.legProgress += budget
            0f
        }
    }

    private fun legDone(pos: Position, route: Route, leg: Leg): Boolean = when (leg) {
        is Leg.Walk -> pos.x == leg.toX && pos.floor == leg.floor
        is Leg.Stairs -> pos.floor == leg.toFloor
        is Leg.Teleport -> route.legProgress >= NavCosts.TELEPORT
        is Leg.Ride -> false // the elevator system removes ride legs
    }
}

private const val MISFIRE_CHANCE = 0.04f

/** Lets people who could not find a way give up after a while and try again later. */
class StuckSystem(
    private val sim: Simulation = inject(),
) : IteratingSystem(family { all(Stuck) }), Unhurried {
    override fun onTickEntity(entity: Entity) {
        if (sim.clock.totalMinutes - entity[Stuck].since > RETRY_MINUTES) {
            entity.configure {
                drop(it, Stuck)
                drop(it, Goal)
            }
            sim.onGaveUp(entity)
        }
    }

    companion object {
        const val RETRY_MINUTES = 30.0
    }
}
