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

    fun random(random: Random): String = "${first[random.nextInt(first.size)]} ${last[random.nextInt(last.size)]}"
}
