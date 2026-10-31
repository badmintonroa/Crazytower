package crazytower.core

import crazytower.core.ecs.Goal
import crazytower.core.ecs.Person
import crazytower.core.ecs.Position
import crazytower.core.ecs.Vip
import crazytower.core.ecs.Visitor

/**
 * A very important guest. From [minStars] on, she or he drops by now and then, visits one rented
 * room and judges the tower on the way. A good impression leaves a fat tip, a bad one small change.
 * Lines may use {room}, {floor} ("5. Etage"), {onfloor} ("on floor 5"), {money} ("1.800 $") and {amount} ("1.800").
 *
 * @param likes tenant id the guest would rather visit, if the tower has one
 * @param recurring false for Frau Dr. Wichtig, who comes once by event and unlocks stage 5
 * @param fans sightseers who follow the guest into the tower (streamers bring their chat)
 */
class VipGuest(
    val id: String,
    val name: Tr,
    val look: String,
    val minStars: Int,
    val arrives: Tr,
    val generous: Tr,
    val stingy: Tr,
    val givesUp: Tr,
    val likes: String? = null,
    val recurring: Boolean = true,
    val fans: Int = 0,
)

/** How a VIP liked the tower: [score] 0..1 and, when it was not great, what bothered her most. */
data class VipVerdict(val score: Float, val complaint: String?) {
    val happy: Boolean get() = score >= Vips.HAPPY_SCORE
}

