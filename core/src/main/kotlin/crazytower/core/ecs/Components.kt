package crazytower.core.ecs

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import crazytower.core.Loc
import crazytower.core.Shift
import crazytower.core.nav.Leg

/** Where a person currently is. [x] is measured in segments. */
data class Position(var floor: Int, var x: Float, var facingLeft: Boolean = false) : Component<Position> {
    override fun type() = Position
    companion object : ComponentType<Position>()
}

/** Visible identity of a person. [look] selects the sprite set. */
data class Person(val name: String, val look: String) : Component<Person> {
    override fun type() = Person
    companion object : ComponentType<Person>()
}

/** Where a person wants to go. The pathfinding system turns it into a [Route]. */
data class Goal(val target: Loc) : Component<Goal> {
    override fun type() = Goal
    companion object : ComponentType<Goal>()
}

/** The legs a person still has to walk, ride or climb, front first. */
class Route(val legs: ArrayDeque<Leg>) : Component<Route> {
    /** Progress inside the current leg, used by timed legs such as stairs. */
    var legProgress: Float = 0f
    override fun type() = Route
    companion object : ComponentType<Route>()
}

/** A person standing still until the given game minute. */
data class Idle(var until: Double) : Component<Idle> {
    override fun type() = Idle
    companion object : ComponentType<Idle>()
}

/** A sightseer: walks to a few spots in the lobby, then leaves the tower. */
data class Visitor(var stopsLeft: Int, var leaving: Boolean = false) : Component<Visitor> {
    override fun type() = Visitor
    companion object : ComponentType<Visitor>()
}

/** Set when no route could be found to the current goal. */
data class Stuck(val since: Double) : Component<Stuck> {
    override fun type() = Stuck
    companion object : ComponentType<Stuck>()
}

/** Inside the elevator car [carId]. The elevator system moves the person and lets it out. */
data class Riding(val carId: Int) : Component<Riding> {
    override fun type() = Riding
    companion object : ComponentType<Riding>()
}

/** Somebody who belongs to a room: an employee of an office or restaurant, or a resident of a home. */
data class Member(val roomId: Int, val resident: Boolean) : Component<Member> {
    override fun type() = Member
    companion object : ComponentType<Member>()
}

/** What a member is up to right now; shown in the inspector and used to react on arrival. */
enum class Activity(val label: String) {
    AWAY("Nicht im Turm"),
    ARRIVING("Kommt in den Turm"),
    AT_ROOM("Bei der Arbeit"),
    WANDERING("Vertritt sich die Beine"),
    TO_MEAL("Geht essen"),
    EATING("Isst"),
    LEAVING("Geht nach Hause"),
}

/**
 * The daily routine of a member. [offset] shifts the times by a few minutes per person so
 * not everybody turns up at the same second. [mood] is 0..100.
 */
class Schedule(val shift: Shift, val offset: Int) : Component<Schedule> {
    var activity: Activity = Activity.AWAY
    var ateOnDay: Int = -1
    var tripStart: Double = 0.0
    var mood: Float = 70f
    override fun type() = Schedule
    companion object : ComponentType<Schedule>()
}

/** Members outside the tower (at work elsewhere, asleep somewhere, partying). Not drawn. */
data class Away(val since: Double) : Component<Away> {
    override fun type() = Away
    companion object : ComponentType<Away>()
}
