package crazytower.core.ecs

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.IntervalSystem
import com.github.quillraven.fleks.IteratingSystem
import com.github.quillraven.fleks.World.Companion.family
import com.github.quillraven.fleks.World.Companion.inject
import crazytower.core.GROUND_FLOOR
import crazytower.core.Routine
import crazytower.core.Simulation

/**
 * The daily routine of everybody who works or lives in the tower: come in, work or stay
 * at home, go for lunch or dinner, walk around a bit, leave. Runs around the clock, since
 * every tenant has its own hours.
 */
class ScheduleSystem(
    private val sim: Simulation = inject(),
) : IteratingSystem(family { all(Member, Schedule, Position).none(Goal, Route, Idle, Riding, Stuck) }) {
    private var tick = 0

    override fun onTick() {
        tick++
        super.onTick()
    }

    override fun onTickEntity(entity: Entity) {
        // people outside look at the clock once a game minute, not every tick: there are thousands of them
        if (entity has Away && (tick + entity.id) % AWAY_CHECK_TICKS != 0) return
        val member = entity[Member]
        val schedule = entity[Schedule]
        val room = sim.tower.room(member.roomId)
        val tenant = room?.tenant
        if (room == null || tenant == null) {
            entity.remove()
            return
        }
        val clock = sim.clock
        val inside = Routine.wantsInside(member.resident, schedule.plan, schedule.crew, schedule.seed, schedule.offset, clock)
        val pos = entity[Position]

        if (entity has Away) {
            if (!inside) return
            val fromLeft = sim.random.nextBoolean()
            pos.floor = GROUND_FLOOR
            pos.x = if (fromLeft) 0f else sim.lotWidth - 0.01f
            entity.configure { drop(it, Away) }
            sim.goTo(entity, sim.spotIn(room), Activity.ARRIVING)
            return
        }
        if (!inside) {
            sim.goTo(entity, sim.streetExit(pos.x), Activity.LEAVING)
            return
        }
        if (schedule.ateOnDay != clock.day && Routine.mealTime(member.resident, schedule.plan, schedule.offset, clock)) {
            schedule.ateOnDay = clock.day
            val food = sim.nearestOpenFood(pos.floor, pos.x)
            if (food != null) {
                sim.goTo(entity, sim.spotIn(food), Activity.TO_MEAL)
                return
            }
        }
        val atRoom = pos.floor == room.floor && pos.x >= room.x && pos.x <= room.right
        if (!atRoom) {
            sim.goTo(entity, sim.spotIn(room), Activity.ARRIVING)
            return
        }
        // At the room: stay a while, now and then shuffle to another spot or visit the corridor.
        schedule.activity = Activity.AT_ROOM
        schedule.mood = (schedule.mood + 0.4f).coerceAtMost(MOOD_CEILING)
        when (sim.random.nextInt(10)) {
            0 -> sim.goTo(entity, sim.spotIn(room), Activity.WANDERING)
            else -> entity.configure { it += Idle(clock.totalMinutes + 15 + sim.random.nextInt(45)) }
        }
    }

    companion object {
        /** Staying at the room slowly calms people down, but never above this. */
        const val MOOD_CEILING = 80f

        /** Ticks between two looks at the clock for people outside the tower (ten ticks are a game minute). */
        const val AWAY_CHECK_TICKS = 10
    }
}

/**
 * Collects the rent when the tenants finish work, and settles the accounts every midnight:
 * maintenance out, home fees in. Unhappy tenants move out, and the tower gets its new star rating.
 */
class EconomySystem(
    private val sim: Simulation = inject(),
) : IntervalSystem() {
    private var lastDay = -1
    private var lastMinute = -1

    override fun onTick() {
        val minute = sim.clock.totalMinutes.toInt()
        if (minute != lastMinute) {
            lastMinute = minute
            sim.collectRent()
        }
        val day = sim.clock.day
        if (lastDay < 0) lastDay = day
        if (day == lastDay) return
        lastDay = day
        sim.closeDay()
    }
}

/** Every game hour: maybe an absurd event, and a look whether the tower reached the next stage. */
class EventSystem(
    private val sim: Simulation = inject(),
) : IntervalSystem() {
    private var lastHour = -1

    override fun onTick() {
        val hour = (sim.clock.totalMinutes / 60).toInt()
        if (lastHour < 0) lastHour = hour
        if (hour == lastHour) return
        lastHour = hour
        sim.checkStage()
        if (sim.eventsEnabled) crazytower.core.Events.roll(sim)
    }
}
