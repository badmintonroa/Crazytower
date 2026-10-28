package crazytower.core.ecs

import com.github.quillraven.fleks.IntervalSystem
import com.github.quillraven.fleks.World.Companion.inject
import crazytower.core.GROUND_FLOOR
import crazytower.core.Loc
import crazytower.core.Simulation
import crazytower.core.Tenants

/**
 * Lets tenants move into empty offices, homes and restaurants. A room only gets rented once
 * people can actually reach it from the street, so upper floors need stairs or an elevator.
 */
class TenantSystem(
    private val sim: Simulation = inject(),
) : IntervalSystem() {
    private var untilCheck = 0f

    override fun onTick() {
        untilCheck -= deltaTime
        if (untilCheck > 0f) return
        untilCheck = CHECK_EVERY
        val now = sim.clock.totalMinutes
        val street = Loc(GROUND_FLOOR, 0f)
        for (room in sim.tower.allRooms) {
            if (!room.type.role.rentable || room.tenant != null) continue
            if (now - room.since < VACANCY_MINUTES || sim.random.nextFloat() > MOVE_IN_CHANCE) continue
            if (sim.nav.findPath(street, room.center) == null) continue
            val candidates = Tenants.candidates(room.type.role, sim.stars, sim.stage)
            if (candidates.isEmpty()) continue
            // a tower full of identical tenants is boring: newcomers are preferred
            val present = sim.tower.allRooms.mapNotNull { it.tenant }.toSet()
            val fresh = candidates.filter { it !in present }.ifEmpty { candidates }
            sim.moveIn(room, fresh[sim.random.nextInt(fresh.size)])
        }
    }

    companion object {
        const val CHECK_EVERY = 10f
        const val VACANCY_MINUTES = 60.0
        const val MOVE_IN_CHANCE = 0.35f
    }
}
