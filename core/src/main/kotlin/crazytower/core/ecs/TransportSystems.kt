package crazytower.core.ecs

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.IteratingSystem
import com.github.quillraven.fleks.World.Companion.family
import com.github.quillraven.fleks.World.Companion.inject
import crazytower.core.Simulation
import crazytower.core.nav.Leg
import crazytower.core.nav.NavCosts
import crazytower.core.transport.Cabin
import crazytower.core.transport.Car
import crazytower.core.transport.Elevators
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * When the tower changes, people who found no way try again. When something was taken away,
 * routes may lead through rooms that are gone, so everybody who is not inside an elevator
 * plans again, a few hundred per tick, so a big tower doesn't freeze. New rooms only add ways:
 * people on the way keep their route and find new shortcuts on their next trip.
 */
class RouteRefreshSystem(
    private val sim: Simulation = inject(),
) : IteratingSystem(family { all(Position, Goal).any(Route, Stuck).none(Riding) }) {
    private var seenVersion = -1
    private var seenRemoved = -1
    private var removed = false

    /** People still to plan again after something was taken away. */
    private val pending = ArrayDeque<Entity>()

    override fun onTick() {
        val changed = seenVersion != sim.tower.version
        removed = seenRemoved != sim.tower.removedVersion
        seenVersion = sim.tower.version
        seenRemoved = sim.tower.removedVersion
        if (removed) pending.clear()
        if (changed) super.onTick()
        var budget = REPLAN_PER_TICK
        while (budget > 0 && pending.isNotEmpty()) {
            val entity = pending.removeFirst()
            if (entity !in world || entity hasNo Route || entity has Riding) continue
            entity.configure { drop(it, Route) }
            budget--
        }
    }

    override fun onTickEntity(entity: Entity) {
        if (entity has Stuck) {
            entity.configure { drop(it, Stuck) }
        } else if (removed) {
            pending += entity
        }
    }

    companion object {
        /** Routes planned again per tick after something was taken away. */
        const val REPLAN_PER_TICK = 200
    }
}

/**
 * Runs the elevator cars. People with a [Leg.Ride] at the front of their route queue in front
 * of the shaft; the car collects them in its travel direction and drops them at their floor.
 */
