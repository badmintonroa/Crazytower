package crazytower.core

import crazytower.core.ecs.Schedule
import crazytower.core.ecs.Vip
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VipTest {
    private fun towerWithOffice(mood: Float, stars: Int = 4): Pair<Simulation, Room> {
        val sim = Simulation(seed = 7, startMoney = 5_000_000)
        sim.eventsEnabled = false
        val office = sim.buildReachable(Catalog.OFFICE, 10)
        sim.moveIn(office, Tenants.byId("accountant"), announce = false)
        sim.stars = stars
        sim.setMood(mood)
        return sim to office
    }

    private fun Simulation.setMood(mood: Float) = with(world) {
        family { all(Schedule) }.forEach { it[Schedule].mood = mood }
    }

    private fun Simulation.waitForVip() {
        repeat(6 * 60) { if (vipOnTheWay) step(1.0) }
        assertFalse(vipOnTheWay, "the VIP should have arrived by now")
    }

    private fun Simulation.vipTips(): Long = finance.day(clock.day)[MoneyKind.VIP] ?: 0L

    @Test
    fun aHappyTowerGetsABigTip() {
        val (sim, office) = towerWithOffice(mood = 100f)
        assertTrue(sim.spawnVip(Vips.byId("goldzahn"), office))
        sim.waitForVip()
        assertTrue(sim.vipTips() >= 10_000, "tip was ${sim.vipTips()}")
        assertTrue(sim.news.latest.first().text.contains("Goldzahn"))
    }

    @Test
    fun aGloomyTowerGetsSmallChange() {
        val (sim, office) = towerWithOffice(mood = 5f)
        assertTrue(sim.spawnVip(Vips.byId("goldzahn"), office))
        sim.waitForVip()
        val tip = sim.vipTips()
        assertTrue(tip in 1..Vips.STINGY_PER_STAR * 4, "tip was $tip")
        assertTrue(sim.news.latest.first().text.contains("Laune"), "the VIP says what bothered her")
    }

    @Test
    fun noVipsWithoutEnoughStars() {
        val (sim, _) = towerWithOffice(mood = 100f, stars = Vips.MIN_STARS - 1)
        repeat(20 * 24) {
            Vips.roll(sim)
            sim.clock.set(sim.clock.totalMinutes + 60)
        }
        assertFalse(sim.vipOnTheWay)
        assertTrue(Vips.available(2).isEmpty())
    }

    @Test
    fun vipsDropByNowAndThenWithStars() {
        val (sim, _) = towerWithOffice(mood = 100f, stars = 5)
        var visits = 0
        repeat(20 * 24) {
            Vips.roll(sim)
            if (sim.vipOnTheWay) {
                visits++
                sim.world.family { all(Vip) }.forEach { it.remove() }
                sim.lastVipVisit = sim.clock.totalMinutes
            }
            sim.clock.set(sim.clock.totalMinutes + 60)
        }
        assertTrue(visits in 5..20, "$visits visits in 20 days")
    }

    @Test
    fun theTipGrowsWithStarsAndScore() {
        val random = Random(1)
        val good = VipVerdict(0.9f, null)
        assertTrue(Vips.tip(5, good, random) > Vips.tip(3, good, random))
        assertTrue(Vips.tip(3, VipVerdict(0.3f, "x"), random) < Vips.tip(3, VipVerdict(0.6f, null), random))
    }

    @Test
    fun frauDrWichtigStillUnlocksStageFive() {
        val (sim, office) = towerWithOffice(mood = 80f)
        assertTrue(sim.spawnVip(Vips.byId("wichtig"), office))
        sim.waitForVip()
        assertTrue(sim.vipSurvived)
        assertEquals(false, Vips.available(5).any { it.id == "wichtig" })
    }
}
