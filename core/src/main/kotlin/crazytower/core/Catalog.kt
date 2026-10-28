package crazytower.core

/** The build bar categories, in the order of the hotkeys 1 to 6. */
enum class Category(val label: String) {
    WAYS("Wege"),
    OFFICES("Büros"),
    HOMES("Wohnen"),
    FOOD("Essen"),
    SPECIAL("Spezial"),
    DEMOLISH("Abriss"),
}

/** How a room behaves in the simulation. */
enum class RoomRole { LOBBY }

/**
 * A buildable room type.
 *
 * @param width width in segments
 * @param art key of the room art (without size suffix)
 */
data class RoomType(
    val id: String,
    val name: String,
    val category: Category,
    val role: RoomRole,
    val width: Int,
    val cost: Long,
    val minStage: Int = 1,
    val art: String,
)

object Catalog {
    val LOBBY = RoomType(
        id = "lobby", name = "Lobby", category = Category.WAYS, role = RoomRole.LOBBY,
        width = 6, cost = 5_000, art = "lobby_stage1_bruchbude",
    )

    val roomTypes: List<RoomType> = listOf(LOBBY)

    fun type(id: String): RoomType = roomTypes.first { it.id == id }
}
