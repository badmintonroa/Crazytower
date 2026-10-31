package crazytower.core

import kotlinx.serialization.Serializable

/**
 * Everything a rich tower can spend its money on from stage 4 on, and the moon mission after
 * stage 5. Stored in the save game as one block; older saves start with an empty one.
 */
@Serializable
class Endgame {
    /** Station parts on the top floor, 0 to [Mission.parts].size; all of them means lift-off. */
    var parts: Int = 0

    /** Progress of the mission tasks by id; see [Mission]. */
    var progress: MutableMap<String, Long> = LinkedHashMap()

    /** The next station part can't be built before this minute: the press made fun of the queues. */
    var delayedUntil: Double = 0.0

    /** When the moon lift opened, or null. */
    var liftOffDay: Int? = null

    /** Insurance policies the tower pays for every day, by id; see [Insurances]. */
    var insurances: MutableSet<String> = LinkedHashSet()

    /** The ad campaign runs until this minute. */
    var campaignUntil: Double = 0.0

    /** When the last tower party was thrown. */
    var lastParty: Double = -Promotion.PARTY_COOLDOWN

    /** Decorations bought, by id; see [Decor]. */
    var decor: MutableSet<String> = LinkedHashSet()

    /** Side entrances bought from Granny Hildegard, 0 to [Gates.all].size. */
    var gates: Int = 0

    /** Research done in the Laboratory of Questionable Science, by id; see [Research]. */
    var research: MutableSet<String> = LinkedHashSet()

    // ---- today's sightseers, for the press; not worth saving
    @kotlinx.serialization.Transient var spectatorsHappy: Int = 0
    @kotlinx.serialization.Transient var spectatorsUnhappy: Int = 0
    @kotlinx.serialization.Transient var spectatorsToday: Int = 0
}

/** Money sinks start at this stage: before it, every dollar goes into rooms anyway. */
const val SINK_STAGE = 4

/** Share of yesterday's income, at least [min]: what grows with the tower stays noticeable. */
fun Simulation.shareOfIncome(percent: Double, min: Long): Long =
    maxOf(min, (finance.income(clock.day - 1) * percent / 100).toLong()) / 100 * 100

// =====================================================================================
// Room upgrades
// =====================================================================================

/**
 * Doing up rented rooms: three levels, each with its own name for every tenant. Every level
 * brings [RENT_BONUS] more rent (or service charge) and [MOOD_BONUS] more mood. The upgrade
 * belongs to the room and stays when the tenant changes, only its name follows the tenant.
 */
object Upgrades {
    const val MAX_LEVEL = 3
    const val RENT_BONUS = 0.15
    const val MOOD_BONUS = 3f

    /** What level 1 to 3 cost for a room six tiles wide; wider rooms cost more. */
    val COSTS = listOf(50_000L, 250_000L, 1_250_000L)

    private val ROLES = setOf(RoomRole.OFFICE, RoomRole.HOME, RoomRole.FOOD, RoomRole.HOTEL, RoomRole.DORM, RoomRole.SHOP)

    fun applies(room: Room): Boolean = room.type.role in ROLES

    fun cost(room: Room, level: Int = room.upgrade + 1): Long = COSTS[level - 1] * room.width / 6

    fun rentFactor(room: Room): Double = 1.0 + RENT_BONUS * room.upgrade

    /** The name of [level] for the tenant of [room]. */
    fun name(room: Room, level: Int): String {
        val names = NAMES[room.tenant?.id] ?: ROLE_NAMES[room.type.role] ?: ROLE_NAMES.getValue(RoomRole.OFFICE)
        return names[(level - 1).coerceIn(0, 2)].toString()
    }

    private fun t(de1: String, en1: String, de2: String, en2: String, de3: String, en3: String) =
        listOf(Tr(de1, en1), Tr(de2, en2), Tr(de3, en3))

