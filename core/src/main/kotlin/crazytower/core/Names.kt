package crazytower.core

import kotlin.random.Random

/** Random names for the people in the tower. */
object Names {
    private val first = listOf(
        "Jannis", "Mia", "Ben", "Emma", "Paul", "Lea", "Finn", "Hanna", "Karl", "Greta",
        "Otto", "Frieda", "Luis", "Ida", "Bodo", "Uschi", "Horst", "Elke", "Detlef", "Gisela",
    )
    private val last = listOf(
        "Müller", "Schmidt", "Kleinlich", "Wichtig", "Sonnenschein", "Pünktlich", "Hupe", "Schnarch",
        "Eilig", "Zögerlich", "Kaffeeklatsch", "Treppenwitz", "Aufzugmann", "Wolkenkratz",
    )

    private val lastEn = listOf(
        "Miller", "Smith", "Petty", "Ladder", "Sunshine", "Ontime", "Honk", "Snore",
        "Hurry", "Dawdle", "Teabreak", "Stairwit", "Liftman", "Cloudscrape",
    )

    fun random(random: Random): String {
        val names = if (I18n.english) lastEn else last
        return "${first[random.nextInt(first.size)]} ${names[random.nextInt(names.size)]}"
    }
}