object Vips {
    val all: List<VipGuest> = listOf(
        VipGuest("wichtig", Simulation.VIP_NAME, "npc_vip", minStars = 0, recurring = false,
            arrives = Tr("VIP-Besuch! Frau Dr. Wichtig will zu {room} in die {floor}. Bitte nichts kaputt machen.", "VIP visit! Dr. Bigwig wants to see {room} {onfloor}. Please don't break anything."),
            generous = Tr("Frau Dr. Wichtig lässt {money} für die Kaffeekasse da. Sie hat noch nie Kaffee getrunken.", "Dr. Bigwig leaves {money} for the coffee fund. She has never had a coffee in her life."),
            stingy = Tr("Frau Dr. Wichtig lässt {money} da und den Hinweis, dass man das besser machen kann.", "Dr. Bigwig leaves {money} and a note that this could be done better."),
            givesUp = Tr("Frau Dr. Wichtig hat den Weg nicht gefunden und reist beleidigt ab. Vielleicht ein Aufzug mehr?", "Dr. Bigwig could not find the way and leaves in a huff. Maybe one more elevator?")),
        VipGuest("goldzahn", Tr("Baron von Goldzahn", "Baron von Goldtooth"), "npc_vip", minStars = 3,
            arrives = Tr("Baron von Goldzahn, Erfinder des vergoldeten Zahnstochers, schaut bei {room} vorbei.", "Baron von Goldtooth, inventor of the gold-plated toothpick, drops in on {room}."),
            generous = Tr("Baron von Goldzahn ist entzückt und lässt {money} Trinkgeld da. In Scheinen. Gebügelt.", "Baron von Goldtooth is delighted and tips {money}. In notes. Ironed."),
            stingy = Tr("Baron von Goldzahn rümpft die Nase und lässt {money} da. Und einen benutzten Zahnstocher.", "Baron von Goldtooth turns up his nose and leaves {money}. And a used toothpick."),
            givesUp = Tr("Baron von Goldzahn hat keinen Weg gefunden und lässt sich mit der Sänfte nach Hause tragen.", "Baron von Goldtooth could not find the way and has himself carried home in his sedan chair.")),
        VipGuest("troet", Tr("Zirkusdirektor Tröt-Maximus", "Ringmaster Honk-Maximus"), "npc_clown_boss", minStars = 3, likes = "clown",
            arrives = Tr("Zirkusdirektor Tröt-Maximus sucht neue Talente und will zu {room}. Er hupt zur Begrüßung.", "Ringmaster Honk-Maximus is scouting new talent and wants to see {room}. He honks hello."),
            generous = Tr("Tröt-Maximus ist begeistert! {money} regnen aus seinem Zylinder. Das Kaninchen behält er.", "Honk-Maximus is thrilled! {money} rains from his top hat. He keeps the rabbit."),
            stingy = Tr("Tröt-Maximus hupt enttäuscht und lässt {money} da. In Konfetti umgerechnet ist das viel.", "Honk-Maximus honks in disappointment and leaves {money}. In confetti, that is a lot."),
            givesUp = Tr("Tröt-Maximus hat sich verlaufen und ist mit dem Einrad wieder abgefahren.", "Honk-Maximus got lost and rode off on his unicycle.")),
        VipGuest("zahlula", Tr("Graf Zahlula", "Count Cashula"), "npc_vampire_count", minStars = 3, likes = "vampire",
            arrives = Tr("Graf Zahlula besucht {room} in der {floor}. Er wurde ausdrücklich hereingebeten.", "Count Cashula visits {room} {onfloor}. He was expressly invited in."),
            generous = Tr("Graf Zahlula zählt {money} ab. Eins! Zwei! Drei! Er lässt sie alle da. Ah, ah, ah!", "Count Cashula counts out {money}. One! Two! Three! He leaves it all. Ah, ah, ah!"),
            stingy = Tr("Graf Zahlula zählt nur bis {amount}. Dann ist er beleidigt und lässt genau das da.", "Count Cashula only counts to {amount}. Then he sulks and leaves exactly that."),
            givesUp = Tr("Graf Zahlula hat keinen Weg gefunden. Er fliegt als Fledermaus beleidigt davon.", "Count Cashula could not find the way. He flies off in a huff as a bat.")),
        VipGuest("dividende", Tr("Konzernchefin Dr. Dividende", "CEO Dr. Dividend"), "npc_office_boss", minStars = 4,
            arrives = Tr("Dr. Dividende prüft {room} auf Synergien. Sie hat drei Assistenten und keinen Plan.", "Dr. Dividend checks {room} for synergies. She has three assistants and no plan."),
            generous = Tr("Dr. Dividende sieht Synergien! Sie investiert {money} in den Turm, einfach so.", "Dr. Dividend sees synergies! She invests {money} in the tower, just like that."),
            stingy = Tr("Dr. Dividende sieht keine Synergien und hinterlässt {money} und eine Rechnung für ihre Zeit.", "Dr. Dividend sees no synergies and leaves {money} and an invoice for her time."),
            givesUp = Tr("Dr. Dividende kommt nicht weiter und lässt das Projekt einstampfen. Den Turm gibt es aber noch.", "Dr. Dividend gets stuck and has the project scrapped. The tower is still there, though.")),
        VipGuest("haemisch", Tr("Dr. Hämisch", "Dr. Smirk"), "npc_supervillain", minStars = 4, likes = "supervillain",
            arrives = Tr("Dr. Hämisch, pensionierter Superschurke, will zu {room}. Er streichelt eine Katze, die nicht da ist.", "Dr. Smirk, retired supervillain, wants to see {room}. He strokes a cat that isn't there."),
            generous = Tr("Dr. Hämisch ist zufrieden. Sehr zufrieden. Muhaha! Er lässt {money} da und keine Falle.", "Dr. Smirk is pleased. Very pleased. Mwahaha! He leaves {money} and no trap."),
            stingy = Tr("Dr. Hämisch ist enttäuscht, Mister Turm. Er lässt nur {money} da.", "Dr. Smirk is disappointed, Mister Tower. He leaves only {money}."),
            givesUp = Tr("Dr. Hämisch hat keinen Weg gefunden. Er schwört Rache, dann geht er essen.", "Dr. Smirk could not find the way. He swears revenge, then goes for lunch.")),
        VipGuest("morgen", Tr("Prof. Morgen", "Prof. Tomorrow"), "npc_timetraveller", minStars = 4, likes = "timetravel",
            arrives = Tr("Prof. Morgen aus dem Jahr 2087 will zu {room}. Er war schon da, sagt er, morgen.", "Prof. Tomorrow from the year 2087 wants to see {room}. He has been here before, he says. Tomorrow."),
            generous = Tr("Prof. Morgen hinterlässt {money}. Im Jahr 2087 ist das Kleingeld, sagt er.", "Prof. Tomorrow leaves {money}. In 2087 that is small change, he says."),
            stingy = Tr("Prof. Morgen lässt {money} da. In der Zukunft hat ihm der Turm besser gefallen.", "Prof. Tomorrow leaves {money}. He liked the tower better in the future."),
            givesUp = Tr("Prof. Morgen hat den Weg nicht gefunden und reist zurück in die Zukunft. Gestern.", "Prof. Tomorrow could not find the way and travels back to the future. Yesterday.")),
        // Streamers: they go live on the way and bring a few fans along
        VipGuest("bernd", Tr("xXBerndZocktXx", "xXBerndZocktXx"), "npc_student_gamer", minStars = 3, fans = 4,
            arrives = Tr("xXBerndZocktXx geht live: \"Leute, heute besuchen wir {room}! Abo dalassen!\" Seine Fans stehen schon in der Lobby.", "xXBerndZocktXx goes live: \"Guys, today we're visiting {room}! Smash that subscribe!\" His fans are already in the lobby."),
            generous = Tr("Der Chat von xXBerndZocktXx spammt Herzchen: {money} an Spenden landen in der Turmkasse. Pog!", "The chat of xXBerndZocktXx spams hearts: {money} in donations land in the tower's till. Pog!"),
            stingy = Tr("xXBerndZocktXx sagt \"Content war mid\" und lässt {money} in Bits da.", "xXBerndZocktXx says \"content was mid\" and leaves {money} in bits."),
            givesUp = Tr("xXBerndZocktXx hat den Weg nicht gefunden und macht daraus ein acht Stunden langes Rage-Video.", "xXBerndZocktXx could not find the way and turns it into an eight-hour rage video.")),
        VipGuest("annika", Tr("ASMR-Annika", "ASMR Annika"), "npc_student_artist", minStars = 3, fans = 2,
            arrives = Tr("ASMR-Annika flüstert ins Mikro, während sie zu {room} schleicht. Ihre Fans sind sehr leise.", "ASMR Annika whispers into her mic as she sneaks to {room}. Her fans are very quiet."),
            generous = Tr("ASMR-Annika flüstert \"danke\" und lässt {money} da. Ganz leise.", "ASMR Annika whispers \"thank you\" and leaves {money}. Very quietly."),
            stingy = Tr("ASMR-Annika flüstert \"viel zu laut hier\" und lässt {money} da.", "ASMR Annika whispers \"way too loud in here\" and leaves {money}."),
            givesUp = Tr("ASMR-Annika hat keinen Weg gefunden und ist geflüstert gegangen.", "ASMR Annika could not find the way and whispered off.")),
        VipGuest("vanessa", Tr("Vlog-Vanessa", "Vlog Vanessa"), "npc_tourist", minStars = 3, fans = 3,
            arrives = Tr("Vlog-Vanessa dreht eine Room-Tour bei {room}. Mit Ringlicht. Und Fans.", "Vlog Vanessa films a room tour at {room}. With a ring light. And fans."),
            generous = Tr("Vlog-Vanessa: \"Absolut aesthetic!\" {money} Kooperationsgeld für den Turm.", "Vlog Vanessa: \"Totally aesthetic!\" {money} in sponsorship money for the tower."),
            stingy = Tr("Vlog-Vanessa: \"Leider gar nicht aesthetic.\" Sie lässt {money} und einen Rabattcode da.", "Vlog Vanessa: \"Sadly not aesthetic at all.\" She leaves {money} and a discount code."),
            givesUp = Tr("Vlog-Vanessa hat sich verlaufen. Das Video heißt jetzt \"GEFANGEN IM TURM (gone wrong)\".", "Vlog Vanessa got lost. The video is now called \"TRAPPED IN THE TOWER (gone wrong)\".")),
        VipGuest("soeren", Tr("Speedrun-Sören", "Speedrun Sören"), "npc_student_hoodie", minStars = 4, fans = 3,
            arrives = Tr("Speedrun-Sören will {room} in Weltrekordzeit erreichen. Der Timer läuft, der Chat zählt mit.", "Speedrun Sören wants to reach {room} in world record time. The timer is running, the chat is counting."),
            generous = Tr("Neuer Weltrekord! Speedrun-Sören lässt {money} da und einen Ausdruck seiner Splits.", "New world record! Speedrun Sören leaves {money} and a printout of his splits."),
            stingy = Tr("Kein persönlicher Rekord. Speedrun-Sören resettet und lässt {money} da.", "No personal best. Speedrun Sören resets and leaves {money}."),
            givesUp = Tr("Speedrun-Sören hat keinen Weg gefunden. Run ungültig, Reset.", "Speedrun Sören could not find the way. Run invalid, reset.")),
        VipGuest("mehmet", Tr("Mukbang-Mehmet", "Mukbang Mehmet"), "npc_student_party", minStars = 4, fans = 3,
            arrives = Tr("Mukbang-Mehmet isst sich live durch den Turm und will zu {room}. Er hat drei Teller dabei.", "Mukbang Mehmet is eating his way through the tower live and wants to see {room}. He brought three plates."),
            generous = Tr("Mukbang-Mehmet ist satt und glücklich. Sein Chat spendet {money}.", "Mukbang Mehmet is full and happy. His chat donates {money}."),
            stingy = Tr("Mukbang-Mehmet: \"Geschmacklich eine Drei von Zehn.\" Er lässt {money} da.", "Mukbang Mehmet: \"Taste-wise a three out of ten.\" He leaves {money}."),
            givesUp = Tr("Mukbang-Mehmet hat den Weg nicht gefunden und isst stattdessen draußen einen Döner. Live.", "Mukbang Mehmet could not find the way and eats a kebab outside instead. Live.")),
        VipGuest("gisela", Tr("Oma Gisela Gaming", "Granny Gisela Gaming"), "npc_retiree", minStars = 5, fans = 5,
            arrives = Tr("Oma Gisela Gaming, die älteste Streamerin Deutschlands, besucht {room}. Ihr Chat schreibt nur in Großbuchstaben.", "Granny Gisela Gaming, Germany's oldest streamer, visits {room}. Her chat only writes in capitals."),
            generous = Tr("Oma Gisela verteilt {money} und Kekse. Der Chat dreht durch.", "Granny Gisela hands out {money} and biscuits. The chat goes wild."),
            stingy = Tr("Oma Gisela lässt {money} da. \"Früher waren Türme besser.\"", "Granny Gisela leaves {money}. \"Towers were better in my day.\""),
            givesUp = Tr("Oma Gisela Gaming findet keinen Weg und streamt jetzt vom Parkplatz.", "Granny Gisela Gaming can't find the way and now streams from the car park.")),
        VipGuest("zorg", Tr("Botschafter Zorg", "Ambassador Zorg"), "npc_alien", minStars = 5, likes = "alien",
            arrives = Tr("Botschafter Zorg von Gliese 581 inspiziert {room}. Er will den Chef des Planeten sprechen.", "Ambassador Zorg of Gliese 581 inspects {room}. He wants to speak to the planet's manager."),
            generous = Tr("Zorg ist erfreut. Er lässt {money} in Gold da und einen Gutschein für eine Entführung.", "Zorg is pleased. He leaves {money} in gold and a voucher for an abduction."),
            stingy = Tr("Zorg ist nicht beeindruckt. Er lässt {money} da und schreibt eine Ein-Tentakel-Bewertung.", "Zorg is not impressed. He leaves {money} and writes a one-tentacle review."),
            givesUp = Tr("Zorg findet keinen Weg und beamt sich zurück. Die Erde bekommt eine schlechte Bewertung.", "Zorg can't find the way and beams himself back. Earth gets a bad review.")),
    )