    private val NAMES: Map<String, List<Tr>> = mapOf(
        "clown" to t("Hüpfburg-Boden", "Bouncy castle floor", "Konfetti-Klimaanlage", "Confetti air conditioning", "Manege mit Trapez zum Chefbüro", "Circus ring with a trapeze to the boss"),
        "vampire" to t("Sarg-Ruheraum", "Coffin nap room", "Blutbank-Bar", "Blood bank bar", "Fensterlose Penthouse-Gruft", "Windowless penthouse crypt"),
        "accountant" to t("Taschenrechner mit Goldtasten", "Calculator with gold keys", "Belegsortier-Roboter", "Receipt sorting robot", "Steuerparadies-Tapete", "Tax haven wallpaper"),
        "startup" to t("Größeres Bällebad", "Bigger ball pit", "Rutsche ins Meeting", "Slide into the meeting", "Eigenes Einhorn (Plüsch)", "Unicorn of its own (plush)"),
        "callcenter" to t("Headsets mit Fahrstuhlmusik", "Headsets with lift music", "Warteschleifen-Orchester", "Hold music orchestra", "Rückruf-Rakete", "Callback rocket"),
        "excuses" to t("Ausreden-Generator", "Excuse generator", "Archiv der besten Ausreden", "Archive of the best excuses", "Zeitschleife für Verspätete", "Time loop for latecomers"),
        "socks" to t("Socken-Sortiermaschine", "Sock sorting machine", "Waschmaschinen-Detektiv", "Washing machine detective", "Museum des verlorenen Pendants", "Museum of the lost partner"),
        "ministry" to t("Stempel mit Turbo", "Turbo stamp", "Formular-Rutsche", "Form chute", "Zuständigkeits-Orakel", "Oracle of responsibility"),
        "timetravel" to t("Kalender von übermorgen", "Calendar from the day after tomorrow", "Gestern-Lounge", "Yesterday lounge", "Eigene Zeitzone", "Time zone of its own"),
        "supervillain" to t("Drehstuhl mit Katzenhalter", "Swivel chair with cat holder", "Haifischbecken (vegan)", "Shark tank (vegan)", "Vulkan-Imitat im Besprechungsraum", "Fake volcano in the meeting room"),
        "alien" to t("Antigravitations-Teppich", "Anti-gravity carpet", "Heimweh-Planetarium", "Homesick planetarium", "Beam-Empfang", "Beam-in reception"),
        "hedgefund" to t("Goldener Börsenticker", "Golden stock ticker", "Wettbüro fürs Wetter", "Bookmaker for the weather", "Geldspeicher zum Reinspringen", "Money bin to dive into"),
        "dragonbank" to t("Feuerfester Teppich", "Fireproof carpet", "Hort-Erweiterung", "Hoard extension", "Tresor mit Höhlenklima", "Vault with cave climate"),
        "atlantis" to t("Pfützen-Empfang", "Puddle reception", "Gezeitenkalender", "Tide calendar", "Unterwasser-Konferenzraum", "Underwater conference room"),
        "rocket" to t("Countdown-Uhr", "Countdown clock", "Mini-Startrampe für Kaffeetassen", "Mini launch pad for coffee cups", "Schwerelos-Großraumbüro", "Zero-g open-plan office"),
        "family" to t("Echter Balkon", "A real balcony", "Grill mit Fernbedienung", "Remote-controlled barbecue", "Familien-Hüpfburg", "Family bouncy castle"),
        "students" to t("Zweite Kochplatte", "Second hot plate", "Pfandflaschen-Tresor", "Deposit bottle vault", "WG-Kino", "Flatshare cinema"),
        "retirees" to t("Fensterbank-Kissen", "Window sill cushion", "Nachbarschafts-Fernglas", "Neighbourhood binoculars", "Kaffeefahrt-Terminal", "Coach tour terminal"),
        "influencer" to t("Ringlicht", "Ring light", "Selfie-Spiegelsaal", "Selfie hall of mirrors", "Eigener Livestream-Turm", "Live-stream tower of her own"),
        "penguins" to t("Eiswürfel-Bett", "Ice cube bed", "Fischtheke", "Fish counter", "Privater Eisberg", "Private iceberg"),
        "ghost" to t("Knarrende Dielen", "Creaking floorboards", "Spiegel ohne Spiegelbild", "Mirror without reflection", "Eigenes Spukschloss-Zimmer", "Haunted castle room"),
        "astronauts" to t("Schwerelos-Hängematte", "Zero-g hammock", "Tubennahrung-Bar", "Tube food bar", "Mondglobus mit Heimweh-Knopf", "Moon globe with a homesick button"),
    )

