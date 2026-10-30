package crazytower.core

import crazytower.core.ecs.Goal
import crazytower.core.ecs.Person
import crazytower.core.ecs.Position
import crazytower.core.ecs.Vip
import crazytower.core.ecs.Visitor

/**
 * A very important guest. From [minStars] on, she or he drops by now and then, visits one rented
 * room and judges the tower on the way. A good impression leaves a fat tip, a bad one small change.
 * Lines may use {room}, {floor} and {amount}.
 *
 * @param likes tenant id the guest would rather visit, if the tower has one
 * @param recurring false for Frau Dr. Wichtig, who comes once by event and unlocks stage 5
 */
class VipGuest(
    val id: String,
    val name: String,
    val look: String,
    val minStars: Int,
    val arrives: String,
    val generous: String,
    val stingy: String,
    val givesUp: String,
    val likes: String? = null,
    val recurring: Boolean = true,
)

/** How a VIP liked the tower: [score] 0..1 and, when it was not great, what bothered her most. */
data class VipVerdict(val score: Float, val complaint: String?) {
    val happy: Boolean get() = score >= Vips.HAPPY_SCORE
}

object Vips {
    val all: List<VipGuest> = listOf(
        VipGuest("wichtig", "Frau Dr. Wichtig", "npc_vip", minStars = 0, recurring = false,
            arrives = "VIP-Besuch! Frau Dr. Wichtig will zu {room} in die {floor}. Bitte nichts kaputt machen.",
            generous = "Frau Dr. Wichtig lässt {amount} \$ für die Kaffeekasse da. Sie hat noch nie Kaffee getrunken.",
            stingy = "Frau Dr. Wichtig lässt {amount} \$ da und den Hinweis, dass man das besser machen kann.",
            givesUp = "Frau Dr. Wichtig hat den Weg nicht gefunden und reist beleidigt ab. Vielleicht ein Aufzug mehr?"),
        VipGuest("goldzahn", "Baron von Goldzahn", "npc_vip", minStars = 3,
            arrives = "Baron von Goldzahn, Erfinder des vergoldeten Zahnstochers, schaut bei {room} vorbei.",
            generous = "Baron von Goldzahn ist entzückt und lässt {amount} \$ Trinkgeld da. In Scheinen. Gebügelt.",
            stingy = "Baron von Goldzahn rümpft die Nase und lässt {amount} \$ da. Und einen benutzten Zahnstocher.",
            givesUp = "Baron von Goldzahn hat keinen Weg gefunden und lässt sich mit der Sänfte nach Hause tragen."),
        VipGuest("troet", "Zirkusdirektor Tröt-Maximus", "npc_clown_boss", minStars = 3, likes = "clown",
            arrives = "Zirkusdirektor Tröt-Maximus sucht neue Talente und will zu {room}. Er hupt zur Begrüßung.",
            generous = "Tröt-Maximus ist begeistert! {amount} \$ regnen aus seinem Zylinder. Das Kaninchen behält er.",
            stingy = "Tröt-Maximus hupt enttäuscht und lässt {amount} \$ da. In Konfetti umgerechnet ist das viel.",
            givesUp = "Tröt-Maximus hat sich verlaufen und ist mit dem Einrad wieder abgefahren."),
        VipGuest("zahlula", "Graf Zahlula", "npc_vampire_count", minStars = 3, likes = "vampire",
            arrives = "Graf Zahlula besucht {room} in der {floor}. Er wurde ausdrücklich hereingebeten.",
            generous = "Graf Zahlula zählt {amount} \$ ab. Eins! Zwei! Drei! Er lässt sie alle da. Ah, ah, ah!",
            stingy = "Graf Zahlula zählt nur bis {amount}. Dann ist er beleidigt und lässt genau das da.",
            givesUp = "Graf Zahlula hat keinen Weg gefunden. Er fliegt als Fledermaus beleidigt davon."),
        VipGuest("dividende", "Konzernchefin Dr. Dividende", "npc_office_boss", minStars = 4,
            arrives = "Dr. Dividende prüft {room} auf Synergien. Sie hat drei Assistenten und keinen Plan.",
            generous = "Dr. Dividende sieht Synergien! Sie investiert {amount} \$ in den Turm, einfach so.",
            stingy = "Dr. Dividende sieht keine Synergien und hinterlässt {amount} \$ und eine Rechnung für ihre Zeit.",
            givesUp = "Dr. Dividende kommt nicht weiter und lässt das Projekt einstampfen. Den Turm gibt es aber noch."),
        VipGuest("haemisch", "Dr. Hämisch", "npc_supervillain", minStars = 4, likes = "supervillain",
            arrives = "Dr. Hämisch, pensionierter Superschurke, will zu {room}. Er streichelt eine Katze, die nicht da ist.",
            generous = "Dr. Hämisch ist zufrieden. Sehr zufrieden. Muhaha! Er lässt {amount} \$ da und keine Falle.",
            stingy = "Dr. Hämisch ist enttäuscht, Mister Turm. Er lässt nur {amount} \$ da.",
            givesUp = "Dr. Hämisch hat keinen Weg gefunden. Er schwört Rache, dann geht er essen."),
        VipGuest("morgen", "Prof. Morgen", "npc_timetraveller", minStars = 4, likes = "timetravel",
            arrives = "Prof. Morgen aus dem Jahr 2087 will zu {room}. Er war schon da, sagt er, morgen.",
            generous = "Prof. Morgen hinterlässt {amount} \$. Im Jahr 2087 ist das Kleingeld, sagt er.",
            stingy = "Prof. Morgen lässt {amount} \$ da. In der Zukunft hat ihm der Turm besser gefallen.",
            givesUp = "Prof. Morgen hat den Weg nicht gefunden und reist zurück in die Zukunft. Gestern."),
        VipGuest("zorg", "Botschafter Zorg", "npc_alien", minStars = 5, likes = "alien",
            arrives = "Botschafter Zorg von Gliese 581 inspiziert {room}. Er will den Chef des Planeten sprechen.",
            generous = "Zorg ist erfreut. Er lässt {amount} \$ in Gold da und einen Gutschein für eine Entführung.",
            stingy = "Zorg ist nicht beeindruckt. Er lässt {amount} \$ da und schreibt eine Ein-Tentakel-Bewertung.",
            givesUp = "Zorg findet keinen Weg und beamt sich zurück. Die Erde bekommt eine schlechte Bewertung."),
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
        val tidy = if (hotels == 0) 1f else 1f - sim.dirtyRooms.size.toFloat() / hotels
        val whim = (sim.random.nextFloat() * 2f - 1f) * WHIM
        val score = (0.4f * tower + 0.3f * roomMood + 0.2f * travel + 0.1f * tidy + whim).coerceIn(0f, 1f)
        // the worst part, weighted like the score
        val complaint = listOf(
            0.4f * (1f - tower) to "Die Laune im Turm ist im Keller.",
            0.3f * (1f - roomMood) to "Bei ${room?.tenant?.name ?: "den Mietern"} herrscht dicke Luft.",
            0.2f * (1f - travel) to "Der Weg nach oben hat ewig gedauert.",
            0.1f * (1f - tidy) to "Überall ungeputzte Hotelzimmer.",
        ).maxBy { it.first }.takeIf { it.first > 0.05f }?.second
        return VipVerdict(score, complaint)
    }

