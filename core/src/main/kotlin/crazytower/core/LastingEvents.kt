package crazytower.core

import crazytower.core.ecs.Away
import crazytower.core.ecs.Goal
import crazytower.core.ecs.Idle
import crazytower.core.ecs.Member
import crazytower.core.ecs.Riding
import crazytower.core.ecs.Route
import crazytower.core.ecs.Schedule
import crazytower.core.ecs.Stuck
import crazytower.core.ecs.Visitor
import crazytower.core.ecs.drop
import crazytower.core.transport.Car
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * An event that lasts a while: a wedding in the elevator, a sock avalanche, a giant cat on the
 * roof. It is stored in the save game. Most of them have a remedy the player can pay for in the
 * inspector; some end by themselves when the player builds the right room.
 */
@Serializable
data class ActiveEvent(
    val id: String,
    val started: Double,
    val ends: Double,
    val roomId: Int? = null,
    val floor: Int? = null,
    /** Where on [floor] the event is shown, for events without a room. */
    val x: Float? = null,
    /** Elevator events: the column of the shaft. Shafts get new ids when a game is loaded. */
    val carX: Int? = null,
    /** Free for the event, e.g. the tenant who comes back after the alien exchange. */
    val note: String? = null,
)

/** What the player can do about an event, at a price. */
class Remedy(
    val label: Tr,
    val cost: Long,
    /** What the news says afterwards. */
    val done: Tr,
    /** Why the remedy can't be used right now, or null. */
    val blocked: Simulation.(ActiveEvent) -> String? = { null },
    val apply: Simulation.(ActiveEvent) -> Unit = {},
)

class LastingKind(
    val id: String,
    val title: Tr,
    /** What is going on and how it ends, for the inspector. */
    val hint: Tr,
    val remedy: Remedy? = null,
    /** Posted when the event ran its full time. */
    val over: Tr? = null,
    /** Posted when [solved] ended it early. */
    val solvedText: Tr? = null,
    /** Rooms on the event's floor pay no rent while it lasts. */
    val blocksRent: Boolean = false,
    /** Nobody moves into the event's room while it lasts. */
    val blocksMoveIn: Boolean = false,
    /** Everybody leaves the tower and stays out. */
    val evacuates: Boolean = false,
    val noVisitors: Boolean = false,
    /** Twice the sightseers, and the restaurants earn twice as much. */
    val visitorBoost: Boolean = false,
    /** Puts the event's effects on the tower, when it starts and after loading. */
    val resume: Simulation.(ActiveEvent) -> Unit = {},
    val onHour: Simulation.(ActiveEvent) -> Unit = {},
    /** True when the player fixed it without paying, e.g. by building a snack bar. */
    val solved: Simulation.(ActiveEvent) -> Boolean = { false },
    /** [expired]: the event ran its full time, no remedy and nothing solved it. */
    val onEnd: Simulation.(event: ActiveEvent, expired: Boolean) -> Unit = { _, _ -> },
)

object Lasting {
    /** No more than this many events run at the same time, the tower has enough to do. */
    const val MAX_ACTIVE = 2

    const val GARLIC_COST = 1_500L
    const val MIME_BAN_COST = 2_000L
    const val SOCK_DOG_COST = 800L
    const val BLACKMAIL = 10_000L
    const val FALSE_ALARM_COST = 500L
    const val EXTERMINATOR_COST = 1_500L
    const val TUNA_COST = 300L
    const val EXCHANGE_FEE = 5_000L
    const val PHOTO_FEE = 10_000L
    const val MOON_TIPS = 15_000L

    /** The open day can be held once a week. */
    const val OPEN_DAY_COOLDOWN = 7.0 * GameClock.MINUTES_PER_DAY
    const val OPEN_DAY_STAGE = 3
    const val OPEN_DAY_ENDS = 22

