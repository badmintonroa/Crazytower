package crazytower.core.nav

/** One step of a route. A person works through the legs of its route in order. */
sealed interface Leg {
    /** Walk along [floor] to [toX]. */
    data class Walk(val floor: Int, val toX: Float) : Leg

    /** Take the stairs (or escalator) at [x] from one floor to the next. */
    data class Stairs(val fromFloor: Int, val toFloor: Int, val x: Float) : Leg

    /** Wait for and ride the elevator in shaft [shaftId]. */
    data class Ride(val shaftId: Int, val fromFloor: Int, val toFloor: Int, val x: Float) : Leg
}

/** Movement speeds and costs, all in game minutes. */
object NavCosts {
    /** Walking speed in segments per game minute. */
    const val WALK_SPEED = 1.2f

    /** Minutes to climb one floor on the stairs. */
    const val STAIRS_PER_FLOOR = 2.5f

    /** Minutes an elevator car needs per floor. */
    const val ELEVATOR_PER_FLOOR = 0.4f

    /** Minimum minutes any transport needs per floor, used as the A* heuristic. */
    const val FASTEST_PER_FLOOR = 0.2f
}
