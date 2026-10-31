package crazytower.core

/** The build bar categories, in the order of the hotkeys 1 to 6. */
enum class Category(private val text: Tr) {
    WAYS(Tr("Wege", "Ways")),
    OFFICES(Tr("Büros", "Offices")),
    HOMES(Tr("Wohnen", "Homes")),
    FOOD(Tr("Essen", "Food")),
    SPECIAL(Tr("Spezial", "Special")),
    DEMOLISH(Tr("Abriss", "Demolish")),
    ;

    val label: String get() = text.toString()
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

    /** One underground parking space for one car; earns parking fees, and luxury offices need some. */
    PARKING,

    /** Guests stay one night and pay when they check out; afterwards the room needs cleaning. */
    HOTEL,

    /** Cleaning staff who walk to dirty hotel rooms and make them ready for the next guests. */
    HOUSEKEEPING,

    /** Students who rent a bed by the day: many people on little room, but loud, and they need side jobs. */
    DORM,

    /** A corner shop: residents like having one, every shop serves [Simulation.SHOP_SERVES] flats. */
    SHOP,

    /** More families want to buy flats, see [Simulation.KITA_BUYERS]. */
    DAYCARE,

    /** Brings commuters for the offices, see [Simulation.SUBWAY_COMMUTERS]. */
    SUBWAY,

    /** Trade fair guests pay more for the hotel rooms, see [Simulation.CONFERENCE_HOTEL_BONUS]. */
    CONFERENCE,
    ;

    /** Rooms that get a tenant. */
    val rentable: Boolean get() = this == OFFICE || this == HOME || this == FOOD || this == HOTEL || this == HOUSEKEEPING || this == DORM || this == SHOP

    /** Its people live in the room (and are out during their plan) rather than work there. */
    val residential: Boolean get() = this == HOME || this == HOTEL || this == DORM

    /** People who live in the tower for good, as opposed to hotel guests. They can take jobs in the offices. */
    val housing: Boolean get() = this == HOME || this == DORM

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
    val name: Tr,
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
    val description: Tr = Tr("", ""),
    val stretchable: Boolean = false,
    /** Dragging sideways builds a row of these, see [Simulation.planRow]. */
    val dragRow: Boolean = false,
    /** Fills the whole gap it is clicked into, see [Simulation.planGap]. */
    val fillsGap: Boolean = false,
    val express: Boolean = false,
    /** How many a tower may have; null for no limit. */
    val maxCount: Int? = null,
    /** What the room does for the tower, one short line each, for the build menu. */
    val effects: List<Tr> = emptyList(),
    /** Only luxury tenants move in, see [TenantKind.luxury]. */
    val luxury: Boolean = false,
    /** Daily upkeep of this type when it differs from the role's, see [Simulation.UPKEEP]. */
    val upkeep: Long? = null,
    /** The price grows by this share of [cost] for every star above two, see [Simulation.listPrice]. */
    val pricePerStar: Double = 0.0,
    /** Only on this floor or higher. */
    val minFloor: Int? = null,
) {
    /** A luxury office: posh tenants who need parking spaces of their own. */
    val luxuryOffice: Boolean get() = luxury && role == RoomRole.OFFICE

    /** Priced per segment rather than per room. */
    val perSegment: Boolean get() = stretchable || fillsGap

    /** What a room of this type with [width] segments costs. */
    fun price(width: Int = this.width): Long = if (perSegment) cost * width else cost
}

object Catalog {
    private val ONCE = Tr("nur einmal pro Turm", "only one per tower")

    /** A fixed amount with its thousands grouped by [separator], for texts built once. */
    private fun grouped(value: Long, separator: Char): String = "%,d".format(value).replace(',', separator)

