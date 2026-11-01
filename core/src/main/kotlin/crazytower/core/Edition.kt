package crazytower.core

import java.util.Properties

/**
 * Full game or Steam demo. The demo ends with the second star: the rating never climbs
 * higher, and the game shows the "get the full version" window instead.
 *
 * The edition is baked into the build (`./gradlew -Pdemo=true ...` writes
 * `crazytower/edition.properties` with `edition=demo`). `-Dcrazytower.edition=demo` turns a
 * full build into the demo for testing; nothing turns a demo build into the full game.
 */
object Edition {
    /** The last star of the demo. */
    const val DEMO_STARS = 2

    /** The Steam store page shown at the end of the demo; from the build, empty until there is one. */
    val storeUrl: String

    var demo: Boolean

    init {
        val props = Properties()
        Edition::class.java.getResourceAsStream("/crazytower/edition.properties")?.use { props.load(it) }
        demo = props.getProperty("edition") == "demo" || System.getProperty("crazytower.edition") == "demo"
        storeUrl = props.getProperty("storeUrl").orEmpty()
    }

    /** The most stars this edition hands out. */
    val maxStars: Int get() = if (demo) DEMO_STARS else Rating.steps.size
}
