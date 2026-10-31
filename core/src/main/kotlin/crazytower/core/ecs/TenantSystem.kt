package crazytower.core.ecs

import com.github.quillraven.fleks.IntervalSystem
import com.github.quillraven.fleks.World.Companion.inject
import crazytower.core.RoomProblems
import crazytower.core.RoomRole
import crazytower.core.Simulation
import crazytower.core.Tenants
import crazytower.core.moveInBlocked
import crazytower.core.missionTenant

/**
 * Lets tenants move into empty offices, homes and restaurants. A room only gets rented once
 * people can get there from the street in time, so upper floors need stairs or an elevator.
 * New flats find only a few buyers a day. Whatever keeps a room empty for good ends up in
 * [Simulation.roomProblems], for the exclamation marks over the rooms.
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
        val problems = sim.roomProblems
        problems.clear()
        // one search from each way in serves all empty rooms
        val searches = HashMap<Float, (crazytower.core.Loc) -> Float?>()
        val present by lazy { sim.tower.allRooms.mapNotNullTo(HashSet()) { it.tenant } }
        for (room in sim.tower.allRooms) {
            if (!room.type.role.rentable || room.tenant != null) continue
            val street = sim.streetExit(room.centerX)
            val minutes = searches.getOrPut(street.x) { sim.nav.travelMinutesFrom(street) }(room.center)
            val problem = RoomProblems.of(sim, room, street, minutes)
            if (problem != null) {
                problems[room.id] = problem
                continue
            }
            // termites at work
            if (sim.moveInBlocked(room)) continue
            if (now - room.since < VACANCY_MINUTES || sim.random.nextFloat() > MOVE_IN_CHANCE) continue
            // only a few buyers a day for flats that were never sold
            if (room.type.role == RoomRole.HOME && !room.sold && sim.flatsSoldToday >= sim.flatBuyersPerDay) continue
            // hotel guests only take clean rooms, and only in the afternoon and evening
            if (room.type.role == RoomRole.HOTEL && (room.dirty || sim.clock.hour !in Simulation.CHECK_IN_FROM until Simulation.CHECK_IN_UNTIL)) continue
            val candidates = Tenants.candidates(room.type, sim.stars, sim.stage)
            val special = sim.missionTenant(room)
            if (special != null) {
                sim.moveIn(room, special)
                continue
            }
            if (room.type.role == RoomRole.HOTEL) {
                // guests come and go, a repeat visit is fine
                sim.moveIn(room, candidates[sim.random.nextInt(candidates.size)])
                continue
            }
            // a tower full of identical tenants is boring: newcomers are preferred
            val fresh = candidates.filter { it !in present }.ifEmpty { candidates }
            val tenant = fresh[sim.random.nextInt(fresh.size)]
            sim.moveIn(room, tenant)
            present += tenant
        }
    }

    companion object {
        const val CHECK_EVERY = 10f
        const val VACANCY_MINUTES = 60.0
        const val MOVE_IN_CHANCE = 0.35f
    }
}