    private val ROLE_NAMES: Map<RoomRole, List<Tr>> = mapOf(
        RoomRole.OFFICE to t("Kaffeemaschine mit Gefühl", "Coffee machine with feelings", "Ergonomische Hängematten", "Ergonomic hammocks", "Pool im Besprechungsraum", "Pool in the meeting room"),
        RoomRole.HOME to t("Fußbodenheizung", "Underfloor heating", "Whirlpool in der Küche", "Hot tub in the kitchen", "Eigener Aufzug ins Bett", "Private lift into bed"),
        RoomRole.FOOD to t("Soßen-Brunnen", "Sauce fountain", "Pommes-Förderband", "Chips conveyor belt", "Sterneküche mit Sternen an der Decke", "Star cuisine with stars on the ceiling"),
        RoomRole.HOTEL to t("Minibar mit Mini-Barkeeper", "Minibar with a mini barman", "Kissen-Menü", "Pillow menu", "Präsidenten-Suite (ohne Präsident)", "Presidential suite (president not included)"),
        RoomRole.DORM to t("Matratzen ohne Kuhle", "Mattresses without a dip", "Partyraum mit Schallschutz", "Soundproof party room", "Ewige-Studis-Lounge", "Eternal students' lounge"),
        RoomRole.SHOP to t("Einkaufswagen mit Turbo", "Turbo shopping trolleys", "Sprechende Kasse", "Talking till", "Kühlregal mit Aussicht", "Fridge aisle with a view"),
    )
}

/** Why [room] can't be done up right now, or null. */
fun Simulation.upgradeBlocked(room: Room): String? = when {
    !Upgrades.applies(room) -> tr("Hier gibt es nichts auszubauen", "Nothing to upgrade here")
    stage < SINK_STAGE -> tr("Ausbau erst ab Stufe $SINK_STAGE", "Upgrades only from stage $SINK_STAGE")
    room.upgrade >= Upgrades.MAX_LEVEL -> tr("Schon ganz ausgebaut", "Fully upgraded already")
    money < Upgrades.cost(room) -> tr("Zu wenig Geld", "Not enough money")
    else -> null
}

/** Does up [room] by one level. */
fun Simulation.upgradeRoom(room: Room): PlaceCheck {
    upgradeBlocked(room)?.let { return PlaceCheck.no(it) }
    val level = room.upgrade + 1
    book(-Upgrades.cost(room), MoneyKind.UPGRADE, Upgrades.name(room, level), room.center)
    room.upgrade = level
    bumpLooks()
    val who = room.tenant?.name?.toString() ?: room.type.name.toString()
    news.post(tr("$who bekommt: ${Upgrades.name(room, level)}. Die Miete steigt, die Laune auch.", "$who gets: ${Upgrades.name(room, level)}. Rent goes up, and so does the mood."), room.center)
    return PlaceCheck.OK
}

// =====================================================================================
// Elevator tuning
// =====================================================================================

/**
 * Three levels for an elevator shaft: a turbo motor (faster), a string quartet (people wait
 * longer before they get grumpy) and a lift boy with a monocle (more people per cabin, quicker doors).
 */
object Tuning {
    const val MAX_LEVEL = 3
    const val TURBO = 1
    const val QUARTET = 2
    const val LIFTBOY = 3
    const val TURBO_FACTOR = 0.7f
    const val QUARTET_PATIENCE = 4.0
    const val LIFTBOY_CAPACITY = 1.5f

    val COSTS = listOf(100_000L, 500_000L, 2_000_000L)

    val NAMES = listOf(
        Tr("Turbo-Motor", "Turbo motor"),
        Tr("Fahrstuhlmusik vom Streichquartett", "Lift music by a string quartet"),
        Tr("Liftboy mit Monokel", "Lift boy with a monocle"),
    )

    val EFFECTS = listOf(
        Tr("30 % schneller", "30 % faster"),
        Tr("Wartende werden ${QUARTET_PATIENCE.toInt()} Min. später grantig", "people waiting get grumpy ${QUARTET_PATIENCE.toInt()} min. later"),
        Tr("50 % mehr Leute pro Kabine, Türen doppelt so schnell", "50 % more people per cabin, doors twice as fast"),
    )

    fun cost(car: crazytower.core.transport.Car): Long = COSTS[car.tuning]
}

