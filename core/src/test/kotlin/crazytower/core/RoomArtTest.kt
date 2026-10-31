package crazytower.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every room and tenant has its own picture in the game's atlases, by day, at night and run down. */
class RoomArtTest {
    private val atlasDir = File("../game/src/main/resources/rooms")

    private fun keys(atlas: String): Set<String> =
        Regex("\"([a-z0-9_]+)\\.png\"\\s*:").findAll(File(atlasDir, "$atlas.json").readText()).map { it.groupValues[1] }.toSet()

    private val day = keys("atlas") + keys("extra")
    private val variants = keys("night") + keys("rundown") + keys("extra_night") + keys("extra_rundown")

    private val roomArt: Set<String> =
        (Catalog.roomTypes.filter { it.role != RoomRole.PARKING }.map { it.art } +
            Tenants.all.map { it.art } + Stages.all.map { it.lobbyArt }).toSet()

    @Test
    fun everyRoomAndTenantHasItsOwnPicture() {
        val missing = roomArt.filter { it !in day } +
            listOf("", "_blue", "_yellow", "_empty").map { Catalog.PARKING.art + it }.filter { it !in day }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun everyRoomPictureHasANightAndARunDownLook() {
        val missing = roomArt.flatMap { listOf(it + "_night", it + "_rundown") }.filter { it !in variants }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun theLuxuryOfficesHavePicturesAsWideAsTheRoom() {
        val extra = File(atlasDir, "extra.json").readText()
        for (art in listOf(Catalog.LUXURY_OFFICE.art) + Tenants.all.filter { it.luxury }.map { it.art }) {
            val w = Regex("\"$art\\.png\"\\s*:\\s*\\{\\s*\"frame\"\\s*:\\s*\\{[^}]*\"w\"\\s*:\\s*(\\d+)").find(extra)?.groupValues?.get(1)?.toInt()
            assertEquals(Catalog.LUXURY_OFFICE.width * 96, w, art)
        }
    }
}
