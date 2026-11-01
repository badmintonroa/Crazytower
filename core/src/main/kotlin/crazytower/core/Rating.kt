package crazytower.core

/**
 * The star rating of the tower, 0 to 5. The stars are the tower's only ladder: each star is
 * also the next stage of the building (see [Stages]), with its height limit, lobby and
 * unlocks. A new tower has none. Stars are never taken away.
 */
object Rating {
    data class Step(
        val stars: Int,
        val population: Int,
        val satisfaction: Float,
        val needsFood: Boolean = false,
        val needsSecurity: Boolean = false,
        val needsVip: Boolean = false,
    )

    val steps = listOf(
        // the first star comes quickly: a handful of flats and the tower is on the map
        Step(1, 10, 0f),
        Step(2, 300, 50f),
        Step(3, 1_000, 60f, needsFood = true),
        Step(4, 3_000, 64f, needsFood = true, needsSecurity = true),
        Step(5, 6_000, 68f, needsFood = true, needsVip = true),
    )

    /** What a tower brings along for the rating. */
    data class Facts(
        val population: Int,
        val satisfaction: Float,
        val hasFood: Boolean,
        val hasSecurity: Boolean = false,
        val vipSurvived: Boolean = false,
    )

    private fun Step.met(f: Facts): Boolean =
        f.population >= population && f.satisfaction >= satisfaction &&
            (f.hasFood || !needsFood) && (f.hasSecurity || !needsSecurity) && (f.vipSurvived || !needsVip)

    fun stars(facts: Facts): Int {
        var stars = 0
        for (step in steps) {
            if (step.met(facts)) stars = step.stars else break
        }
        return stars
    }

    fun stars(population: Int, satisfaction: Float, hasFood: Boolean, hasSecurity: Boolean = false, vipSurvived: Boolean = false): Int =
        stars(Facts(population, satisfaction, hasFood, hasSecurity, vipSurvived))

    /** The stars this edition hands out: the demo stops at [Edition.DEMO_STARS]. */
    fun earned(facts: Facts): Int = stars(facts).coerceAtMost(Edition.maxStars)

    fun earned(population: Int, satisfaction: Float, hasFood: Boolean, hasSecurity: Boolean = false, vipSurvived: Boolean = false): Int =
        earned(Facts(population, satisfaction, hasFood, hasSecurity, vipSurvived))

    /** What is still missing for the star after [stars], people first; empty at five stars. */
    fun missing(stars: Int, facts: Facts): List<String> {
        val step = nextStep(stars) ?: return emptyList()
        return buildList {
            if (facts.population < step.population) add(tr("${formatPeople(step.population)} Bewohner", "${formatPeopleEn(step.population)} residents"))
            if (step.needsFood && !facts.hasFood) add(tr("ein Imbiss", "a snack bar"))
            if (step.needsSecurity && !facts.hasSecurity) add(tr("Sicherheitsdienst", "security"))
            if (step.needsVip && !facts.vipSurvived) add(tr("ein glücklicher VIP-Besuch", "a happy VIP"))
            if (facts.satisfaction < step.satisfaction) add(tr("Laune ${step.satisfaction.toInt()} %", "${step.satisfaction.toInt()} % mood"))
        }
    }

    /** Everything star [stars] asks for, e.g. "1.000 Bewohner, Laune 60 %, Imbiss". */
    fun requirement(stars: Int): Tr {
        val step = steps.firstOrNull { it.stars == stars } ?: return Tr("Start", "Start")
        val de = buildList {
            add("${formatPeople(step.population)} Bewohner")
            if (step.satisfaction > 0f) add("Laune ${step.satisfaction.toInt()} %")
            if (step.needsFood) add("Imbiss")
            if (step.needsSecurity) add("Sicherheitsdienst")
            if (step.needsVip) add("VIP-Besuch überlebt")
        }
        val en = buildList {
            add("${formatPeopleEn(step.population)} residents")
            if (step.satisfaction > 0f) add("${step.satisfaction.toInt()} % mood")
            if (step.needsFood) add("snack bar")
            if (step.needsSecurity) add("security")
            if (step.needsVip) add("a happy VIP")
        }
        return Tr(de.joinToString(", "), en.joinToString(", "))
    }

    /** People needed for star [stars]; 0 for none. */
    fun population(stars: Int): Int = steps.firstOrNull { it.stars == stars }?.population ?: 0

    /** Progress towards the star after [stars], 0..1, counting only the people. */
    fun progress(stars: Int, population: Int): Double {
        val next = nextStep(stars) ?: return 1.0
        val from = population(stars)
        return ((population - from).toDouble() / (next.population - from)).coerceIn(0.0, 1.0)
    }

    /** What the next star needs, for the HUD; null at five stars. */
    fun nextStep(stars: Int): Step? = steps.firstOrNull { it.stars == stars + 1 }

    private fun formatPeople(n: Int): String = formatPeopleEn(n).replace(',', '.')
    private fun formatPeopleEn(n: Int): String = "%,d".format(java.util.Locale.ENGLISH, n)
}