fun Simulation.tuningBlocked(car: crazytower.core.transport.Car): String? = when {
    stage < SINK_STAGE -> tr("Tuning erst ab Stufe $SINK_STAGE", "Tuning only from stage $SINK_STAGE")
    car.tuning >= Tuning.MAX_LEVEL -> tr("Schneller geht's nicht", "It doesn't get any faster")
    money < Tuning.cost(car) -> tr("Zu wenig Geld", "Not enough money")
    else -> null
}

/** Tunes the shaft [car] by one level; every module remembers it for the save game. */
fun Simulation.tuneElevator(car: crazytower.core.transport.Car): PlaceCheck {
    tuningBlocked(car)?.let { return PlaceCheck.no(it) }
    val level = car.tuning + 1
    book(-Tuning.cost(car), MoneyKind.TUNING, Tuning.NAMES[level - 1].toString(), Loc(car.bottom, car.centerX))
    for (f in car.floors) tower.wayAt(f, car.x)?.tuning = level
    car.tuning = level
    // the rides got shorter: the route planner has to know
    tower.touch()
    news.post(
        when (level) {
            1 -> tr("Der Aufzug hat jetzt einen Turbo-Motor. Die Frisuren der Fahrgäste auch.", "The elevator now has a turbo motor. So do the passengers' hairdos.")
            2 -> tr("Im Aufzug spielt jetzt ein Streichquartett. Die Wartenden summen mit.", "A string quartet now plays in the elevator. The people waiting hum along.")
            else -> tr("Der neue Liftboy trägt Monokel und sagt „Bitte zurücktreten“. Alle treten zurück.", "The new lift boy wears a monocle and says \"Stand back, please\". Everybody stands back.")
        },
        Loc(car.bottom, car.centerX),
    )
    return PlaceCheck.OK
}

// =====================================================================================
// Insurance
// =====================================================================================

/** A daily policy against some events: they are mostly stopped before they start. */
class Insurance(val id: String, val name: Tr, val against: Set<String>, val percent: Double, val stopped: Tr)

object Insurances {
    /** Share of the insured events that never happen. */
    const val STOPS = 0.8f
    const val MIN_FEE = 2_000L

    val all = listOf(
        Insurance("pigeons", Tr("Tauben-Versicherung", "Pigeon insurance"), setOf("pigeons"), 1.0,
            Tr("Die Tauben wollten einziehen. Die Versicherung hat ihnen eine Zweitwohnung im Park bezahlt.", "The pigeons wanted to move in. The insurance paid for a second home in the park.")),
        Insurance("clowns", Tr("Clown-Gewerkschaftsvertrag", "Clown union agreement"), setOf("clown", "clownriot"), 2.0,
            Tr("Ein Büro wollte Clowns werden. Laut Tarifvertrag erst nach Feierabend.", "An office wanted to turn into clowns. Under the union agreement, only after hours.")),
        Insurance("garlic", Tr("Knoblauch-Abo", "Garlic subscription"), setOf("blooddrive"), 1.0,
            Tr("Die Vampire wollten zur Blutspende laden. Der Flur riecht leider noch nach Knoblauch.", "The vampires wanted to hold a blood drive. Sadly, the corridor still smells of garlic.")),
        Insurance("hamster", Tr("Notstrom-Hamsterrad", "Emergency hamster wheel"), setOf("blackout", "firealarm"), 2.0,
            Tr("Kurz war der Strom weg. Hamster Horst ist sofort losgerannt.", "The power was gone for a moment. Horst the hamster started running at once.")),
        Insurance("elevator", Tr("Aufzug-Schutzbrief", "Elevator protection plan"), setOf("wedding", "karaoke"), 3.0,
            Tr("Im Aufzug wollte jemand feiern. Der Schutzbrief hat freundlich abgelehnt.", "Somebody wanted to party in the elevator. The protection plan politely declined.")),
    )

    fun byId(id: String): Insurance? = all.firstOrNull { it.id == id }

    fun fee(sim: Simulation, insurance: Insurance): Long = sim.shareOfIncome(insurance.percent, MIN_FEE)
}

fun Simulation.insured(eventId: String): Insurance? =
    endgame.insurances.mapNotNull { Insurances.byId(it) }.firstOrNull { eventId in it.against }