    val LOBBY = RoomType(
        id = "lobby", name = Tr("Lobby", "Lobby"), category = Category.WAYS, role = RoomRole.LOBBY,
        width = 6, cost = 800, art = "lobby_stage1_bruchbude", stretchable = true,
        description = Tr(
            "Eingang zum Turm. Im Erdgeschoss und als Sky-Lobby auf Etage 25, 50 und 75: Pflicht, bevor es darüber weitergeht. Zum Verlängern ziehen.",
            "The way into the tower. On the ground floor and as a sky lobby on floors 25, 50 and 75: a must before you build higher. Drag to make it longer.",
        ),
    )
    val CORRIDOR = RoomType(
        id = "corridor", name = Tr("Flur", "Corridor"), category = Category.WAYS, role = RoomRole.CORRIDOR,
        width = 6, cost = 500, art = "corridor",
        description = Tr("Begehbarer Boden für die oberen Etagen.", "Floor to walk on for the upper storeys."),
    )
    val FILLER = RoomType(
        id = "filler", name = Tr("Lückenfüller", "Gap filler"), category = Category.WAYS, role = RoomRole.CORRIDOR,
        width = 1, cost = 300, art = "corridor", fillsGap = true,
        description = Tr(
            "Klick in eine Lücke füllt sie ganz mit Flur, bis ${Simulation.MAX_GAP} Felder. Für Bauherren mit Augenmaß.",
            "Click into a gap to fill it with corridor, up to ${Simulation.MAX_GAP} tiles. For builders with a good eye.",
        ),
    )
    val STAIRS = RoomType(
        id = "stairs", name = Tr("Treppe", "Stairs"), category = Category.WAYS, role = RoomRole.STAIRS,
        width = 2, cost = 1_000, art = "stairs",
        description = Tr(
            "Führt eine Etage nach oben. Günstig, aber mehr als 5 Etagen läuft keiner. Senkrecht ziehen für mehrere Etagen.",
            "Goes up one floor. Cheap, but nobody walks more than 5 floors. Drag up or down for several floors.",
        ),
    )
    val ELEVATOR = RoomType(
        id = "elevator", name = Tr("Aufzug", "Elevator"), category = Category.WAYS, role = RoomRole.ELEVATOR,
        width = 2, cost = 6_000, art = "elevator_shaft",
        description = Tr(
            "Bis 25 Etagen. Jeder weitere Schacht kostet mehr. Senkrecht ziehen, Enden greifen zum Verlängern.",
            "Up to 25 floors. Every further shaft costs more. Drag up or down, grab the ends to extend it.",
        ),
    )
    val EXPRESS = RoomType(
        id = "express", name = Tr("Schnellaufzug", "Express elevator"), category = Category.WAYS, role = RoomRole.ELEVATOR,
        width = 2, cost = 15_000, minStage = 2, art = "elevator_shaft", express = true,
        description = Tr(
            "Bis 100 Etagen, dreimal so schnell, 40 Plätze pro Kabine. Hält nur im EG und auf jeder 5. Etage.",
            "Up to 100 floors, three times as fast, 40 seats per cabin. Only stops on the ground floor and every 5th floor.",
        ),
    )
    val OFFICE = RoomType(
        id = "office", name = Tr("Büro", "Office"), category = Category.OFFICES, role = RoomRole.OFFICE,
        width = 6, cost = 12_000, minStars = 1, art = "office_empty", capacity = 6, pricePerStar = Simulation.OFFICE_PRICE_PER_STAR,
        description = Tr("Wird vermietet. Wer einzieht, entscheidet der Turm.", "For rent. The tower decides who moves in."),
        effects = listOf(
            Tr("ab 3 Sternen teurer: die Handwerker lesen auch Bewertungen", "dearer from 3 stars: the builders read reviews too"),
            Tr("ab 3 Sternen Betriebskosten, bei 5 Sternen ${Simulation.RUNNING_COST_PERCENT * 3} % der Miete pro Tag", "running costs from 3 stars, at 5 stars ${Simulation.RUNNING_COST_PERCENT * 3} % of the rent a day"),
        ),
    )
    val HOME = RoomType(
        id = "home", name = Tr("Wohnung", "Flat"), category = Category.HOMES, role = RoomRole.HOME,
        width = 6, cost = 15_000, art = "home_empty", capacity = 3,
        description = Tr("Drei Zimmer, Küche, seltsame Nachbarn.", "Three rooms, kitchen, strange neighbours."),
    )
    val FOOD = RoomType(
        id = "food", name = Tr("Imbiss", "Snack bar"), category = Category.FOOD, role = RoomRole.FOOD,
        width = 6, cost = 25_000, minStars = 1, art = "food_empty", capacity = 3,
        description = Tr("Hier essen Angestellte und Besucher.", "Where workers and visitors eat."),
        effects = listOf(
            Tr("Essen hebt die Laune, ohne Imbiss bleibt sie mau", "a meal lifts the mood, without food it stays low"),
            Tr("ab 3 Sternen Betriebskosten, bei 5 Sternen ${Simulation.RUNNING_COST_PERCENT * 3} % der Miete pro Tag", "running costs from 3 stars, at 5 stars ${Simulation.RUNNING_COST_PERCENT * 3} % of the rent a day"),
        ),
    )

