package crazytower.game

import korlibs.image.bitmap.BmpSlice
import korlibs.image.bitmap.slice
import korlibs.image.format.readBitmap
import korlibs.io.file.std.resourcesVfs

import korlibs.math.geom.RectangleInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Room art from the 4x atlas (576x192 per 6 segments) and people sprite sheets (three 12x24 frames). */
class Assets private constructor(
    private val rooms: Map<String, BmpSlice>,
    private val people: Map<String, List<BmpSlice>>,
) {
    fun hasRoom(key: String): Boolean = key in rooms

    fun room(key: String): BmpSlice = rooms[key] ?: rooms.getValue(FALLBACK_ROOM)

    /** Frame 0 = standing, 1 and 2 = walking. */
    fun person(look: String, frame: Int): BmpSlice = (people[look] ?: people.getValue("casual"))[frame]

    companion object {
        const val FALLBACK_ROOM = "office_empty"
        const val ROOM_ART_SCALE = 4
        const val PERSON_W = 12
        const val PERSON_H = 24

        private suspend fun index(folder: String): List<String> =
            resourcesVfs["$folder/index.txt"].readString().lines().map { it.trim() }.filter { it.isNotEmpty() }

        /** Reads a TexturePacker-style atlas (hash format): one PNG plus a JSON with a frame per room key. */
        private suspend fun loadAtlas(base: String): Map<String, BmpSlice> {
            val sheet = resourcesVfs["$base.png"].readBitmap()
            val frames = Json.parseToJsonElement(resourcesVfs["$base.json"].readString())
                .jsonObject.getValue("frames").jsonObject
            return frames.entries.associate { (name, value) ->
                val f = value.jsonObject.getValue("frame").jsonObject
                fun n(k: String) = f.getValue(k).jsonPrimitive.int
                name.removeSuffix(".png") to sheet.slice(RectangleInt(n("x"), n("y"), n("w"), n("h")))
            }
        }

        suspend fun load(): Assets {
            // Resource folders inside a jar can't be listed, so people/ carries an index.txt.
            val rooms = loadAtlas("rooms/atlas") + loadAtlas("rooms/extra") + loadAtlas("rooms/night")
            val people = index("people").associateWith { name ->
                val sheet = resourcesVfs["people/$name.png"].readBitmap()
                (0 until 3).map { sheet.slice(RectangleInt(it * PERSON_W, 0, PERSON_W, PERSON_H)) }
            }
            return Assets(rooms, people)
        }
    }
}