/** Takes out or cancels a policy; returns why not, or null. */
fun Simulation.toggleInsurance(id: String): String? {
    val insurance = Insurances.byId(id) ?: return tr("Gibt es nicht", "Doesn't exist")
    if (id in endgame.insurances) {
        endgame.insurances -= id
        news.post(tr("${insurance.name} gekündigt. Der Vertreter weint ein bisschen.", "${insurance.name} cancelled. The salesman cries a little."))
        return null
    }
    if (stage < SINK_STAGE) return tr("Versicherungen erst ab Stufe $SINK_STAGE", "Insurance only from stage $SINK_STAGE")
    endgame.insurances += id
    news.post(tr("${insurance.name} abgeschlossen. Kostet jeden Tag ${insurance.percent.toInt()} % der Einnahmen.", "${insurance.name} taken out. Costs ${insurance.percent.toInt()} % of the income every day."))
    return null
}

/** Midnight: every policy costs its share of yesterday's income. */
internal fun Simulation.payInsurances() {
    for (insurance in endgame.insurances.mapNotNull { Insurances.byId(it) }) {
        book(-Insurances.fee(this, insurance), MoneyKind.INSURANCE, insurance.name.toString())
    }
}

// =====================================================================================
// Ads and the tower party
// =====================================================================================

object Promotion {
    const val CAMPAIGN_PERCENT = 10.0
    const val CAMPAIGN_MIN = 50_000L
    const val CAMPAIGN_DAYS = 3
    const val PARTY_PERCENT = 20.0
    const val PARTY_MIN = 100_000L
    const val PARTY_COOLDOWN = 7.0 * GameClock.MINUTES_PER_DAY
    const val PARTY_MOOD = 10f
    const val PARTY_HOURS = 48

    /** More sightseers, VIPs and spectators while a campaign runs. */
    const val CAMPAIGN_VISITORS = 1.5

    val CAMPAIGNS = listOf(
        Tr("Ein Zeppelin mit Banner kreist um den Turm: „CrazyTower: Jetzt mit Aufzug!“", "A zeppelin with a banner circles the tower: \"CrazyTower: now with elevators!\""),
        Tr("Graf Zahlula wirbt im Fernsehen für den Turm. Man sieht ihn nur im Spiegel nicht.", "Count Cashula advertises the tower on TV. You just can't see him in the mirror."),
        Tr("In der ganzen Stadt hängen Plakate: „CrazyTower: Jetzt mit 40 % weniger Spuk.“", "Posters all over town: \"CrazyTower: now with 40 % less haunting.\""),
    )
}

val Simulation.campaignRunning: Boolean get() = clock.totalMinutes < endgame.campaignUntil

fun Simulation.campaignCost(): Long = shareOfIncome(Promotion.CAMPAIGN_PERCENT, Promotion.CAMPAIGN_MIN)

fun Simulation.campaignBlocked(): String? = when {
    stage < SINK_STAGE -> tr("Werbung erst ab Stufe $SINK_STAGE", "Ads only from stage $SINK_STAGE")
    campaignRunning -> tr("Die Kampagne läuft noch", "The campaign is still running")
    money < campaignCost() -> tr("Zu wenig Geld", "Not enough money")
    else -> null
}

fun Simulation.startCampaign(): PlaceCheck {
    campaignBlocked()?.let { return PlaceCheck.no(it) }
    book(-campaignCost(), MoneyKind.PROMOTION, tr("Werbekampagne", "Ad campaign"))
    endgame.campaignUntil = clock.totalMinutes + Promotion.CAMPAIGN_DAYS * GameClock.MINUTES_PER_DAY
    news.post(Promotion.CAMPAIGNS[random.nextInt(Promotion.CAMPAIGNS.size)].toString(), important = true)
    return PlaceCheck.OK
}

fun Simulation.partyCost(): Long = shareOfIncome(Promotion.PARTY_PERCENT, Promotion.PARTY_MIN)