    val all: List<LastingKind> = listOf(
        LastingKind(
            "wedding", Tr("Hochzeit im Aufzug", "Wedding in the elevator"),
            Tr("Der Aufzug fährt erst nach dem Brautstrauß wieder.", "The elevator won't move until the bouquet has been thrown."),
            remedy = Remedy(
                Tr("Brautstrauß werfen", "Throw the bouquet"), 0,
                Tr("Der Brautstrauß fliegt, der Hausmeister fängt ihn. Der Aufzug fährt wieder.", "The bouquet flies, the caretaker catches it. The elevator runs again."),
            ),
            resume = { ev -> carOf(ev)?.let { it.blockedUntil = maxOf(it.blockedUntil, ev.ends) } },
            onEnd = { ev, _ -> carOf(ev)?.let { it.blockedUntil = minOf(it.blockedUntil, clock.totalMinutes) } },
        ),
        LastingKind(
            "karaoke", Tr("Aufzug-Karaoke", "Elevator karaoke"),
            Tr("Der Aufzug fährt dreimal langsamer. Wer drin ist, singt glücklich mit. Wer wartet, nicht.", "The elevator runs three times slower. The riders sing along happily. The people waiting don't."),
            remedy = Remedy(
                Tr("Mikro wegnehmen", "Take the microphone"), 0,
                Tr("Das Mikro ist weg. Der Aufzug fährt wieder normal, der ganze Turm ist ein bisschen traurig.", "The microphone is gone. The elevator runs normally again, the whole tower is a bit sad."),
                apply = { changeMood(-5f) { true } },
            ),
            over = Tr("Das Aufzug-Karaoke ist vorbei. Zugabe-Rufe aus dem Schacht.", "Elevator karaoke is over. Calls for an encore from the shaft."),
            resume = { ev -> carOf(ev)?.let { it.slowUntil = maxOf(it.slowUntil, ev.ends) } },
            onHour = { ev ->
                val car = carOf(ev)
                if (car != null) with(world) {
                    for (e in car.riders) e.getOrNull(Schedule)?.let { it.mood = (it.mood + 3f).coerceAtMost(100f) }
                    for (e in car.waiting.values.flatten()) e.getOrNull(Schedule)?.let { it.mood = (it.mood - 3f).coerceAtLeast(0f) }
                }
            },
            onEnd = { ev, _ -> carOf(ev)?.let { it.slowUntil = minOf(it.slowUntil, clock.totalMinutes) } },
        ),
        LastingKind(
            "blooddrive", Tr("Vampir-Blutspende", "Vampire blood drive"),
            Tr("Die Laune auf der Etage sinkt jede Stunde. Ein Imbiss auf dieser Etage oder Knoblauchbrot beendet die Aktion.",
                "The mood on this floor drops every hour. A snack bar on this floor or garlic bread ends the drive."),
            remedy = Remedy(
                Tr("Knoblauchbrot liefern", "Deliver garlic bread"), GARLIC_COST,
                Tr("Knoblauchbrot für die ganze Etage! Die Blutspende ist abgesagt.", "Garlic bread for the whole floor! The blood drive is cancelled."),
                blocked = { if (tower.allRooms.any { it.type.role == RoomRole.FOOD && it.tenant != null }) null else tr("Erst einen Imbiss im Turm eröffnen", "Open a snack bar in the tower first") },
            ),
            over = Tr("Die Blutspende ist vorbei. Die Vampire bedanken sich mit Keksen. Die Kekse sind rot.", "The blood drive is over. The vampires say thanks with biscuits. The biscuits are red."),
            solvedText = Tr("Der Imbiss auf der Etage verteilt Knoblauchbrot. Die Vampire ziehen sich beleidigt zurück.", "The snack bar on the floor hands out garlic bread. The vampires retreat in a huff."),
            onHour = { ev -> changeMood(-4f) { it.floor == ev.floor && it.roomId != ev.roomId } },
            solved = { ev -> tower.allRooms.any { it.type.role == RoomRole.FOOD && it.floor == ev.floor } },
        ),
        LastingKind(
            "clownriot", Tr("Clown-Aufstand", "Clown uprising"),
            Tr("Jede Nacht kann ein weiteres Büro zur Clownsagentur werden. Nur ein Pantomime-Verbot hilft.",
                "Every night another office may turn into a clown agency. Only a ban on mime helps."),
            remedy = Remedy(
                Tr("Pantomime verbieten", "Ban mime"), MIME_BAN_COST,
                Tr("Pantomime ist ab sofort verboten. Die Clowns protestieren stumm. Genau das war das Problem.", "Mime is banned from now on. The clowns protest silently. Which was the problem."),
            ),
            over = Tr("Der Clown-Aufstand ist vorbei. Die Hupen sind müde.", "The clown uprising is over. The horns are tired."),
            onHour = { ev ->
                val clowns = tower.room(ev.roomId ?: -1)
                if (clowns != null && clock.hour == 0 && random.nextFloat() < CLOWN_SPREAD) {
                    val victim = rentedRooms(RoomRole.OFFICE).filter { it.tenant?.id != "clown" && !it.type.luxury }
                        .minByOrNull { abs(it.floor - clowns.floor) * 100 + abs(it.centerX - clowns.centerX) }
                    if (victim != null) {
                        val old = victim.tenant!!.name
                        moveIn(victim, Tenants.byId("clown"), announce = false)
                        news.post(tr("Der Aufstand breitet sich aus: $old trägt jetzt rote Nasen.", "The uprising spreads: $old now wears red noses."), victim.center, important = true)
                    }
                }
            },
        ),
        LastingKind(
            "socks", Tr("Socken-Lawine", "Sock avalanche"),
            Tr("Einzelne Socken bis zur Decke. Auf dieser Etage zahlt niemand Miete und die Laune sinkt. Ein Reinigungsdienst im Turm räumt doppelt so schnell.",
                "Odd socks up to the ceiling. Nobody on this floor pays rent and moods drop. Housekeeping in the tower clears it twice as fast."),
            remedy = Remedy(
                Tr("Sockensuchhund holen", "Call the sock dog"), SOCK_DOG_COST,
                Tr("Der Sockensuchhund hat alles weggeräumt. Gefundene Paare: null.", "The sock dog cleared everything away. Pairs found: zero."),
            ),
            over = Tr("Die Socken-Lawine ist abgetragen. Gefundene Paare: null.", "The sock avalanche has been cleared. Pairs found: zero."),
            blocksRent = true,
            onHour = { ev -> changeMood(-5f) { it.floor == ev.floor } },
        ),
        LastingKind(
            "blackmail", Tr("Superschurken-Erpressung", "Supervillain blackmail"),
            Tr("Zahl ${formatMoney(BLACKMAIL)} bis morgen, sonst fallen eine Stunde lang alle Aufzüge aus. Ein Sicherheitsdienst wehrt den Mondlaser vielleicht ab.",
                "Pay ${formatMoney(BLACKMAIL)} by tomorrow, or every elevator stops for an hour. Security might fend off the moon laser."),
            remedy = Remedy(
                Tr("Zahlen", "Pay"), BLACKMAIL,
                Tr("Gezahlt. Der Schurke bedankt sich mit einem handgeschriebenen Drohbrief.", "Paid. The villain says thanks with a handwritten threatening letter."),
            ),
            onEnd = { _, expired ->
                if (expired) {
                    val secured = tower.allRooms.any { it.type.role == RoomRole.SECURITY }
                    if (secured && random.nextBoolean()) {
                        news.post(tr("Der Sicherheitsdienst hat den Mondlaser mit einem Taschenspiegel umgelenkt. Der Mond hat jetzt ein Loch.",
                            "Security deflected the moon laser with a pocket mirror. The moon now has a hole in it."), important = true)
                    } else {
                        elevators.cars.values.forEach { it.blockedUntil = maxOf(it.blockedUntil, clock.totalMinutes + 60) }
                        news.post(tr("Der Mondlaser hat getroffen! Alle Aufzüge stehen eine Stunde still. Die Treppe hat Hochkonjunktur.",
                            "The moon laser hit! Every elevator stands still for an hour. The stairs are booming."), important = true)
                    }
                }
            },
        ),
        LastingKind(
            "firealarm", Tr("Feueralarm durch Toaster", "Fire alarm by toaster"),
            Tr("Alle verlassen den Turm, bis Entwarnung kommt. Ein Sicherheitsdienst gibt doppelt so schnell Entwarnung.",
                "Everybody leaves the tower until the all-clear. Security gives the all-clear twice as fast."),
            remedy = Remedy(
                Tr("Fehlalarm melden", "Report a false alarm"), FALSE_ALARM_COST,
                Tr("Fehlalarm gemeldet. Der Toaster wird verhört. Alle dürfen zurück.", "False alarm reported. The toaster is being questioned. Everybody may come back."),
            ),
            over = Tr("Entwarnung! Es war nur ein Toast. Alle dürfen zurück in den Turm.", "All clear! It was only a piece of toast. Everybody may go back in."),
            evacuates = true,
            noVisitors = true,
            resume = { evacuate() },
        ),
        LastingKind(
            "termites", Tr("Termiten mit Architekturdiplom", "Termites with a degree in architecture"),
            Tr("Die Termiten bauen das Zimmer um. Bis sie fertig sind, zieht hier niemand ein.", "The termites are remodelling the room. Nobody moves in until they are done."),
            remedy = Remedy(
                Tr("Kammerjäger rufen", "Call the exterminator"), EXTERMINATOR_COST,
                Tr("Der Kammerjäger war da. Die Termiten hinterlassen eine Bewertung: vier Sterne, gutes Holz.", "The exterminator has been. The termites leave a review: four stars, good wood."),
            ),
            over = Tr("Die Termiten sind weitergezogen. Sie haben eine Wendeltreppe gebaut. Sie führt nirgendwohin.", "The termites have moved on. They built a spiral staircase. It leads nowhere."),
            blocksMoveIn = true,
        ),
        LastingKind(
            "exchange", Tr("Alien-Austauschprogramm", "Alien exchange programme"),
            Tr("Für einen Tag wohnen hier Aliens. Danach kommen die alten Bewohner zurück.", "Aliens live here for a day. Then the old residents come back."),
            onEnd = { ev, _ ->
                val home = tower.room(ev.roomId ?: -1)
                val back = ev.note?.let { id -> Tenants.all.firstOrNull { it.id == id } }
                if (home != null && back != null && home.tenant?.id == "exchange") {
                    moveIn(home, back, announce = false)
                    news.post(tr("Die Austausch-Aliens fliegen heim. ${back.name} ist zurück und findet die Möbel an der Decke.",
                        "The exchange aliens fly home. ${back.name} is back and finds the furniture on the ceiling."), home.center, important = true)
                }
            },
        ),
        LastingKind(
            "openday", Tr("Tag der offenen Tür", "Open day"),
            Tr("Doppelt so viele Besucher, und die Restaurants verdienen doppelt. Die Aufzüge werden voll.", "Twice the visitors, and the restaurants earn twice as much. The elevators get crowded."),
            over = Tr("Der Tag der offenen Tür ist vorbei. Jemand hat den Kaktus aus der Lobby mitgenommen.", "The open day is over. Somebody took the cactus from the lobby."),
            visitorBoost = true,
        ),
        LastingKind(
            "giantcat", Tr("Riesenkatze auf dem Dach", "Giant cat on the roof"),
            Tr("Bleibt sie zwei Stunden, gibt es Geld für die Fotorechte. Bis dahin sinkt die Laune auf der Etage.", "If she stays for two hours, the photo rights pay well. Until then, moods drop on this floor."),
            remedy = Remedy(
                Tr("Thunfisch anbieten", "Offer tuna"), TUNA_COST,
                Tr("Die Katze nimmt den Thunfisch und springt davon. Die Fotografen sind enttäuscht.", "The cat takes the tuna and jumps away. The photographers are disappointed."),
            ),
            onHour = { ev -> changeMood(-8f) { it.floor == ev.floor } },
            onEnd = { ev, expired ->
                if (expired) {
                    val fee = (PHOTO_FEE * starBonus).toLong()
                    val where = ev.floor?.let { Loc(it, ev.x ?: (lotWidth / 2f)) }
                    book(fee, MoneyKind.LUCK, tr("Fotorechte Riesenkatze", "Giant cat photo rights"), where)
                    news.post(tr("Die Riesenkatze ist weitergezogen. Die Fotos gehen um die Welt: ${formatMoney(fee)} für die Fotorechte.",
                        "The giant cat has moved on. The photos go around the world: ${formatMoney(fee)} for the photo rights."), where, important = true)
                }
            },
        ),
        LastingKind(
            "moon", Tr("Turm auf dem Mond", "Tower on the moon"),
            Tr("Der Teleporter hat den ganzen Turm auf den Mond gebeamt. Keine Besucher, bis er zurück ist.", "The teleporter beamed the whole tower to the moon. No visitors until it is back."),
            noVisitors = true,
            onEnd = { _, _ ->
                val tips = (MOON_TIPS * starBonus).toLong()
                book(tips, MoneyKind.LUCK, tr("Mond-Touristen", "Moon tourists"))
                if (tower.allRooms.any { it.type.role == RoomRole.LOBBY }) repeat(6) { spawnVisitor() }
                news.post(tr("Der Turm ist zurück! Ein paar Mond-Touristen sind mitgekommen und geben ${formatMoney(tips)} Trinkgeld. In Mondgestein.",
                    "The tower is back! A few moon tourists came along and tip ${formatMoney(tips)}. In moon rock."), important = true)
            },
        ),
    )

