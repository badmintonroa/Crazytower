package crazytower.core.ecs

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.IntervalSystem
import com.github.quillraven.fleks.IteratingSystem
import com.github.quillraven.fleks.World.Companion.family
import com.github.quillraven.fleks.World.Companion.inject
import crazytower.core.Loc
import crazytower.core.RoomRole
import crazytower.core.Simulation

/** Ends [Idle] once its time is up. */
class IdleSystem(
    private val sim: Simulation = inject(),
) : IteratingSystem(family { all(Idle) }) {
    override fun onTickEntity(entity: Entity) {
        if (sim.clock.totalMinutes >= entity[Idle].until) entity.configure { drop(it, Idle) }
    }
}

/** Brings sightseers in from the street while the lobby is open. */
class VisitorSpawnSystem(
    private val sim: Simulation = inject(),
) : IntervalSystem() {
    private val visitors = family { all(Visitor) }
    private var cooldown = 0f

    override fun onTick() {
        cooldown -= deltaTime
        if (cooldown > 0f) return
        // one "lobby" per six segments, however the player has dragged them
        val lobbies = (sim.tower.allRooms.filter { it.type.role == RoomRole.LOBBY }.sumOf { it.width } + 5) / 6
        val hour = sim.clock.hour
        val limit = maxVisitors(sim, lobbies)
        if (lobbies == 0 || hour < 7 || hour >= 22 || visitors.numEntities >= limit) {
            cooldown = 5f
            return
        }
        cooldown = (SPAWN_EVERY * 4 / limit).coerceAtLeast(0.5f) * (0.5f + sim.random.nextFloat())
        sim.spawnVisitor()
    }

    companion object {
        const val SPAWN_EVERY = 6f
        const val MAX_PER_LOBBY = 8
        /** A few curious people come to an empty tower, then three more for every rented office or restaurant. */
        const val BASE_VISITORS = 3
        const val VISITORS_PER_TENANT = 3

        /** How many sightseers may be in the tower: grows with what there is to see, not with the lobby alone. */
        fun maxVisitors(sim: Simulation, lobbies: Int): Int {
            val rented = sim.tower.allRooms.count { it.tenant != null && it.type.role in VISITABLE }
            return minOf(MAX_PER_LOBBY * lobbies, BASE_VISITORS + VISITORS_PER_TENANT * rented)
        }
    }
}

private val VISITABLE = setOf(RoomRole.FOOD, RoomRole.OFFICE)
private val SIGHTS = setOf(RoomRole.LOBBY, RoomRole.SPECIAL)

/** Picks the next stop for idle sightseers, or sends them home when they have seen enough. */
class VisitorSystem(
    private val sim: Simulation = inject(),
) : IteratingSystem(family { all(Visitor, Position).none(Goal, Route, Idle, Stuck) }) {
    override fun onTickEntity(entity: Entity) {
        val visitor = entity[Visitor]
        val lobbies = sim.tower.allRooms.filter { it.type.role == RoomRole.LOBBY }
        if (visitor.stopsLeft <= 0 || lobbies.isEmpty()) {
            visitor.leaving = true
            entity.configure { it += Goal(sim.streetExit(entity[Position].x)) }
            return
        }
        visitor.stopsLeft--
        // Sightseers look at the lobby, eat something or bother an office.
        val sights = sim.tower.allRooms.filter {
            it.type.role in SIGHTS || (it.tenant != null && it.type.role in VISITABLE)
        }
        val stop = sights[sim.random.nextInt(sights.size)]
        val x = stop.x + 0.5f + sim.random.nextFloat() * (stop.width - 1f)
        entity.configure { it += Goal(Loc(stop.floor, x)) }
    }
}
