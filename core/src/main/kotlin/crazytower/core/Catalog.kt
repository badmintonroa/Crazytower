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
enum class RoomRole {
    LOBBY,

    /** Empty walkable floor, connects rooms on upper floors. */
    CORRIDOR,

    /** Connects its floor with the floor above. */
    STAIRS,

    /** One floor of an elevator shaft; stacked modules form a shaft. */
    ELEVATOR,
    OFFICE,
    HOME,
    FOOD,
    ;

    /** Rooms that get a tenant. */
    val rentable: Boolean get() = this == OFFICE || this == HOME || this == FOOD
}

/**
 * A buildable room type.
 *
 * @param width width in segments
 * @param art key of the room art (without size suffix)
 * @param capacity workers (office, food) or residents (home) once a tenant moved in
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
    val capacity: Int = 0,
    val description: String = "",
)

object Catalog {
    val LOBBY = RoomType(
        id = "lobby", name = "Lobby", category = Category.WAYS, role = RoomRole.LOBBY,
        width = 6, cost = 5_000, art = "lobby_stage1_bruchbude",
        description = "Eingang zum Turm. Nur im Erdgeschoss.",
    )
    val CORRIDOR = RoomType(
        id = "corridor", name = "Flur", category = Category.WAYS, role = RoomRole.CORRIDOR,
        width = 6, cost = 500, art = "corridor",
        description = "Begehbarer Boden für die oberen Etagen.",
    )
    val STAIRS = RoomType(
        id = "stairs", name = "Treppe", category = Category.WAYS, role = RoomRole.STAIRS,
        width = 2, cost = 2_000, art = "stairs",
        description = "Führt eine Etage nach oben. Langsam, aber ehrlich.",
    )
    val ELEVATOR = RoomType(
        id = "elevator", name = "Aufzug", category = Category.WAYS, role = RoomRole.ELEVATOR,
        width = 2, cost = 6_000, art = "elevator_shaft",
        description = "Pro Etage ein Stück Schacht. Übereinander bauen.",
    )
    val OFFICE = RoomType(
        id = "office", name = "Büro", category = Category.OFFICES, role = RoomRole.OFFICE,
        width = 6, cost = 20_000, art = "office_empty", capacity = 6,
        description = "Wird vermietet. Wer einzieht, entscheidet der Turm.",
    )
    val HOME = RoomType(
        id = "home", name = "Wohnung", category = Category.HOMES, role = RoomRole.HOME,
        width = 6, cost = 15_000, art = "home_empty", capacity = 3,
        description = "Drei Zimmer, Küche, seltsame Nachbarn.",
    )
    val FOOD = RoomType(
        id = "food", name = "Imbiss", category = Category.FOOD, role = RoomRole.FOOD,
        width = 6, cost = 25_000, art = "food_empty", capacity = 3,
        description = "Hier essen Angestellte und Besucher.",
    )

    val roomTypes: List<RoomType> = listOf(LOBBY, CORRIDOR, STAIRS, ELEVATOR, OFFICE, HOME, FOOD)

    fun type(id: String): RoomType = roomTypes.first { it.id == id }
}