    private val byId = all.associateBy { it.id }

    fun kind(id: String): LastingKind = byId.getValue(id)

    fun exists(id: String): Boolean = id in byId

    /** Chance per night that the clowns take over another office. */
    const val CLOWN_SPREAD = 0.25f
}

val ActiveEvent.kind: LastingKind get() = Lasting.kind(id)

/** Starts [event] unless one of its kind is already running, and tells the ticker. */
fun Simulation.startEvent(event: ActiveEvent, text: String, where: Loc? = whereOf(event)): Boolean {
    if (activeEvents.any { it.id == event.id }) return false
    activeEvents += event
    event.kind.resume(this, event)
    news.post(text, where, important = true)
    return true
}

fun Simulation.endEvent(event: ActiveEvent, expired: Boolean) {
    if (!activeEvents.remove(event)) return
    event.kind.onEnd(this, event, expired)
}

/** Why the remedy for [event] can't be used now, or null when it can. */
fun Simulation.remedyBlocked(event: ActiveEvent): String? {
    val remedy = event.kind.remedy ?: return tr("Dagegen hilft nichts", "Nothing helps against it")
    if (event !in activeEvents) return tr("Schon vorbei", "Already over")
    remedy.blocked(this, event)?.let { return it }
    if (money < remedy.cost) return tr("Zu wenig Geld", "Not enough money")
    return null
}