    fun byId(id: String): VipGuest = all.firstOrNull { it.id == id } ?: all.first()

    /** Guests who may drop by at [stars]. */
    fun available(stars: Int): List<VipGuest> = all.filter { it.recurring && stars >= it.minStars }

    /**
     * What the VIP thinks of the tower: the mood in the tower, the mood in the room she visited,
     * how long the way up took, and how tidy the hotel rooms are. Plus her mood of the day.
     */
    fun judge(sim: Simulation, room: Room?, minutes: Double): VipVerdict {
        val tower = sim.satisfaction / 100f
        val roomMood = ((room?.let { sim.moodOf(it) } ?: 70f) / 100f).coerceIn(0f, 1f)
        val travel = (1f - ((minutes - QUICK_MINUTES) / (SLOW_MINUTES - QUICK_MINUTES)).toFloat()).coerceIn(0f, 1f)
        val hotels = sim.tower.allRooms.count { it.type.role == RoomRole.HOTEL }
        val tidy = if (hotels == 0) 1f else 1f - sim.dirtyRooms.count { it.type.role == RoomRole.HOTEL }.toFloat() / hotels
        val whim = (sim.random.nextFloat() * 2f - 1f) * WHIM
        val score = (0.3f * tower + 0.2f * roomMood + 0.4f * travel + 0.1f * tidy + whim).coerceIn(0f, 1f)
        // the worst part, weighted like the score
        val complaint = listOf(
            0.3f * (1f - tower) to tr("Die Laune im Turm ist im Keller.", "The mood in the tower is rock bottom."),
            0.2f * (1f - roomMood) to tr("Bei ${room?.tenant?.name ?: "den Mietern"} herrscht dicke Luft.", "There is tension in the air at ${room?.tenant?.name ?: "the tenants"}."),
            0.4f * (1f - travel) to tr("Der Weg nach oben hat ewig gedauert.", "The way up took forever."),
            0.1f * (1f - tidy) to tr("Überall ungeputzte Hotelzimmer.", "Dirty hotel rooms everywhere."),
        ).maxBy { it.first }.takeIf { it.first > 0.05f }?.second
        return VipVerdict(score, complaint)
    }

