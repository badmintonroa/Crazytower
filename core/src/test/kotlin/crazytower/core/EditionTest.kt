package crazytower.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Steam demo stops at two stars. */
class EditionTest {
    private fun daysAfterReset(demo: Boolean): Simulation {
        val before = Edition.demo
        Edition.demo = demo
        try {
            // a big tower that has earned far more than two stars, back at one star, a few days to settle in
            val sim = TestTower.create(4, seed = 3)
            sim.stars = 1
            repeat(3 * 24 * 6) { sim.step(10.0) }
            return sim
        } finally {
            Edition.demo = before
        }
    }

    @Test
    fun theDemoEndsAtTheSecondStar() {
        val before = Edition.demo
        val sim = daysAfterReset(demo = true)
        assertEquals(Edition.DEMO_STARS, sim.stars)
        Edition.demo = true
        try {
            assertTrue(sim.demoOver)
            assertEquals(Edition.DEMO_STARS, Edition.maxStars)
            // a tower good enough for five stars still gets two
            assertEquals(Edition.DEMO_STARS, Rating.earned(10_000, 90f, hasFood = true))
        } finally {
            Edition.demo = before
        }
    }

    @Test
    fun theFullGameHasAllStars() {
        val before = Edition.demo
        Edition.demo = false
        try {
            assertEquals(Rating.steps.size, Edition.maxStars)
            assertEquals(5, Rating.earned(10_000, 90f, hasFood = true))
            val sim = TestTower.create(4, seed = 3)
            assertTrue(sim.stars > Edition.DEMO_STARS)
            assertFalse(sim.demoOver)
        } finally {
            Edition.demo = before
        }
    }
}
