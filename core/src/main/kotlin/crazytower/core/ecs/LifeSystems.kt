package crazytower.core.ecs

import crazytower.core.missionHour

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.IntervalSystem
import com.github.quillraven.fleks.IteratingSystem
import com.github.quillraven.fleks.World.Companion.family
import com.github.quillraven.fleks.World.Companion.inject
import crazytower.core.GROUND_FLOOR
import crazytower.core.Routine
import crazytower.core.Simulation
import crazytower.core.evacuated
import crazytower.core.tickEvents

/**
 * The daily routine of everybody who works or lives in the tower: come in, work or stay
 * at home, go for lunch or dinner, walk around a bit, leave. Runs around the clock, since
 * every tenant has its own hours.
 */
class ScheduleSystem(
    private val sim: Simulation = inject(),
) : IteratingSystem(family { all(Member, Schedule, Position).none(Goal, Route, Idle, Riding, Stuck) }), Unhurried {
    private var tick = 0

    override fun onTick() {
        tick++
        super.onTick()
    }

    override fun onTickEntity(entity: Entity) {
        // people outside look at the clock once a game minute, not every pass: there are thousands of them
        if (entity has Away && (tick + entity.id) % AWAY_CHECK_PASSES != 0) return
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

        // fire alarm: out of the tower and stay out
        if (sim.evacuated) {
            if (entity hasNo Away && schedule.activity != Activity.LEAVING) sim.goTo(entity, sim.streetExit(pos.x), Activity.LEAVING)
            return
        }
        if (entity has Away) {
            if (!inside) return
            // luxury tenants drive in and come up from their parking space
            val car = sim.carSpot(room)
            pos.floor = car?.floor ?: GROUND_FLOOR
            pos.x = car?.x ?: sim.arrivalX(room)
            entity.configure { drop(it, Away) }
            sim.goTo(entity, sim.spotIn(room), Activity.ARRIVING)
            return
        }
        if (!inside) {
            sim.goTo(entity, sim.carSpot(room) ?: sim.streetExit(pos.x), Activity.LEAVING)
            return
        }
        if (schedule.ateOnDay != clock.day && Routine.mealTime(member.resident, schedule.plan, schedule.offset, clock)) {
            schedule.ateOnDay = clock.day
            val food = sim.nearestOpenFood(pos.floor, pos.x)
            if (food != null) {
                sim.goTo(entity, sim.spotIn(food), Activity.TO_MEAL)
                return
            }
            if (schedule.grievance == null) schedule.grievance = Grievance.NO_FOOD
        }
        val atRoom = pos.floor == room.floor && pos.x >= room.x && pos.x <= room.right
        if (!atRoom) {
            sim.goTo(entity, sim.spotIn(room), Activity.ARRIVING)
            return
        }
        // At the room: stay a while, now and then shuffle to another spot or visit the corridor.
        // Meanwhile the mood settles towards what the tower offers them: a long wait drags it
        // down for a while, a meal and the special rooms lift what it settles to.
        schedule.activity = Activity.AT_ROOM
        schedule.mood += (sim.moodTarget(schedule, member.resident, room) - schedule.mood) * MOOD_SETTLE
        if (schedule.mood >= MOOD_BASE) schedule.grievance = null
        when (sim.random.nextInt(10)) {
            0 -> sim.goTo(entity, sim.spotIn(room), Activity.WANDERING)
            else -> entity.configure { it += Idle(clock.totalMinutes + 15 + sim.random.nextInt(45)) }
        }
    }

    companion object {
        /** The mood people settle to at their room when the tower offers them nothing. */
        const val MOOD_BASE = 55f

        /** Share of the way to the mood they settle to, on each look around the room. */
        const val MOOD_SETTLE = 0.1f

        /** Passes between two looks at the clock for people outside the tower: a pass is half a game minute. */
        const val AWAY_CHECK_PASSES = 2
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

/**
 * Every game minute the running events, every game hour maybe a new absurd event, and a look
 * whether the tower reached the next stage.
 */
class EventSystem(
    private val sim: Simulation = inject(),
) : IntervalSystem() {
    private var lastHour = -1
    private var lastMinute = -1

    override fun onTick() {
        val minute = sim.clock.totalMinutes.toInt()
        if (minute == lastMinute) return
        lastMinute = minute
        val hour = minute / 60
        if (lastHour < 0) lastHour = hour
        val newHour = hour != lastHour
        lastHour = hour
        sim.tickEvents(newHour)
        if (!newHour) return
        sim.checkStage()
        sim.missionHour()
        if (sim.eventsEnabled) {
            crazytower.core.Events.roll(sim)
            crazytower.core.Vips.roll(sim)
        }
    }
}