    /**
     * A happy VIP leaves thousands per star, or a share of what the tower earned [yesterday] if
     * that is more, so she still matters in a big tower. An unhappy one leaves a little change.
     */
    fun tip(stars: Int, verdict: VipVerdict, random: kotlin.random.Random, yesterday: Long = 0): Long {
        val s = stars.coerceAtLeast(1)
        return if (verdict.happy) {
            val base = maxOf(TIP_PER_STAR * s, (yesterday * TIP_SHARE).toLong())
            (base * (0.5 + verdict.score)).toLong() / 100 * 100
        } else {
            1L + random.nextLong(STINGY_PER_STAR * s)
        }
    }

    /** Rolls once per game hour whether a VIP drops by. */
    fun roll(sim: Simulation) {
        if (sim.stars < MIN_STARS || sim.vipOnTheWay) return
        if (sim.clock.hour !in FIRST_HOUR until LAST_HOUR) return
        if (sim.clock.totalMinutes - sim.lastVipVisit < COOLDOWN_MINUTES) return
        val ads = if (sim.campaignRunning) 2f else 1f
        if (sim.random.nextFloat() >= CHANCE_PER_STAR * (sim.stars - MIN_STARS + 1) * ads) return
        // the landing permit: Zorg is keen to come while the mission waits for him
        val zorg = if (sim.taskOpen(Mission.ZORG) && sim.random.nextFloat() < 0.4f) byId("zorg") else null
        val guest = zorg ?: available(sim.stars).randomOrNull(sim.random) ?: return
        sim.spawnVip(guest)
    }

