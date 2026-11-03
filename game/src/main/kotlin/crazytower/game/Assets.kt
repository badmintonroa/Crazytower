package crazytower.game

import crazytower.core.Looks
import korlibs.image.bitmap.Bitmap32
import korlibs.image.bitmap.BmpSlice
import korlibs.image.format.readBitmap
import korlibs.io.file.std.resourcesVfs
import korlibs.korge.view.Container
import korlibs.korge.view.Image
import korlibs.korge.view.image
import korlibs.math.geom.Anchor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Room art from the 4x atlases (96x192 per segment, usually 6 segments) and people sprite sheets: three
 * frames side by side, 12x24 each, or any multiple of that (48x96 for 4x art).
 *
 * Everything the tower draws, the room atlases, every person sheet and the little pictures in
 * [WorldSprites], is packed onto one big texture when the game starts ([TexturePacker]), so
 * KorGE draws the tower in a few draw calls instead of one per person or room.
 */
class Assets private constructor(
    private val rooms: Map<String, BmpSlice>,
    private val people: Map<String, List<BmpSlice>>,
    private val sprites: Map<String, WorldSprites.Sprite>,
) {
    fun hasRoom(key: String): Boolean = key in rooms

    fun room(key: String): BmpSlice = rooms[key] ?: rooms.getValue(FALLBACK_ROOM)

    /** Frame 0 = standing, 1 and 2 = walking. */
    fun person(look: String, frame: Int): BmpSlice =
        (people[look] ?: Looks.standIns[look]?.let { people[it] } ?: people.getValue("casual"))[frame]

    /** Scale that shows a person frame [times] its native 12x24 size, whatever resolution the sheet has. */
    fun personScale(look: String, times: Double): Double = times * PERSON_H / person(look, 0).height

    /** One of the [WorldSprites] as an image whose 0,0 is where the drawing had its 0,0. */
    fun sprite(parent: Container, key: String): Image {
        val sprite = sprites.getValue(key)
        return parent.image(sprite.slice, sprite.anchor) { scale = 1.0 / sprite.scale }
    }

    companion object {
        const val FALLBACK_ROOM = "office_empty"
        const val ROOM_ART_SCALE = 4

        /** Width of one segment in the room art. */
        const val ROOM_ART_SEGMENT = 24 * ROOM_ART_SCALE
        const val PERSON_H = 24

        private suspend fun index(folder: String): List<String> =
            resourcesVfs["$folder/index.txt"].readString().lines().map { it.trim() }.filter { it.isNotEmpty() }

        private suspend fun readBmp32(path: String): Bitmap32 = resourcesVfs[path].readBitmap().toBMP32().premultipliedIfRequired()

        /** Frames of a TexturePacker-style atlas (hash format): room key to its rectangle in the sheet. */
        private suspend fun atlasFrames(base: String): Map<String, IntArray> =
            Json.parseToJsonElement(resourcesVfs["$base.json"].readString()).jsonObject.getValue("frames").jsonObject
                .entries.associate { (name, value) ->
                    val f = value.jsonObject.getValue("frame").jsonObject
                    fun n(k: String) = f.getValue(k).jsonPrimitive.int
                    name.removeSuffix(".png") to intArrayOf(n("x"), n("y"), n("w"), n("h"))
                }

        suspend fun load(): Assets {
            val sheets = LinkedHashMap<String, Bitmap32>()
            // the room atlases go onto the page whole, their frames keep their place inside
            val atlases = listOf("atlas", "night", "rundown", "extra", "extra_night", "extra_rundown")
            val frames = atlases.associateWith { atlasFrames("rooms/$it") }
            for (atlas in atlases) sheets["rooms/$atlas"] = readBmp32("rooms/$atlas.png")
            // Resource folders inside a jar can't be listed, so people/ carries an index.txt.
            val peopleNames = index("people")
            val peopleSheets = peopleNames.associateWith { readBmp32("people/$it.png") }
            // The native 12x24 sheets are blown up to the size of the 4x art, pixel for pixel. Then every
            // person is smoothed alike: switching smoothing on and off would end the draw call each time.
            val tallest = peopleSheets.values.maxOf { it.height }
            for ((name, sheet) in peopleSheets) {
                val times = tallest / sheet.height
                sheets["people/$name"] = if (times > 1 && tallest % sheet.height == 0) sheet.scaleNearest(times, times) else sheet
            }
            val drawn = WorldSprites.draw()
            for ((key, raster) in drawn) sheets["sprite/$key"] = raster.bitmap

            val packed = TexturePacker.pack(sheets)

            val rooms = HashMap<String, BmpSlice>()
            for (atlas in atlases) {
                val sheet = packed.getValue("rooms/$atlas")
                for ((name, r) in frames.getValue(atlas)) rooms[name] = sheet.sliceWithSize(r[0], r[1], r[2], r[3])
            }
            val people = peopleNames.associateWith { name ->
                val sheet = packed.getValue("people/$name")
                val w = sheet.width / 3
                (0 until 3).map { sheet.sliceWithSize(it * w, 0, w, sheet.height) }
            }
            val sprites = drawn.mapValues { (key, raster) ->
                val slice = packed.getValue("sprite/$key")
                WorldSprites.Sprite(slice, Anchor(raster.originX / slice.width, raster.originY / slice.height), raster.scale)
            }
            return Assets(rooms, people, sprites)
        }
    }
}