/** The player pays for the remedy of [event] and it ends. */
fun Simulation.useRemedy(event: ActiveEvent): PlaceCheck {
    remedyBlocked(event)?.let { return PlaceCheck.no(it) }
    val remedy = event.kind.remedy!!
    val where = whereOf(event)
    book(-remedy.cost, MoneyKind.EVENTS, event.kind.title.toString(), where)
    remedy.apply(this, event)
    endEvent(event, expired = false)
    news.post(remedy.done.toString(), where)
    return PlaceCheck.OK
}

/** The shaft an elevator event is about. */
fun Simulation.carOf(event: ActiveEvent): Car? {
    val x = event.carX ?: return null
    return elevators.cars.values.firstOrNull { it.x == x && (event.floor == null || event.floor in it.floors) }
}

/** Where the event is shown in the tower, or null for the whole tower. */
fun Simulation.whereOf(event: ActiveEvent): Loc? {
    event.roomId?.let { id -> return tower.room(id)?.center }
    carOf(event)?.let { return Loc(it.position.roundToInt(), it.centerX) }
    return event.floor?.let { Loc(it, event.x ?: (lotWidth / 2f)) }
}

/** Events the inspector shows for [room]: its own, those of its floor, those of its shaft. */
fun Simulation.eventsAt(room: Room): List<ActiveEvent> = activeEvents.filter { event ->
    when {
        event.roomId != null -> event.roomId == room.id
        event.carX != null -> room.type.role == RoomRole.ELEVATOR && carOf(event)?.let { it.x == room.x && room.floor in it.floors } == true
        else -> event.floor == room.floor
    }
}