    val LUXURY_OFFICE = RoomType(
        id = "luxury_office", name = Tr("Luxusbüro", "Luxury office"), category = Category.OFFICES, role = RoomRole.OFFICE,
        width = 12, cost = 150_000, minStage = 4, art = "office_luxury_empty", capacity = 10, luxury = true, upkeep = 2_000,
        description = Tr("Marmor, Kunst an der Wand und ein Aquarium mit Anwalt.", "Marble, art on the walls and an aquarium with a lawyer in it."),
        effects = listOf(
            Tr("nur feine Mieter, die sehr viel Miete zahlen", "only posh tenants who pay a lot of rent"),
            Tr("unter ${Simulation.LUXURY_MOOD.toInt()} % Laune nur die halbe Miete", "half the rent below ${Simulation.LUXURY_MOOD.toInt()} % mood"),
            Tr("braucht ${Simulation.LUXURY_SPACES} eigene Parkplätze im Keller", "needs ${Simulation.LUXURY_SPACES} parking spaces of its own in the basement"),
            Tr("Unterhalt 2.000 \$ pro Tag", "upkeep \$2,000 a day"),
        ),
    )
    val HOTEL = RoomType(
        id = "hotel", name = Tr("Hotelzimmer", "Hotel room"), category = Category.HOMES, role = RoomRole.HOTEL,
        width = 6, cost = 40_000, minStage = 3, art = "hotel_empty", capacity = 2,
        description = Tr("Frische Handtücher, gelegentlich.", "Fresh towels, occasionally."),
        effects = listOf(
            Tr("Gäste checken ab 14 Uhr ein, zahlen beim Auschecken", "guests check in from 2 pm, pay on check-out"),
            Tr("danach schmutzig, bis der Reinigungsdienst kommt", "then dirty until housekeeping comes"),
            Tr("Unterhalt ${Simulation.HOTEL_UPKEEP} \$ pro Tag", "upkeep \$${Simulation.HOTEL_UPKEEP} a day"),
        ),
    )
    val HOUSEKEEPING = RoomType(
        id = "housekeeping", name = Tr("Reinigungsdienst", "Housekeeping"), category = Category.HOMES, role = RoomRole.HOUSEKEEPING,
        width = 6, cost = 60_000, minStage = 3, art = "housekeeping_empty", capacity = 2,
        description = Tr("Wischt alles weg. Auch Erinnerungen.", "Wipes everything away. Memories too."),
        effects = listOf(
            Tr("das Personal läuft zu schmutzigen Hotelzimmern", "the staff walk to dirty hotel rooms"),
            Tr("ein Zimmer putzen dauert ${Simulation.CLEAN_MINUTES} Minuten", "cleaning a room takes ${Simulation.CLEAN_MINUTES} minutes"),
            Tr("Unterhalt ${grouped(Simulation.HOUSEKEEPING_UPKEEP, '.')} \$ pro Tag", "upkeep \$${grouped(Simulation.HOUSEKEEPING_UPKEEP, ',')} a day"),
        ),
    )

