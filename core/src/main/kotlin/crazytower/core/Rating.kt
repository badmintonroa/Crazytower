package crazytower.core

/** The star rating of the tower, from population, mood and services. */
object Rating {
    data class Step(val stars: Int, val population: Int, val satisfaction: Float, val needsFood: Boolean)

    val steps = listOf(
        Step(2, 40, 50f, needsFood = false),
        Step(3, 150, 55f, needsFood = true),
        Step(4, 500, 60f, needsFood = true),
        Step(5, 1500, 65f, needsFood = true),
    )

    fun stars(population: Int, satisfaction: Float, hasFood: Boolean): Int {
        var stars = 1
        for (step in steps) {
            if (population >= step.population && satisfaction >= step.satisfaction && (hasFood || !step.needsFood)) {
                stars = step.stars
            } else {
                break
            }
        }
        return stars
    }

    /** What the next star needs, for the HUD; null at five stars. */
    fun nextStep(stars: Int): Step? = steps.firstOrNull { it.stars == stars + 1 }
}