class ElevatorSystem(
    private val sim: Simulation = inject(),
) : IteratingSystem(family { all(Position, Route).none(Riding) }) {
    private val elevators get() = sim.elevators

    override fun onTick() {
        elevators.sync()
        for (car in elevators.removed) dropEveryone(car)
        elevators.removed.clear()
        for (car in elevators.cars.values) car.waiting.clear()
        super.onTick() // collects the waiting people
        for (car in elevators.cars.values) {
            runCar(car)
            car.refreshEstimate()
        }
    }

    override fun onTickEntity(entity: Entity) {
        // the family can hand out somebody whose route was just taken away; they plan again
        val leg = entity.getOrNull(Route)?.legs?.firstOrNull() as? Leg.Ride ?: return
        val car = elevators.cars[leg.shaftId]
        if (car == null || leg.fromFloor !in car.floors || leg.toFloor !in car.floors) {
            entity.configure { drop(it, Route) } // plan again
            return
        }
        val pos = entity[Position]
        if (pos.floor == leg.fromFloor && abs(pos.x - leg.x) < 0.3f) {
            car.waiting.getOrPut(leg.fromFloor) { ArrayList() } += entity
            car.waitingSince.getOrPut(entity) { sim.clock.totalMinutes }
        }
    }

    private fun rideOf(entity: Entity): Leg.Ride? = entity.getOrNull(Route)?.legs?.firstOrNull() as? Leg.Ride

    private fun runCar(car: Car) {
        for (cabin in car.cabins) {
            cabin.riders.removeAll { it !in world } // people whose tenant moved out mid-ride
            // a rider without a ride (route lost while the tower changed) gets out and plans again
            cabin.riders.filter { rideOf(it) == null }.forEach { dropRider(car, cabin, it) }
        }
        for (line in car.waiting.values) line.removeAll { it !in world || rideOf(it) == null }
        car.waitingSince.keys.retainAll(car.waiting.values.flatten().toSet())
        if (sim.clock.totalMinutes < car.blockedUntil) return
        val assigned = assignCalls(car)
        for (cabin in car.cabins) runCabin(car, cabin, assigned[cabin].orEmpty())
    }

    /**
     * Every floor where people wait goes to one cabin: the nearest one that is idle or already
     * heading that way, so several cabins don't all chase the same call.
     */
    private fun assignCalls(car: Car): Map<Cabin, Set<Int>> {
        val result = HashMap<Cabin, MutableSet<Int>>()
        val span = (car.top - car.bottom + 1).toFloat()
        for ((floor, list) in car.waiting) {
            if (list.isEmpty()) continue
            val best = car.cabins.minBy { cabin ->
                val distance = abs(cabin.position - floor)
                val away = cabin.direction != 0 && (floor - cabin.position) * cabin.direction < -0.001f
                distance + (if (away) span else 0f) + cabin.riders.size * 0.1f
            }
            result.getOrPut(best) { HashSet() } += floor
        }
        return result
    }

    private fun runCabin(car: Car, cabin: Cabin, waitingCalls: Set<Int>) {
        if (cabin.doorTimer > 0f) {
            cabin.doorTimer -= deltaTime
            return
        }
        val here = cabin.position.roundToInt()
        if (abs(cabin.position - here) < 0.001f && stopAt(car, cabin, here, waitingCalls)) {
            cabin.doorTimer = Elevators.DOOR_MINUTES
            return
        }
        val calls = calls(cabin, waitingCalls)
        if (calls.isEmpty()) {
            cabin.direction = 0
            return
        }
        if (cabin.direction == 0 || calls.none { (it - cabin.position) * cabin.direction > 0.001f }) {
            val nearest = calls.minBy { abs(it - cabin.position) }
            cabin.direction = sign(nearest - cabin.position).toInt()
            if (cabin.direction == 0) return
        }
        val step = deltaTime / car.minutesPerFloor
        val next = if (cabin.direction > 0) ceil(cabin.position + 0.001f).toInt() else floor(cabin.position - 0.001f).toInt()
        cabin.position = if (next in calls && abs(next - cabin.position) <= step) {
            next.toFloat()
        } else {
            (cabin.position + cabin.direction * step).coerceIn(car.bottom.toFloat(), car.top.toFloat())
        }
        val shown = cabin.position.roundToInt()
        for (rider in cabin.riders) rider[Position].floor = shown
    }

    /** Unloads and loads at [floor]. Returns true when the doors opened for somebody. */
    private fun stopAt(car: Car, cabin: Cabin, floor: Int, waitingCalls: Set<Int>): Boolean {
        var opened = false
        val leaving = cabin.riders.filter { rideOf(it)?.toFloor == floor }
        for (rider in leaving) {
            cabin.riders -= rider
            val pos = rider[Position]
            pos.floor = floor
            pos.x = car.centerX
            rider[Route].legs.removeFirst()
            rider[Route].legProgress = 0f
            rider.configure { drop(it, Riding) }
            opened = true
        }
        val queue = car.waiting[floor] ?: return opened
        if (cabin.riders.isEmpty() && calls(cabin, waitingCalls).none { it != floor && (it - floor) * cabin.direction > 0 }) {
            cabin.direction = 0
        }
        val boarded = ArrayList<Entity>()
        for (person in queue) {
            if (cabin.riders.size >= car.capacity) break
            val ride = rideOf(person) ?: continue
            val wants = sign((ride.toFloor - ride.fromFloor).toFloat()).toInt()
            if (cabin.direction == 0) cabin.direction = wants
            if (wants != cabin.direction) continue
            cabin.riders += person
            boarded += person
            person.configure { it += Riding(car.id) }
            car.waitingSince.remove(person)?.let { sim.onBoarded(person, sim.clock.totalMinutes - it) }
            opened = true
        }
        // nobody boards two cabins in the same tick
        queue.removeAll(boarded.toSet())
        return opened
    }

    /** Floors the cabin has a reason to go to: where its riders want out, and the calls it was given. */
    private fun calls(cabin: Cabin, waitingCalls: Set<Int>): Set<Int> {
        val result = HashSet(waitingCalls)
        for (rider in cabin.riders) rideOf(rider)?.let { result += it.toFloor }
        return result
    }

    private fun dropEveryone(car: Car) {
        for (cabin in car.cabins) dropCabin(car, cabin)
    }

    private fun dropCabin(car: Car, cabin: Cabin) {
        for (rider in cabin.riders.toList()) dropRider(car, cabin, rider)
        cabin.riders.clear()
    }

    /** Lets [rider] out at the floor the cabin is at; without a route they plan their way again. */
    private fun dropRider(car: Car, cabin: Cabin, rider: Entity) {
        cabin.riders -= rider
        if (rider !in world) return
        val pos = rider[Position]
        pos.floor = cabin.position.roundToInt()
        pos.x = car.centerX
        rider.configure {
            drop(it, Riding)
            drop(it, Route)
        }
    }
}