    fun line(text: Tr, room: Room?, amount: Long = 0): String = text.toString()
        .replace("{room}", room?.tenant?.name?.toString() ?: tr("den Mietern", "the tenants"))
        .replace("{floor}", room?.let { Simulation.floorLabel(it.floor) } ?: tr("oberste Etage", "top floor"))
        .replace("{onfloor}", room?.let { Simulation.onFloor(it.floor) } ?: "on the top floor")
        .replace("{money}", formatMoney(amount))
        .replace("{amount}", formatNumber(amount))

    /** Recurring VIPs come from three stars on. */
    const val MIN_STARS = 3
    /** Chance per game hour at three stars; it grows by the same amount with every star. */
    const val CHANCE_PER_STAR = 0.03f
    /** VIPs only come in office hours, so they are gone before the lobby closes. */
    const val FIRST_HOUR = 9
    const val LAST_HOUR = 19
    /** At least this long between two visits. */
    const val COOLDOWN_MINUTES = 12 * 60.0
    /** From this score on a VIP is happy and generous. */
    const val HAPPY_SCORE = 0.6f
    const val TIP_PER_STAR = 6_000L
    /** Share of the day's income a happy VIP leaves in a big tower. */
    const val TIP_SHARE = 0.15
    const val STINGY_PER_STAR = 30L
    const val QUICK_MINUTES = 15.0
    const val SLOW_MINUTES = 90.0
    const val WHIM = 0.1f
}

/** Sends [guest] from the street to one rented room, her favourite tenant if the tower has one. */
fun Simulation.spawnVip(guest: VipGuest, target: Room? = null): Boolean {
    val rented = tower.allRooms.filter { it.tenant != null }
    val room = target
        ?: rented.filter { it.tenant?.id == guest.likes }.randomOrNull(random)
        ?: (if (guest.recurring) rented.randomOrNull(random) else rented.maxByOrNull { it.floor })
        ?: return false
    val fromLeft = random.nextBoolean()
    world.entity {
        it += Position(GROUND_FLOOR, if (fromLeft) 0f else lotWidth - 0.01f, facingLeft = !fromLeft)
        it += Person(guest.name.toString(), guest.look)
        it += Visitor(stopsLeft = 0)
        it += Vip(since = clock.totalMinutes, guestId = guest.id, roomId = room.id)
        it += Goal(spotIn(room))
    }
    repeat(guest.fans) { spawnVisitor() }
    news.post(Vips.line(guest.arrives, room), room.center, important = true)
    return true
}