fun Simulation.partyBlocked(): String? = when {
    stage < SINK_STAGE -> tr("Turmfest erst ab Stufe $SINK_STAGE", "Tower party only from stage $SINK_STAGE")
    activeEvents.any { it.id == "towerparty" } -> tr("Das Fest läuft schon", "The party is on already")
    clock.totalMinutes - endgame.lastParty < Promotion.PARTY_COOLDOWN -> {
        val days = ((Promotion.PARTY_COOLDOWN - (clock.totalMinutes - endgame.lastParty)) / GameClock.MINUTES_PER_DAY).toInt() + 1
        tr("Nächstes Turmfest in $days Tagen", "Next tower party in $days days")
    }
    money < partyCost() -> tr("Zu wenig Geld", "Not enough money")
    else -> null
}

/** Two days of confetti: more mood and twice the sightseers. */
fun Simulation.throwParty(): PlaceCheck {
    partyBlocked()?.let { return PlaceCheck.no(it) }
    book(-partyCost(), MoneyKind.PROMOTION, tr("Turmfest", "Tower party"))
    val now = clock.totalMinutes
    endgame.lastParty = now
    startEvent(
        ActiveEvent("towerparty", now, now + Promotion.PARTY_HOURS * 60.0),
        tr("TURMFEST! Konfettikanonen im Treppenhaus, eine Hochzeit im Aufzug, diesmal mit Absicht, und Feuerwerk vom Dach.",
            "TOWER PARTY! Confetti cannons in the stairwell, a wedding in the elevator, on purpose this time, and fireworks from the roof."),
    )
    return PlaceCheck.OK
}

val Simulation.partyRunning: Boolean get() = activeEvents.any { it.id == "towerparty" }

// =====================================================================================
// Decorations
// =====================================================================================

/** Something big and silly on the roof or the front. Pure show, but sightseers love it. */
class Decoration(val id: String, val name: Tr, val cost: Long, val line: Tr)

object Decor {
    /** Every decoration brings this share more spectators to the moon mission. */
    const val SPECTATOR_BONUS = 0.1

    val all = listOf(
        Decoration("duck", Tr("Riesen-Gummiente auf dem Dach", "Giant rubber duck on the roof"), 1_000_000,
            Tr("Auf dem Dach sitzt jetzt eine Gummiente, so groß wie ein Bus. Sie quietscht bei Wind.", "A rubber duck as big as a bus now sits on the roof. It squeaks in the wind.")),
        Decoration("neon", Tr("Leuchtschrift „CRAZY“", "Neon sign \"CRAZY\""), 3_000_000,
            Tr("Die Leuchtschrift „CRAZY“ brennt. Das Y flackert, aber das gehört so.", "The \"CRAZY\" neon sign is on. The Y flickers, but that's on purpose.")),
        Decoration("disco", Tr("Discokugel am Dach", "Disco ball on the roof"), 6_000_000,
            Tr("Eine Discokugel dreht sich über dem Turm. Die Flugzeuge tanzen ein bisschen.", "A disco ball spins above the tower. The planes dance a little.")),
        Decoration("gold", Tr("Goldene Fassade", "Golden facade"), 12_000_000,
            Tr("Die Fassade ist jetzt aus Gold. Die Nachbarn tragen Sonnenbrillen, auch drinnen.", "The facade is gold now. The neighbours wear sunglasses, indoors too.")),
        Decoration("laser", Tr("Lasershow „Turm-Saga“", "Laser show \"Tower Saga\""), 20_000_000,
            Tr("Jeden Abend erzählt eine Lasershow die Geschichte des Turms. Mit Clowns.", "Every evening a laser show tells the story of the tower. With clowns.")),
    )

    fun byId(id: String): Decoration? = all.firstOrNull { it.id == id }
}

fun Simulation.decorBlocked(item: Decoration): String? = when {
    stage < SINK_STAGE -> tr("Deko erst ab Stufe $SINK_STAGE", "Decorations only from stage $SINK_STAGE")
    item.id in endgame.decor -> tr("Hängt schon", "Up already")
    money < item.cost -> tr("Zu wenig Geld", "Not enough money")
    else -> null
}

fun Simulation.buyDecor(item: Decoration): PlaceCheck {
    decorBlocked(item)?.let { return PlaceCheck.no(it) }
    book(-item.cost, MoneyKind.EXTRAS, item.name.toString())
    endgame.decor += item.id
    news.post(item.line.toString(), important = true)
    return PlaceCheck.OK
}

// =====================================================================================
// Side entrances
// =====================================================================================

