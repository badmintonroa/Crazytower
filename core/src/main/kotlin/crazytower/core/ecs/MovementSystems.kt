package crazytower.core.ecs

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

/** Turns a [Goal] into a [Route]. Every person finds its own way through the tower. */
class PathfindingSystem(
    private val sim: Simulation = inject(),
) : IteratingSystem(family { all(Position, Goal).none(Route, Stuck) }) {
    override fun onTickEntity(entity: Entity) {
        val pos = entity[Position]
        val legs = sim.nav.findPath(Loc(pos.floor, pos.x), entity[Goal].target)
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
                is Leg.Ride -> break // elevators move riders themselves
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
                it -= Route
                it -= Goal
            }
            sim.onArrived(entity)
        }
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
        is Leg.Ride -> false // the elevator system removes ride legs
    }
}

/** Lets people who could not find a way give up after a while and try again later. */
class StuckSystem(
    private val sim: Simulation = inject(),
) : IteratingSystem(family { all(Stuck) }) {
    override fun onTickEntity(entity: Entity) {
        if (sim.clock.totalMinutes - entity[Stuck].since > RETRY_MINUTES) {
            entity.configure {
                it -= Stuck
                it -= Goal
            }
            sim.onGaveUp(entity)
        }
    }

    companion object {
        const val RETRY_MINUTES = 30.0
    }
}
