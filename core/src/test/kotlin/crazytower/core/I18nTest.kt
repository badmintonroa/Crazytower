package crazytower.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class I18nTest {
    @AfterTest
    fun german() {
        I18n.language = Language.DE
    }

    @Test
    fun `every name and joke has an English version`() {
        val texts = Catalog.roomTypes.flatMap { listOf(it.name, it.description) + it.effects } +
            Tenants.all.flatMap { listOf(it.name, it.quirk, it.plan.label) } +
            Stages.all.flatMap { listOf(it.name, it.requirement) } + Stages.all.drop(1).map { it.unlocks } +
            Simulation.MOVE_OUT_REASONS +
            Vips.all.flatMap { listOf(it.name, it.arrives, it.generous, it.stingy, it.givesUp) }
        for (text in texts.filter { it.de.isNotEmpty() }) {
            assertTrue(text.en.isNotBlank(), "no English for \"${text.de}\"")
        }
    }

    @Test
    fun `texts follow the language`() {
        val office = Catalog.OFFICE
        assertEquals("Büro", office.name.toString())
        assertEquals("1.800 $", formatMoney(1_800))
        I18n.language = Language.EN
        assertEquals("Office", office.name.toString())
        assertEquals("$1,800", formatMoney(1_800))
        assertEquals("-$3,000", formatMoney(-3_000))
        assertEquals("$12–20", formatMoneyRange(12, 20))
        assertEquals("floor 5", Simulation.floorLabel(5))
    }

    @Test
    fun `the ticker speaks English`() {
        I18n.language = Language.EN
        val sim = Simulation(seed = 3)
        sim.build(Catalog.LOBBY, GROUND_FLOOR, 0, 12)
        assertTrue(sim.news.latest.first().text.startsWith("The first lobby is open"), sim.news.latest.first().text)
        assertEquals("Not enough money", Simulation(seed = 3, startMoney = 0).checkPlace(Catalog.LOBBY, GROUND_FLOOR, 0).reason)
    }
}