/**
 * Granny Hildegard sells the plots behind the tower, one after the other. Each one becomes a
 * side entrance from the back street, so the middle of a wide tower is no longer far from the street.
 */
object Gates {
    data class Gate(val x: Float, val cost: Long, val deal: Tr)

    val all = listOf(
        Gate(Lot.maxWidth / 2f, 2_000_000, Tr("Oma Hildegard verkauft ihren Schrebergarten. In der Mitte des Turms gibt es jetzt einen Seiteneingang.",
            "Granny Hildegard sells her allotment. There is a side entrance in the middle of the tower now.")),
        Gate(Lot.maxWidth / 4f, 6_000_000, Tr("Oma Hildegard verkauft das nächste Beet. Dafür will sie eine Hollywoodschaukel. Neuer Seiteneingang links.",
            "Granny Hildegard sells the next flower bed. She wants a swing seat in return. New side entrance on the left.")),
        Gate(Lot.maxWidth * 3 / 4f, 18_000_000, Tr("Oma Hildegard gibt das letzte Beet her, gegen Geld und einen Teleporter zu ihrem Enkel. Neuer Seiteneingang rechts.",
            "Granny Hildegard gives up her last bed, for money and a teleporter to her grandson. New side entrance on the right.")),
    )
}

/** Where people may enter from the street: both ends and the side entrances bought. */
val Simulation.entrances: List<Float> get() =
    listOf(0f, lotWidth - 0.01f) + Gates.all.take(endgame.gates).map { it.x }.filter { it < lotWidth }

fun Simulation.gateBlocked(): String? {
    val next = Gates.all.getOrNull(endgame.gates) ?: return tr("Oma Hildegard hat nichts mehr", "Granny Hildegard has nothing left")
    return when {
        stage < SINK_STAGE -> tr("Oma Hildegard verkauft erst ab Stufe $SINK_STAGE", "Granny Hildegard only sells from stage $SINK_STAGE")
        next.x >= lotWidth -> tr("Das Grundstück ist dafür noch zu schmal", "The lot is too narrow for it yet")
        money < next.cost -> tr("Zu wenig Geld", "Not enough money")
        else -> null
    }
}

fun Simulation.buyGate(): PlaceCheck {
    gateBlocked()?.let { return PlaceCheck.no(it) }
    val gate = Gates.all[endgame.gates]
    book(-gate.cost, MoneyKind.EXTRAS, tr("Seiteneingang", "Side entrance"), Loc(GROUND_FLOOR, gate.x))
    endgame.gates++
    tower.touch()
    news.post(gate.deal.toString(), Loc(GROUND_FLOOR, gate.x), important = true)
    return PlaceCheck.OK
}

// =====================================================================================
// The Laboratory of Questionable Science
// =====================================================================================

class Study(val id: String, val name: Tr, val effect: Tr)

object Research {
    const val FIRST_COST = 1_000_000L
    const val PATIENCE = 3.0

    val all = listOf(
        Study("teleport", Tr("Teleporter ohne Verwechslung", "Teleporter without mix-ups"), Tr("niemand landet mehr in der falschen Etage", "nobody lands on the wrong floor any more")),
        Study("coffee", Tr("Kopierraum kopiert jetzt auch Kaffee", "The copy room now copies coffee too"), Tr("Büroleute: doppelte Laune vom Kopierraum", "office workers: twice the mood from the copy room")),
        Study("towels", Tr("Selbstputzende Handtücher", "Self-cleaning towels"), Tr("Hotelzimmer sind doppelt so schnell sauber", "hotel rooms are clean twice as fast")),
        Study("wallpaper", Tr("Schallschluck-Tapete", "Sound-swallowing wallpaper"), Tr("Wohnheime stören die Nachbarn nicht mehr", "dorms no longer bother the neighbours")),
        Study("patience", Tr("Geduldspille (Geschmack: Wartezimmer)", "Patience pill (flavour: waiting room)"), Tr("alle warten ${PATIENCE.toInt()} Min. länger ohne Murren", "everybody waits ${PATIENCE.toInt()} min. longer without grumbling")),
    )

    fun byId(id: String): Study? = all.firstOrNull { it.id == id }
}

fun Simulation.researched(id: String): Boolean = id in endgame.research

