package crazytower.core.ecs

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.IntervalSystem
import com.github.quillraven.fleks.IteratingSystem
import com.github.quillraven.fleks.World.Companion.family
import com.github.quillraven.fleks.World.Companion.inject
import crazytower.core.GROUND_FLOOR
import crazytower.core.Loc
import crazytower.core.RoomRole
import crazytower.core.Simulation

/** Ends [Idle] once its time is up. */
class IdleSystem(
    private val sim: Simulation = inject(),
) : IteratingSystem(family { all(Idle) }) {
    override fun onTickEntity(entity: Entity) {
        if (sim.clock.totalMinutes >= entity[Idle].until) entity.configure { it -= Idle }
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
        val lobbies = sim.tower.allRooms.count { it.type.role == RoomRole.LOBBY }
        val hour = sim.clock.hour
        if (lobbies == 0 || hour < 7 || hour >= 22 || visitors.numEntities >= MAX_PER_LOBBY * lobbies) {
            cooldown = 5f
            return
        }
        cooldown = (SPAWN_EVERY / lobbies).coerceAtLeast(0.5f) * (0.5f + sim.random.nextFloat())
        sim.spawnVisitor()
    }

    companion object {
        const val SPAWN_EVERY = 6f
        const val MAX_PER_LOBBY = 8
    }
}

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
        val lobby = lobbies[sim.random.nextInt(lobbies.size)]
        val x = lobby.x + 0.5f + sim.random.nextFloat() * (lobby.width - 1f)
        entity.configure { it += Goal(Loc(GROUND_FLOOR, x)) }
    }
}
