package crazytower.core

/**
 * The building lot. It starts narrow and the city sells the neighbouring plots once the tower
 * has enough stars; the lot grows to the right. The basement opens at [BASEMENT_STARS].
 * Both go by the best stars ever reached, so nothing built is ever outside the lot.
 */
object Lot {
    data class Step(val stars: Int, val width: Int)

    val steps = listOf(
        Step(0, 64),
        Step(3, 96),
        Step(4, 128),
        Step(5, 160),
    )

    /** The widest the lot ever gets; the tower is laid out for it from the start. */
    val maxWidth: Int get() = steps.last().width

    const val BASEMENT_STARS = 4

    /** Lot width in segments for a tower that reached [stars]. */
    fun width(stars: Int): Int = steps.last { it.stars <= stars }.width

    /** The next widening after [stars], or null when the lot is as wide as it gets. */
    fun next(stars: Int): Step? = steps.firstOrNull { it.stars > stars }
}