/** Every study costs twice the one before. */
fun Simulation.researchCost(): Long = Research.FIRST_COST shl endgame.research.size

fun Simulation.researchBlocked(study: Study): String? = when {
    stage < SINK_STAGE -> tr("Labor erst ab Stufe $SINK_STAGE", "Laboratory only from stage $SINK_STAGE")
    study.id in endgame.research -> tr("Schon erforscht", "Researched already")
    money < researchCost() -> tr("Zu wenig Geld", "Not enough money")
    else -> null
}

fun Simulation.doResearch(study: Study): PlaceCheck {
    researchBlocked(study)?.let { return PlaceCheck.no(it) }
    book(-researchCost(), MoneyKind.EXTRAS, study.name.toString())
    endgame.research += study.id
    bumpLooks()
    news.post(tr("Das Labor für fragwürdige Wissenschaft meldet: ${study.name}. Nebenwirkungen: keine bekannt. Gesucht wurde auch nicht.",
        "The Laboratory of Questionable Science reports: ${study.name}. Side effects: none known. Nobody looked, though."), important = true)
    return PlaceCheck.OK
}

// =====================================================================================
// Hand-picked tenants
// =====================================================================================

object Recruiting {
    const val PERCENT = 5.0
    const val MIN = 20_000L
    private val ROLES = setOf(RoomRole.OFFICE, RoomRole.HOME, RoomRole.FOOD, RoomRole.DORM, RoomRole.SHOP)

    fun applies(room: Room): Boolean = room.type.role in ROLES

    /** The job ad in the paper. */
    fun ad(tenant: TenantKind): String = when (tenant.id) {
        "supervillain" -> tr("Superschurke gesucht, Vulkan nicht erforderlich.", "Supervillain wanted, volcano not required.")
        "clown" -> tr("Lustige Leute gesucht. Rote Nase von Vorteil.", "Funny people wanted. A red nose helps.")
        "vampire" -> tr("Nachtaktive Versicherer gesucht. Keine Spiegel vorhanden.", "Nocturnal insurers wanted. No mirrors on site.")
        else -> tr("Gesucht: ${tenant.name}. Wir haben Aufzüge!", "Wanted: ${tenant.name}. We have elevators!")
    }
}

fun Simulation.recruitCost(): Long = shareOfIncome(Recruiting.PERCENT, Recruiting.MIN)

/** Tenants the player may pick for [room]: the usual ones, and clowns for offices. */
fun Simulation.recruitable(room: Room): List<TenantKind> {
    val usual = Tenants.candidates(room.type, stars, stage)
    val clowns = if (room.type.role == RoomRole.OFFICE && !room.type.luxury) listOf(Tenants.byId("clown")) else emptyList()
    return usual + clowns
}

fun Simulation.recruitBlocked(room: Room): String? = when {
    !Recruiting.applies(room) -> tr("Hier sucht man niemanden aus", "No picking tenants here")
    stage < SINK_STAGE -> tr("Anwerben erst ab Stufe $SINK_STAGE", "Recruiting only from stage $SINK_STAGE")
    room.tenant != null -> tr("Schon vermietet", "Rented already")
    roomProblems[room.id] != null -> tr("Erst muss man hinkommen", "People have to get there first")
    room.type.role == RoomRole.OFFICE && !hasWorkersFor(room.type.capacity) -> tr("Keine Fachkräfte frei", "No staff available")
    money < recruitCost() -> tr("Zu wenig Geld", "Not enough money")
    else -> null
}

/** Puts an ad in the paper and [tenant] moves into [room] right away. */
fun Simulation.recruit(room: Room, tenant: TenantKind): PlaceCheck {
    recruitBlocked(room)?.let { return PlaceCheck.no(it) }
    if (tenant !in recruitable(room)) return PlaceCheck.no(tr("Passt hier nicht rein", "Doesn't fit in here"))
    book(-recruitCost(), MoneyKind.EXTRAS, tr("Anzeige: ${tenant.name}", "Ad: ${tenant.name}"), room.center)
    news.post(tr("Anzeige in der Zeitung: „${Recruiting.ad(tenant)}“", "Ad in the paper: \"${Recruiting.ad(tenant)}\""), room.center)
    moveIn(room, tenant)
    return PlaceCheck.OK
}
