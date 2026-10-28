package crazytower.core.ecs

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.IteratingSystem
import com.github.quillraven.fleks.World.Companion.family
import com.github.quillraven.fleks.World.Companion.inject
import crazytower.core.Simulation
import crazytower.core.nav.Leg
import crazytower.core.nav.NavCosts
import crazytower.core.transport.Car
import crazytower.core.transport.Elevators
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * When the tower changes, routes may lead through rooms that are gone or miss new shortcuts.
 * Everybody who is not inside an elevator plans again.
 */
class RouteRefreshSystem(
    private val sim: Simulation = inject(),
) : IteratingSystem(family { all(Position, Goal).any(Route, Stuck).none(Riding) }) {
    private var seenVersion = -1
    private var changed = false

    override fun onTick() {
        changed = seenVersion != sim.tower.version
        seenVersion = sim.tower.version
        if (changed) super.onTick()
    }

    override fun onTickEntity(entity: Entity) {
        entity.configure {
            it -= Route
            it -= Stuck
        }
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
        for (car in elevators.cars.values) runCar(car)
    }

    override fun onTickEntity(entity: Entity) {
        val leg = entity[Route].legs.firstOrNull() as? Leg.Ride ?: return
        val car = elevators.cars[leg.shaftId]
        if (car == null || leg.fromFloor !in car.floors || leg.toFloor !in car.floors) {
            entity.configure { it -= Route } // plan again
            return
        }
        val pos = entity[Position]
        if (pos.floor == leg.fromFloor && abs(pos.x - leg.x) < 0.3f) {
            car.waiting.getOrPut(leg.fromFloor) { ArrayList() } += entity
            car.waitingSince.getOrPut(entity) { sim.clock.totalMinutes }
        }
    }

    private fun rideOf(entity: Entity): Leg.Ride? = entity[Route].legs.firstOrNull() as? Leg.Ride

    private fun runCar(car: Car) {
        car.riders.removeAll { it !in world } // people whose tenant moved out mid-ride
        car.waitingSince.keys.retainAll(car.waiting.values.flatten().toSet())
        if (car.doorTimer > 0f) {
            car.doorTimer -= deltaTime
            return
        }
        val here = car.position.roundToInt()
        if (abs(car.position - here) < 0.001f && stopAt(car, here)) {
            car.doorTimer = Elevators.DOOR_MINUTES
            return
        }
        val calls = calls(car)
        if (calls.isEmpty()) {
            car.direction = 0
            return
        }
        if (car.direction == 0 || calls.none { (it - car.position) * car.direction > 0.001f }) {
            val nearest = calls.minBy { abs(it - car.position) }
            car.direction = sign(nearest - car.position).toInt()
            if (car.direction == 0) return
        }
        val step = deltaTime / NavCosts.ELEVATOR_PER_FLOOR
        val next = if (car.direction > 0) ceil(car.position + 0.001f).toInt() else floor(car.position - 0.001f).toInt()
        car.position = if (next in calls && abs(next - car.position) <= step) {
            next.toFloat()
        } else {
            (car.position + car.direction * step).coerceIn(car.bottom.toFloat(), car.top.toFloat())
        }
        val shown = car.position.roundToInt()
        for (rider in car.riders) rider[Position].floor = shown
    }

    /** Unloads and loads at [floor]. Returns true when the doors opened for somebody. */
    private fun stopAt(car: Car, floor: Int): Boolean {
        var opened = false
        val leaving = car.riders.filter { rideOf(it)?.toFloor == floor }
        for (rider in leaving) {
            car.riders -= rider
            val pos = rider[Position]
            pos.floor = floor
            pos.x = car.centerX
            rider[Route].legs.removeFirst()
            rider[Route].legProgress = 0f
            rider.configure { it -= Riding }
            opened = true
        }
        val queue = car.waiting[floor].orEmpty()
        if (car.riders.isEmpty() && calls(car).none { it != floor && (it - floor) * car.direction > 0 }) {
            car.direction = 0
        }
        for (person in queue) {
            if (car.riders.size >= Elevators.CAPACITY) break
            val ride = rideOf(person) ?: continue
            val wants = sign((ride.toFloor - ride.fromFloor).toFloat()).toInt()
            if (car.direction == 0) car.direction = wants
            if (wants != car.direction) continue
            car.riders += person
            person.configure { it += Riding(car.id) }
            car.waitingSince.remove(person)?.let { sim.onBoarded(person, sim.clock.totalMinutes - it) }
            opened = true
        }
        return opened
    }

    /** Floors the car has a reason to go to. */
    private fun calls(car: Car): Set<Int> {
        val result = HashSet<Int>()
        for (rider in car.riders) rideOf(rider)?.let { result += it.toFloor }
        for ((floor, list) in car.waiting) if (list.isNotEmpty()) result += floor
        return result
    }

    private fun dropEveryone(car: Car) {
        val floor = car.position.roundToInt()
        for (rider in car.riders) {
            if (rider !in world) continue
            val pos = rider[Position]
            pos.floor = floor
            pos.x = car.centerX
            rider.configure {
                it -= Riding
                it -= Route
            }
        }
        car.riders.clear()
    }
}