/** Called every game minute: runs the events, ends those whose time is up. */
fun Simulation.tickEvents(newHour: Boolean) {
    if (activeEvents.isEmpty()) return
    val now = clock.totalMinutes
    for (event in activeEvents.toList()) {
        val kind = event.kind
        // the room or shaft it was about is gone
        val gone = (event.roomId != null && tower.room(event.roomId) == null) || (event.carX != null && carOf(event) == null)
        when {
            gone -> endEvent(event, expired = false)
            now >= event.ends -> {
                endEvent(event, expired = true)
                kind.over?.let { news.post(it.toString(), whereOf(event)) }
            }
            kind.solved(this, event) -> {
                endEvent(event, expired = false)
                kind.solvedText?.let { news.post(it.toString(), whereOf(event), important = true) }
            }
            newHour -> kind.onHour(this, event)
        }
    }
}

val Simulation.evacuated: Boolean get() = activeEvents.any { it.kind.evacuates }

/** No sightseers come right now. */
val Simulation.visitorsStopped: Boolean get() = activeEvents.any { it.kind.noVisitors }

/** Sightseers and meals count double during the open day. */
val Simulation.visitorBoost: Int get() = if (activeEvents.any { it.kind.visitorBoost }) 2 else 1

fun Simulation.rentBlocked(room: Room): Boolean = activeEvents.any { it.kind.blocksRent && it.floor == room.floor }