    /** A happy VIP leaves thousands per star, an unhappy one a little change. */
    fun tip(stars: Int, verdict: VipVerdict, random: kotlin.random.Random): Long {
        val s = stars.coerceAtLeast(1)
        return if (verdict.happy) {
            (TIP_PER_STAR * s * (0.5 + verdict.score)).toLong() / 100 * 100
        } else {
            1L + random.nextLong(STINGY_PER_STAR * s)
        }
    }

    /** Rolls once per game hour whether a VIP drops by. */
    fun roll(sim: Simulation) {
        if (sim.stars < MIN_STARS || sim.vipOnTheWay) return
        if (sim.clock.hour !in FIRST_HOUR until LAST_HOUR) return
        if (sim.clock.totalMinutes - sim.lastVipVisit < COOLDOWN_MINUTES) return
        if (sim.random.nextFloat() >= CHANCE_PER_STAR * (sim.stars - MIN_STARS + 1)) return
        val guest = available(sim.stars).randomOrNull(sim.random) ?: return
        sim.spawnVip(guest)
    }

    fun line(text: String, room: Room?, amount: Long = 0): String = text
        .replace("{room}", room?.tenant?.name ?: "den Mietern")
        .replace("{floor}", room?.let { Simulation.floorLabel(it.floor) } ?: "oberste Etage")
        .replace("{amount}", "%,d".format(amount).replace(',', '.'))

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
    const val STINGY_PER_STAR = 30L
    const val QUICK_MINUTES = 15.0
    const val SLOW_MINUTES = 75.0
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
        it += Person(guest.name, guest.look)
        it += Visitor(stopsLeft = 0)
        it += Vip(since = clock.totalMinutes, guestId = guest.id, roomId = room.id)
        it += Goal(spotIn(room))
    }
    news.post(Vips.line(guest.arrives, room), room.center, important = true)
    return true
}