    val COPYROOM = RoomType(
        id = "copyroom", name = Tr("Kopierraum der Ewigkeit", "Copy Room of Eternity"), category = Category.SPECIAL, role = RoomRole.SPECIAL,
        width = 6, cost = 40_000, minStage = 2, art = "extra_copyroom", maxCount = 1,
        description = Tr("Niemand weiß, wer den Toner bezahlt.", "Nobody knows who pays for the toner."),
        effects = listOf(
            Tr("Büroleute: +${Simulation.COPYROOM_MOOD.toInt()} Laune", "office workers: +${Simulation.COPYROOM_MOOD.toInt()} mood"),
            Tr("Sehenswürdigkeit für Besucher", "a sight for visitors"),
            ONCE,
        ),
    )
    val SECURITY = RoomType(
        id = "security", name = Tr("Sicherheitsdienst", "Security"), category = Category.SPECIAL, role = RoomRole.SECURITY,
        width = 6, cost = 80_000, minStage = 3, art = "special_security", maxCount = 1,
        description = Tr("Die Herren tragen Sonnenbrillen. Auch nachts.", "The gentlemen wear sunglasses. At night too."),
        effects = listOf(
            Tr("halb so viel Ärger, verhindert manchen ganz", "half the trouble, stops some of it entirely"),
            Tr("Pflicht für Stufe 4", "required for stage 4"),
            ONCE,
        ),
    )
    val TELEPORTER = RoomType(
        id = "teleporter", name = Tr("Teleporter", "Teleporter"), category = Category.SPECIAL, role = RoomRole.TELEPORTER,
        width = 2, cost = 150_000, minStage = 4, art = "special_teleporter", maxCount = 2,
        description = Tr("Kommt meistens dort an, wo man hinwollte.", "Usually arrives where you wanted to go."),
        effects = listOf(
            Tr("Leute springen sofort zum anderen Teleporter", "people jump straight to the other teleporter"),
            Tr("spart lange Aufzugfahrten", "saves long elevator rides"),
            Tr("genau ein Paar pro Turm", "exactly one pair per tower"),
        ),
    )
    val UFO_PAD = RoomType(
        id = "ufo", name = Tr("Ufo-Landeplatz", "UFO landing pad"), category = Category.SPECIAL, role = RoomRole.SPECIAL,
        width = 6, cost = 500_000, minStage = 5, art = "special_ufo", maxCount = 1,
        description = Tr("Für Besuch von ganz weit weg.", "For visitors from very far away."),
        effects = listOf(
            Tr("Landegebühr ${grouped(Simulation.UFO_FEE, '.')} \$ jeden Tag", "landing fee \$${grouped(Simulation.UFO_FEE, ',')} every day"),
            Tr("alle: +${Simulation.UFO_MOOD.toInt()} Laune (Aussicht!)", "everyone: +${Simulation.UFO_MOOD.toInt()} mood (the view!)"),
            ONCE,
        ),
    )

    val PARKING = RoomType(
        id = "parking_space", name = Tr("Parkplatz", "Parking space"), category = Category.SPECIAL, role = RoomRole.PARKING,
        width = 2, cost = 1_500, minStars = Lot.BASEMENT_STARS, basementOnly = true, art = "parking_space", dragRow = true, upkeep = 10,
        description = Tr(
            "Ein Platz für genau ein Auto. Waagerecht ziehen für eine ganze Reihe.",
            "Room for exactly one car. Drag sideways for a whole row.",
        ),
        effects = listOf(
            Tr(
                "1 Auto; Büros bringen ${Simulation.CARS_PER_OFFICE}, Wohnungen ${Simulation.CARS_PER_HOME}, Luxusbüros brauchen ${Simulation.LUXURY_SPACES}",
                "1 car; offices bring ${Simulation.CARS_PER_OFFICE}, flats ${Simulation.CARS_PER_HOME}, luxury offices need ${Simulation.LUXURY_SPACES}",
            ),
            Tr("${Simulation.PARKING_FEE} \$ pro geparktem Auto und Tag", "\$${Simulation.PARKING_FEE} per parked car and day"),
            Tr("nur im Keller, nur so viele wie Autos", "basement only, only as many as there are cars"),
        ),
    )

    // ---------------------------------------------------------------- from 3 stars: more ways to grow

    val DORM = RoomType(
        id = "dorm", name = Tr("Wohnheim", "Student dorm"), category = Category.HOMES, role = RoomRole.DORM,
        width = 6, cost = 20_000, minStars = 3, art = "dorm_empty", capacity = 6,
        description = Tr("Sechs Betten, eine Kochplatte, null Ruhe.", "Six beds, one hot plate, zero peace."),
        effects = listOf(
            Tr("6 Bewohner, Miete jeden Tag, kein Käufer nötig", "6 residents, rent every day, no buyer needed"),
            Tr("Studis brauchen Nebenjobs: höchstens halb so viele wie Büro-Angestellte", "students need side jobs: at most half as many as office workers"),
            Tr("laut: Wohnungen auf derselben Etage −${Simulation.DORM_NOISE.toInt()} Laune", "loud: flats on the same floor −${Simulation.DORM_NOISE.toInt()} mood"),
        ),
    )
    val SHOP = RoomType(
        id = "shop", name = Tr("Späti", "Corner shop"), category = Category.FOOD, role = RoomRole.SHOP,
        width = 6, cost = 30_000, minStars = 3, art = "shop_empty", capacity = 2,
        description = Tr("Milch, Batterien und Lebensweisheiten.", "Milk, batteries and wisdom."),
        effects = listOf(
            Tr("versorgt ${Simulation.SHOP_SERVES} Wohnungen und Wohnheime", "serves ${Simulation.SHOP_SERVES} flats and dorms"),
            Tr("Bewohner: bis +${Simulation.SHOP_MOOD.toInt()} Laune, wenn alle versorgt sind", "residents: up to +${Simulation.SHOP_MOOD.toInt()} mood when all are served"),
        ),
    )
    val KITA = RoomType(
        id = "kita", name = Tr("Kita", "Daycare"), category = Category.SPECIAL, role = RoomRole.DAYCARE,
        width = 6, cost = 50_000, minStars = 3, art = "special_kita", maxCount = 3, upkeep = 800,
        description = Tr("Hier wird gebaut, gemalt und gebissen.", "Building, painting and biting."),
        effects = listOf(
            Tr("+${Simulation.KITA_BUYERS} Wohnungskäufer pro Tag", "+${Simulation.KITA_BUYERS} flat buyers a day"),
            Tr("höchstens 3 pro Turm", "at most 3 per tower"),
            Tr("Unterhalt 800 \$ pro Tag", "upkeep \$800 a day"),
        ),
    )