fun Simulation.moveInBlocked(room: Room): Boolean = activeEvents.any { it.kind.blocksMoveIn && it.roomId == room.id }

/** Sends everybody inside out to the street; the schedule keeps them out while [evacuated]. */
internal fun Simulation.evacuate() = with(world) {
    family { all(Member, Schedule) }.forEach { e ->
        if (e has Away || e has Riding) return@forEach
        e.configure { drop(it, Idle); drop(it, Route); drop(it, Goal); drop(it, Stuck) }
    }
    family { all(Visitor) }.forEach { e ->
        e[Visitor].stopsLeft = 0
        e.configure { drop(it, Idle) }
    }
}

/** Why the player can't hold an open day right now, or null. */
fun Simulation.openDayBlocked(): String? = when {
    stage < Lasting.OPEN_DAY_STAGE -> tr("Tag der offenen Tür erst ab Stufe ${Lasting.OPEN_DAY_STAGE}", "Open day only from stage ${Lasting.OPEN_DAY_STAGE}")
    activeEvents.any { it.id == "openday" } -> tr("Heute ist schon Tag der offenen Tür", "It is open day already")
    clock.totalMinutes - lastOpenDay < Lasting.OPEN_DAY_COOLDOWN -> {
        val days = ((Lasting.OPEN_DAY_COOLDOWN - (clock.totalMinutes - lastOpenDay)) / GameClock.MINUTES_PER_DAY).toInt() + 1
        tr("Nächster Tag der offenen Tür in $days Tagen", "Next open day in $days days")
    }
    tower.allRooms.none { it.type.role == RoomRole.LOBBY } -> tr("Ohne Lobby kommt niemand", "Nobody comes without a lobby")
    clock.hour !in 8 until 18 -> tr("Nur zwischen 8 und 18 Uhr", "Only between 8 am and 6 pm")
    else -> null
}

/** The player opens the doors: lots of visitors until the evening. */
fun Simulation.startOpenDay(): PlaceCheck {
    openDayBlocked()?.let { return PlaceCheck.no(it) }
    val now = clock.totalMinutes
    val ends = clock.day * GameClock.MINUTES_PER_DAY + Lasting.OPEN_DAY_ENDS * 60.0
    lastOpenDay = now
    startEvent(
        ActiveEvent("openday", now, ends),
        tr("Tag der offenen Tür! Bis ${Lasting.OPEN_DAY_ENDS} Uhr darf jeder rein. Die Lobby verteilt Luftballons.", "Open day! Everybody may come in until ${Lasting.OPEN_DAY_ENDS - 12} pm. The lobby hands out balloons."),
    )
    repeat(10) { spawnVisitor() }
    return PlaceCheck.OK
}
