package crazytower.core.ecs

import com.github.quillraven.fleks.IntervalSystem
import com.github.quillraven.fleks.World.Companion.family
import com.github.quillraven.fleks.World.Companion.inject
import crazytower.core.Room
import crazytower.core.RoomRole
import crazytower.core.Simulation
import kotlin.math.abs

/**
 * Sends the cleaning staff to dirty hotel rooms: whoever is at their own room and not busy
 * walks to the nearest room nobody else is on the way to. The room is clean once they arrive,
 * then they stay [Simulation.CLEAN_MINUTES] minutes and walk back.
 */
class HousekeepingSystem(
    private val sim: Simulation = inject(),
) : IntervalSystem() {
    private val cleaners = family { all(Cleaning, Schedule) }
    private var untilCheck = 0f

    override fun onTick() {
        untilCheck -= deltaTime
        if (untilCheck > 0f) return
        untilCheck = CHECK_EVERY
        // staff who went home or got sent elsewhere drop their job, so somebody else takes it
        cleaners.forEach { if (it[Schedule].activity != Activity.TO_CLEAN) it.configure { e -> drop(e, Cleaning) } }
        val dirty = sim.dirtyRooms.toMutableList()
        if (dirty.isEmpty()) return
        cleaners.forEach { e -> dirty.removeAll { it.id == e[Cleaning].roomId } }
        for (service in sim.tower.allRooms) {
            if (service.type.role != RoomRole.HOUSEKEEPING || service.tenant == null) continue
            for (member in sim.membersOf(service.id)) {
                if (dirty.isEmpty()) return
                if (member[Schedule].activity != Activity.AT_ROOM || member has Goal || member has Route || member has Riding) continue
                val target = dirty.minBy { distance(service, it) }
                dirty -= target
                member.configure {
                    drop(it, Idle)
                    it += Cleaning(target.id)
                }
                sim.goTo(member, sim.spotIn(target), Activity.TO_CLEAN)
            }
        }
    }

    private fun distance(a: Room, b: Room): Float = abs(a.floor - b.floor) * 10 + abs(a.centerX - b.centerX)

    companion object {
        const val CHECK_EVERY = 5f
    }
}
