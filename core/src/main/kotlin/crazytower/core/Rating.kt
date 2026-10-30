package crazytower.core

/** The star rating of the tower, 0 to 5, from population, mood and services. A new tower has none. */
object Rating {
    data class Step(val stars: Int, val population: Int, val satisfaction: Float, val needsFood: Boolean)

    val steps = listOf(
        Step(1, 10, 0f, needsFood = false),
        Step(2, 40, 50f, needsFood = false),
        Step(3, 150, 60f, needsFood = true),
        Step(4, 500, 64f, needsFood = true),
        Step(5, 1500, 68f, needsFood = true),
    )

    fun stars(population: Int, satisfaction: Float, hasFood: Boolean): Int {
        var stars = 0
        for (step in steps) {
            if (population >= step.population && satisfaction >= step.satisfaction && (hasFood || !step.needsFood)) {
                stars = step.stars
            } else {
                break
            }
        }
        return stars
    }

    /** What is still missing for the star after [stars], shortest first; empty at five stars. */
    fun missing(stars: Int, population: Int, satisfaction: Float, hasFood: Boolean): List<String> {
        val step = nextStep(stars) ?: return emptyList()
        return buildList {
            if (step.needsFood && !hasFood) add(tr("ein Imbiss", "a snack bar"))
            if (satisfaction < step.satisfaction) add(tr("Laune ${step.satisfaction.toInt()} %", "${step.satisfaction.toInt()} % mood"))
            if (population < step.population) add(tr("${step.population} Bewohner", "${step.population} residents"))
        }
    }

    /** What the next star needs, for the HUD; null at five stars. */
    fun nextStep(stars: Int): Step? = steps.firstOrNull { it.stars == stars + 1 }
}
