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

    /** Guests stay one night and pay when they check out; afterwards the room needs cleaning. */
    HOTEL,

    /** Cleaning staff who walk to dirty hotel rooms and make them ready for the next guests. */
    HOUSEKEEPING,
    ;

    /** Rooms that get a tenant. */
    val rentable: Boolean get() = this == OFFICE || this == HOME || this == FOOD || this == HOTEL || this == HOUSEKEEPING

    /** Its people live in the room (and are out during their plan) rather than work there. */
    val residential: Boolean get() = this == HOME || this == HOTEL

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
    /** How many a tower may have; null for no limit. */
    val maxCount: Int? = null,
    /** What the room does for the tower, one short line each, for the build menu. */
    val effects: List<String> = emptyList(),
    /** Only luxury tenants move in, see [TenantKind.luxury]. */
    val luxury: Boolean = false,
    /** Daily upkeep of this type when it differs from the role's, see [Simulation.UPKEEP]. */
    val upkeep: Long? = null,
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
        description = "Eingang zum Turm. Im Erdgeschoss und als Sky-Lobby auf Etage 25, 50 und 75: Pflicht, bevor es darüber weitergeht. Zum Verlängern ziehen.",
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
        description = "Bis 25 Etagen. Jeder weitere Schacht kostet mehr. Senkrecht ziehen, Enden greifen zum Verlängern.",
    )
    val EXPRESS = RoomType(
        id = "express", name = "Schnellaufzug", category = Category.WAYS, role = RoomRole.ELEVATOR,
        width = 2, cost = 15_000, minStage = 2, art = "elevator_shaft", express = true,
        description = "Bis 100 Etagen, dreimal so schnell, 40 Plätze pro Kabine. Hält nur im EG und auf jeder 5. Etage.",
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

    val LUXURY_OFFICE = RoomType(
        id = "luxury_office", name = "Luxusbüro", category = Category.OFFICES, role = RoomRole.OFFICE,
        width = 12, cost = 150_000, minStage = 4, art = "office_empty", capacity = 10, luxury = true, upkeep = 2_000,
        description = "Marmor, Kunst an der Wand und ein Aquarium mit Anwalt.",
        effects = listOf(
            "nur feine Mieter, die sehr viel Miete zahlen",
            "unter ${Simulation.LUXURY_MOOD.toInt()} % Laune nur die halbe Miete",
            "Unterhalt 2.000 \$ pro Tag",
        ),
    )
    val HOTEL = RoomType(
        id = "hotel", name = "Hotelzimmer", category = Category.HOMES, role = RoomRole.HOTEL,
        width = 6, cost = 40_000, minStage = 3, art = "home_empty", capacity = 2,
        description = "Frische Handtücher, gelegentlich.",
        effects = listOf(
            "Gäste checken ab 14 Uhr ein, zahlen beim Auschecken",
            "danach schmutzig, bis der Reinigungsdienst kommt",
            "Unterhalt ${Simulation.HOTEL_UPKEEP} \$ pro Tag",
        ),
    )
    val HOUSEKEEPING = RoomType(
        id = "housekeeping", name = "Reinigungsdienst", category = Category.HOMES, role = RoomRole.HOUSEKEEPING,
        width = 6, cost = 60_000, minStage = 3, art = "home_empty", capacity = 2,
        description = "Wischt alles weg. Auch Erinnerungen.",
        effects = listOf(
            "das Personal läuft zu schmutzigen Hotelzimmern",
            "ein Zimmer putzen dauert ${Simulation.CLEAN_MINUTES} Minuten",
            "Unterhalt ${"%,d".format(Simulation.HOUSEKEEPING_UPKEEP).replace(',', '.')} \$ pro Tag",
        ),
    )

    val COPYROOM = RoomType(
        id = "copyroom", name = "Kopierraum der Ewigkeit", category = Category.SPECIAL, role = RoomRole.SPECIAL,
        width = 6, cost = 40_000, minStage = 2, art = "extra_copyroom", maxCount = 1,
        description = "Niemand weiß, wer den Toner bezahlt.",
        effects = listOf("Büroleute: jede Nacht +${Simulation.COPYROOM_MOOD.toInt()} Laune", "Sehenswürdigkeit für Besucher", "nur einmal pro Turm"),
    )
    val SECURITY = RoomType(
        id = "security", name = "Sicherheitsdienst", category = Category.SPECIAL, role = RoomRole.SECURITY,
        width = 6, cost = 80_000, minStage = 3, art = "special_security", maxCount = 1,
        description = "Die Herren tragen Sonnenbrillen. Auch nachts.",
        effects = listOf("halb so viel Ärger, verhindert manchen ganz", "Pflicht für Stufe 4", "nur einmal pro Turm"),
    )
    val TELEPORTER = RoomType(
        id = "teleporter", name = "Teleporter", category = Category.SPECIAL, role = RoomRole.TELEPORTER,
        width = 2, cost = 150_000, minStage = 4, art = "special_teleporter", maxCount = 2,
        description = "Kommt meistens dort an, wo man hinwollte.",
        effects = listOf("Leute springen sofort zum anderen Teleporter", "spart lange Aufzugfahrten", "genau ein Paar pro Turm"),
    )
    val UFO_PAD = RoomType(
        id = "ufo", name = "Ufo-Landeplatz", category = Category.SPECIAL, role = RoomRole.SPECIAL,
        width = 6, cost = 500_000, minStage = 5, art = "special_ufo", maxCount = 1,
        description = "Für Besuch von ganz weit weg.",
        effects = listOf("Landegebühr ${"%,d".format(Simulation.UFO_FEE).replace(',', '.')} \$ jeden Tag", "alle: jede Nacht +${Simulation.UFO_MOOD.toInt()} Laune (Aussicht!)", "nur einmal pro Turm"),
    )

    val PARKING = RoomType(
        id = "parking", name = "Parkhaus", category = Category.SPECIAL, role = RoomRole.PARKING,
        width = 6, cost = 60_000, minStars = Lot.BASEMENT_STARS, basementOnly = true, art = "special_parking",
        description = "Ein Auto parkt seit 1987 quer.",
        effects = listOf(
            "${Simulation.CARS_PER_PARKING} Plätze, gebucht von Büros (${Simulation.CARS_PER_OFFICE} Autos) und Wohnungen (${Simulation.CARS_PER_HOME})",
            "${Simulation.PARKING_FEE} \$ pro gebuchtem Platz und Tag",
            "nur im Keller, nur so viele wie gebraucht",
        ),
    )

    val roomTypes: List<RoomType> =
        listOf(LOBBY, CORRIDOR, FILLER, STAIRS, ELEVATOR, EXPRESS, OFFICE, LUXURY_OFFICE, HOME, HOTEL, HOUSEKEEPING, FOOD, COPYROOM, SECURITY, TELEPORTER, UFO_PAD, PARKING)

    fun type(id: String): RoomType = roomTypes.first { it.id == id }
}
