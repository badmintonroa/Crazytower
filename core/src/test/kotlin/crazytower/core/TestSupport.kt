package crazytower.core

/**
 * A simulation for tests that build offices right away: offices come with the first star, so
 * the tower counts as having had one. The stars themselves stay at 0.
 */
fun officeSim(seed: Long = 1L, startMoney: Long = Simulation.START_MONEY): Simulation =
    Simulation(seed, startMoney).apply { bestStars = maxOf(bestStars, 1) }