    // ---------------------------------------------------------------- from 4 stars

    val PENTHOUSE = RoomType(
        id = "penthouse", name = Tr("Penthouse", "Penthouse"), category = Category.HOMES, role = RoomRole.HOME,
        width = 12, cost = 120_000, minStars = 4, art = "penthouse_empty", capacity = 4, luxury = true,
        minFloor = Simulation.PENTHOUSE_FLOOR,
        description = Tr("Mit Pool, Bar und Blick auf die Nachbarn.", "With a pool, a bar and a view of the neighbours."),
        effects = listOf(
            Tr("nur ab der ${Simulation.PENTHOUSE_FLOOR}. Etage, wegen der Aussicht", "only from floor ${Simulation.PENTHOUSE_FLOOR} up, for the view"),
            Tr("${Simulation.PENTHOUSE_FEES}× Hausgeld einer Wohnung, braucht nur einen Käufer", "${Simulation.PENTHOUSE_FEES}× the service charge of a flat, needs only one buyer"),
            Tr("viel Platz für nur 4 Bewohner", "lots of room for only 4 residents"),
        ),
    )
    val SUBWAY = RoomType(
        id = "subway", name = Tr("U-Bahn-Station", "Subway station"), category = Category.SPECIAL, role = RoomRole.SUBWAY,
        width = 12, cost = 300_000, minStars = 4, basementOnly = true, art = "special_subway", maxCount = 1, upkeep = 3_000,
        description = Tr("Der nächste Zug kommt gestern.", "The next train is due yesterday."),
        effects = listOf(
            Tr("+${grouped(Simulation.SUBWAY_COMMUTERS.toLong(), '.')} Pendler für die Büros", "+${grouped(Simulation.SUBWAY_COMMUTERS.toLong(), ',')} commuters for the offices"),
            Tr("nur im Keller, nur einmal pro Turm", "basement only, only one per tower"),
            Tr("Unterhalt 3.000 \$ pro Tag", "upkeep \$3,000 a day"),
        ),
    )
    val CONFERENCE = RoomType(
        id = "conference", name = Tr("Konferenzzentrum", "Conference centre"), category = Category.SPECIAL, role = RoomRole.CONFERENCE,
        width = 12, cost = 250_000, minStars = 4, art = "special_conference", maxCount = 1, upkeep = 2_000,
        description = Tr("Synergien, Flipcharts und Kekse, in dieser Reihenfolge.", "Synergies, flip charts and biscuits, in that order."),
        effects = listOf(
            Tr("Hotelgäste zahlen +${(Simulation.CONFERENCE_HOTEL_BONUS * 100).toInt()} % (Messepreise)", "hotel guests pay +${(Simulation.CONFERENCE_HOTEL_BONUS * 100).toInt()} % (trade fair prices)"),
            Tr("Sehenswürdigkeit für Besucher", "a sight for visitors"),
            ONCE,
            Tr("Unterhalt 2.000 \$ pro Tag", "upkeep \$2,000 a day"),
        ),
    )

    /** The old car park for 40 cars; saves that still have one get single spaces instead. */
    const val OLD_CAR_PARK = "parking"

    val roomTypes: List<RoomType> =
        listOf(
            LOBBY, CORRIDOR, FILLER, STAIRS, ELEVATOR, EXPRESS, OFFICE, LUXURY_OFFICE, HOME, DORM, PENTHOUSE, HOTEL, HOUSEKEEPING,
            FOOD, SHOP, COPYROOM, SECURITY, KITA, SUBWAY, CONFERENCE, TELEPORTER, UFO_PAD, PARKING,
        )

    fun type(id: String): RoomType = roomTypes.first { it.id == id }
}
