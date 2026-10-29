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

    /** Odd extras that make the tower more fun; sightseers love them. */
    SPECIAL,

    /** Needed for stage 4, keeps some of the chaos away. */
    SECURITY,

    /** Teleporters connect to each other, wherever they are. */
    TELEPORTER,
    ;

    /** Rooms that get a tenant. */
    val rentable: Boolean get() = this == OFFICE || this == HOME || this == FOOD

    /** Stairs and elevators sit on their own layer in front of the rooms. */
    val isWay: Boolean get() = this == STAIRS || this == ELEVATOR
}

/**
 * A buildable room type.
 *
 * @param width width in segments; for [stretchable] types the width of a single click
 * @param cost price of the room, or of one segment for [stretchable] types
 * @param stretchable the player drags it to any width (the lobby)
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
    val minStage: Int = 0,
    val art: String,
    val capacity: Int = 0,
    val description: String = "",
    val stretchable: Boolean = false,
) {
    /** What a room of this type with [width] segments costs. */
    fun price(width: Int = this.width): Long = if (stretchable) cost * width else cost
}

object Catalog {
    val LOBBY = RoomType(
        id = "lobby", name = "Lobby", category = Category.WAYS, role = RoomRole.LOBBY,
        width = 6, cost = 800, art = "lobby_stage1_bruchbude", stretchable = true,
        description = "Eingang zum Turm. Nur im Erdgeschoss. Zum Verlängern ziehen.",
    )
    val CORRIDOR = RoomType(
        id = "corridor", name = "Flur", category = Category.WAYS, role = RoomRole.CORRIDOR,
        width = 6, cost = 500, art = "corridor",
        description = "Begehbarer Boden für die oberen Etagen.",
    )
    val STAIRS = RoomType(
        id = "stairs", name = "Treppe", category = Category.WAYS, role = RoomRole.STAIRS,
        width = 2, cost = 2_000, art = "stairs",
        description = "Führt eine Etage nach oben. Langsam, aber ehrlich. Senkrecht ziehen für mehrere Etagen.",
    )
    val ELEVATOR = RoomType(
        id = "elevator", name = "Aufzug", category = Category.WAYS, role = RoomRole.ELEVATOR,
        width = 2, cost = 6_000, art = "elevator_shaft",
        description = "Senkrecht ziehen. Enden greifen zum Verlängern. Darf durch Räume laufen.",
    )
    val OFFICE = RoomType(
        id = "office", name = "Büro", category = Category.OFFICES, role = RoomRole.OFFICE,
        width = 6, cost = 12_000, art = "office_empty", capacity = 6,
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

    val COPYROOM = RoomType(
        id = "copyroom", name = "Kopierraum der Ewigkeit", category = Category.SPECIAL, role = RoomRole.SPECIAL,
        width = 6, cost = 40_000, minStage = 2, art = "extra_copyroom",
        description = "Hebt die Laune im ganzen Turm. Niemand weiß, wer den Toner bezahlt.",
    )
    val SECURITY = RoomType(
        id = "security", name = "Sicherheitsdienst", category = Category.SPECIAL, role = RoomRole.SECURITY,
        width = 6, cost = 80_000, minStage = 3, art = "special_security",
        description = "Weniger Chaos bei Ereignissen. Pflicht für Stufe 4.",
    )
    val TELEPORTER = RoomType(
        id = "teleporter", name = "Teleporter", category = Category.SPECIAL, role = RoomRole.TELEPORTER,
        width = 2, cost = 150_000, minStage = 4, art = "special_teleporter",
        description = "Verbindet alle Teleporter miteinander. Meistens.",
    )
    val UFO_PAD = RoomType(
        id = "ufo", name = "Ufo-Landeplatz", category = Category.SPECIAL, role = RoomRole.SPECIAL,
        width = 6, cost = 500_000, minStage = 5, art = "special_ufo",
        description = "Für Besuch von ganz weit weg.",
    )

    val roomTypes: List<RoomType> =
        listOf(LOBBY, CORRIDOR, STAIRS, ELEVATOR, OFFICE, HOME, FOOD, COPYROOM, SECURITY, TELEPORTER, UFO_PAD)

    fun type(id: String): RoomType = roomTypes.first { it.id == id }
}
