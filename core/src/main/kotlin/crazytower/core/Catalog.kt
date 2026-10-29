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

    /** Underground car park; earns parking fees for the cars of the people in the tower. */
    PARKING,
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
 * @param express an elevator that is fast and big but only stops on every fifth floor
 */
data class RoomType(
    val id: String,
    val name: String,
    val category: Category,
    val role: RoomRole,
    val width: Int,
    val cost: Long,
    val minStage: Int = 0,
    /** The tower needs to have reached this many stars. */
    val minStars: Int = 0,
    /** Only in the basement. */
    val basementOnly: Boolean = false,
    val art: String,
    val capacity: Int = 0,
    val description: String = "",
    val stretchable: Boolean = false,
    /** Fills the whole gap it is clicked into, see [Simulation.planGap]. */
    val fillsGap: Boolean = false,
    val express: Boolean = false,
) {
    /** Priced per segment rather than per room. */
    val perSegment: Boolean get() = stretchable || fillsGap

    /** What a room of this type with [width] segments costs. */
    fun price(width: Int = this.width): Long = if (perSegment) cost * width else cost
}

object Catalog {
    val LOBBY = RoomType(
        id = "lobby", name = "Lobby", category = Category.WAYS, role = RoomRole.LOBBY,
        width = 6, cost = 800, art = "lobby_stage1_bruchbude", stretchable = true,
        description = "Eingang zum Turm. Im Erdgeschoss und als Sky-Lobby alle 10 Etagen. Zum Verlängern ziehen.",
    )
    val CORRIDOR = RoomType(
        id = "corridor", name = "Flur", category = Category.WAYS, role = RoomRole.CORRIDOR,
        width = 6, cost = 500, art = "corridor",
        description = "Begehbarer Boden für die oberen Etagen.",
    )
    val FILLER = RoomType(
        id = "filler", name = "Lückenfüller", category = Category.WAYS, role = RoomRole.CORRIDOR,
        width = 1, cost = 300, art = "corridor", fillsGap = true,
        description = "Klick in eine Lücke füllt sie ganz mit Flur, bis ${Simulation.MAX_GAP} Felder. Für Bauherren mit Augenmaß.",
    )
    val STAIRS = RoomType(
        id = "stairs", name = "Treppe", category = Category.WAYS, role = RoomRole.STAIRS,
        width = 2, cost = 1_000, art = "stairs",
        description = "Führt eine Etage nach oben. Günstig, aber mehr als 5 Etagen läuft keiner. Senkrecht ziehen für mehrere Etagen.",
    )
    val ELEVATOR = RoomType(
        id = "elevator", name = "Aufzug", category = Category.WAYS, role = RoomRole.ELEVATOR,
        width = 2, cost = 6_000, art = "elevator_shaft",
        description = "Senkrecht ziehen. Enden greifen zum Verlängern. Darf durch Räume laufen.",
    )
    val EXPRESS = RoomType(
        id = "express", name = "Schnellaufzug", category = Category.WAYS, role = RoomRole.ELEVATOR,
        width = 2, cost = 15_000, minStage = 2, art = "elevator_shaft", express = true,
        description = "Dreimal so schnell, 40 Plätze pro Kabine. Hält nur in der Lobby und auf jeder 5. Etage.",
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

    val PARKING = RoomType(
        id = "parking", name = "Parkhaus", category = Category.SPECIAL, role = RoomRole.PARKING,
        width = 6, cost = 60_000, minStars = Lot.BASEMENT_STARS, basementOnly = true, art = "special_parking",
        description = "Nur im Keller. Parkgebühren für die Autos der Leute im Turm. Ein Auto parkt seit 1987 quer.",
    )

    val roomTypes: List<RoomType> =
        listOf(LOBBY, CORRIDOR, FILLER, STAIRS, ELEVATOR, EXPRESS, OFFICE, HOME, FOOD, COPYROOM, SECURITY, TELEPORTER, UFO_PAD, PARKING)

    fun type(id: String): RoomType = roomTypes.first { it.id == id }
}
