package crazytower.game

import crazytower.core.Loc
import crazytower.core.RoomRole
import crazytower.core.Simulation
import crazytower.core.transport.Elevators
import crazytower.core.ecs.Activity
import crazytower.core.ecs.Goal
import crazytower.core.ecs.Member
import crazytower.core.ecs.Schedule
import crazytower.core.ecs.Idle
import crazytower.core.ecs.Person
import crazytower.core.ecs.Position
import crazytower.core.ecs.Stuck
import crazytower.core.ecs.Visitor
import crazytower.game.ui.Panel
import crazytower.game.ui.TextButton
import crazytower.game.ui.fitWidth
import crazytower.game.ui.formatMoney
import crazytower.game.ui.label
import crazytower.game.ui.wrappedLabel
import crazytower.core.tr
import korlibs.korge.input.onOut
import korlibs.korge.input.onOver
import korlibs.korge.view.*

/** The info card on the right: shows the selected room or person. */
class Inspector(private val sim: Simulation, private val game: GameController, private val assets: Assets) : Container() {
    val panelWidth = 290.0
    var panelHeight = 200.0
        private set
    private var shown: Any? = null
    private var refresh = 0

    /** While the mouse is on the card it isn't rebuilt, so its buttons can be clicked. */
    private var hovered = false

    init {
        visible = false
        onOver { hovered = true }
        onOut { hovered = false }
    }

    fun sync() {
        val sel = game.selection
        if (sel == null) {
            visible = false
            shown = null
            return
        }
        visible = true
        // rebuild when the selection changes and a few times per second for live values
        if (sel != shown || (++refresh % 15 == 0 && !hovered)) {
            shown = sel
            rebuild(sel)
        }
    }

    private fun rebuild(sel: Selection) {
        removeChildren()
        val bg = Panel(panelWidth, 200.0).addTo(this)
        val lines = ArrayList<Pair<String, String>>()
        var title = ""
        var subtitle = ""
        var art: String? = null
        var look: String? = null
        var quirk: String? = null
        when (sel) {
            is Selection.RoomSel -> {
                val room = sim.tower.room(sel.roomId) ?: return
                val info = RoomInfo.describe(sim, room)
                title = info.title
                subtitle = info.subtitle
                art = RoomLooks.art(sim, room)
                lines += info.lines
                quirk = info.quirk
            }
            is Selection.PersonSel -> with(sim.world) {
                val e = sel.entity
                if (!sim.world.contains(e)) return
                val person = e[Person]
                val pos = e[Position]
                title = person.name
                subtitle = "${PersonInfo.role(sim, e)} · ${floorName(pos.floor)}"
                look = person.look
                lines += tr("Macht gerade", "Doing") to PersonInfo.doing(sim, e)
                PersonInfo.extra(sim, e).forEach { lines += it }
            }
        }
        var y = 16.0
        when {
            art != null -> {
                image(assets.room(art)) { scale = minOf(96.0 / bitmap.width, 32.0 / bitmap.height); xy(16.0, y) }
                label(title.uppercase(), 18.0, Theme.TEXT, Theme.bodyBold).xy(16.0, y + 42).fitWidth(panelWidth - 32)
                label(subtitle, 12.0, Theme.TEXT_DIM).xy(16.0, y + 66).fitWidth(panelWidth - 32)
                y += 92.0
            }
            look != null -> {
                image(assets.person(look, 0)) { scale = assets.personScale(look, 2.0); smoothing = scale < 1.0; xy(16.0, y) }
                label(title.uppercase(), 18.0, Theme.TEXT, Theme.bodyBold).xy(52.0, y + 6).fitWidth(panelWidth - 68)
                label(subtitle, 12.0, Theme.TEXT_DIM).xy(52.0, y + 30).fitWidth(panelWidth - 68)
                y += 60.0
            }
        }
        for ((key, value) in lines) {
            label(key, 12.0, Theme.TEXT_DIM).xy(16.0, y).fitWidth(VALUE_X - 22.0)
            label(value, 14.0, Theme.TEXT, Theme.bodyBold).xy(VALUE_X, y - 1).fitWidth(panelWidth - VALUE_X - 14.0)
            y += 22.0
        }
        if (quirk != null) {
            // long quirks wrap, and the box grows with them
            val box = Panel(panelWidth - 32, 56.0, Theme.BUTTON, 6.0).addTo(this).also { it.xy(16.0, y + 4) }
            box.label(tr("Macke", "Quirk"), 12.0, Theme.ALARM, Theme.bodyBold).xy(10.0, 6.0)
            val text = box.wrappedLabel(quirk, panelWidth - 52, 12.0, Theme.TEXT).xy(10.0, 24.0)
            val boxHeight = maxOf(56.0, text.height + 32.0)
            box.resize(panelWidth - 32, boxHeight)
            y += boxHeight + 10.0
        }
        val shaft = (sel as? Selection.RoomSel)?.let { sim.tower.room(it.roomId) }?.takeIf { it.type.role == RoomRole.ELEVATOR }
            ?.let { room -> sim.elevators.cars.values.firstOrNull { it.x == room.x && room.floor in it.floors } }
        if (shaft != null) {
            val half = (panelWidth - 40) / 2
            TextButton(half, 40.0, tr("+ Kabine", "+ Cabin"), 14.0) {
                val result = sim.addCabin(shaft)
                game.say(if (result.ok) tr("Neue Kabine, ", "New cabin, ") + formatMoney(Elevators.CABIN_COST) else result.reason)
                Sfx.play(if (result.ok) Sfx.BUILD else Sfx.ERROR)
                shown = null
            }.addTo(this).xy(16.0, y + 8).also { it.enabled = sim.checkCabin(shaft).ok }
            TextButton(half, 40.0, tr("− Kabine", "− Cabin"), 14.0) {
                if (sim.removeCabin(shaft)) Sfx.play(Sfx.DEMOLISH)
                shown = null
            }.addTo(this).xy(24.0 + half, y + 8).also { it.enabled = shaft.cabins.size > 1 }
            y += 48.0
        }
        TextButton(panelWidth - 32, 40.0, tr("Schließen", "Close"), 14.0) { game.selection = null }.addTo(this).xy(16.0, y + 8)
        y += 56.0
        panelHeight = y + 8
        bg.resize(panelWidth, panelHeight)
    }

    private companion object {
        /** Where the values start, right of their names. */
        const val VALUE_X = 120.0
    }
}

fun floorName(floor: Int): String = Simulation.floorLabel(floor).replaceFirstChar { it.uppercase() }

/** Text for the inspector's room card. */
data class RoomCard(val title: String, val subtitle: String, val lines: List<Pair<String, String>>, val quirk: String?)

object RoomInfo {
    private val ROOM get() = tr("Raum", "Room")
    private val MOOD get() = tr("Laune", "Mood")
    private val HOURS get() = tr("Zeiten", "Hours")
    private val PEOPLE get() = tr("Leute", "People")
    private val STATUS get() = tr("Status", "Status")
    private val HINT get() = tr("Hinweis", "Note")

    fun describe(sim: Simulation, room: crazytower.core.Room): RoomCard {
        val where = "${floorName(room.floor)} · " + tr("${room.width} Felder breit", "${room.width} tiles wide")
        val tenant = room.tenant
        val lines = ArrayList<Pair<String, String>>()
        return when {
            room.type.role == RoomRole.ELEVATOR -> {
                val car = sim.elevators.cars.values.firstOrNull { it.x == room.x && room.floor in it.floors }
                if (car != null) {
                    lines += tr("Schacht", "Shaft") to "${floorName(car.bottom)} " + tr("bis", "to") + " ${floorName(car.top)}"
                    lines += tr("Kabinen", "Cabins") to tr("${car.cabins.size} von ${Elevators.maxCabins(sim.bestStars)}", "${car.cabins.size} of ${Elevators.maxCabins(sim.bestStars)}") +
                        if (car.cabins.size < Elevators.MAX_CABINS && Elevators.maxCabins(sim.bestStars) <= car.cabins.size) tr(" (mehr mit Sternen)", " (more with stars)") else ""
                    if (car.express) lines += tr("Hält auf", "Stops at") to car.stops.joinToString(", ") { if (it == 0) tr("EG", "G") else "$it" }
                    lines += tr("Fahrgäste", "Riders") to tr("${car.riders.size} von ${car.capacity * car.cabins.size}", "${car.riders.size} of ${car.capacity * car.cabins.size}")
                    lines += tr("Warten", "Waiting") to tr("${car.waitingCount} Leute", "${car.waitingCount} people")
                }
                RoomCard(room.type.name.toString(), where, lines, null)
            }
            tenant != null && room.type.role == RoomRole.HOTEL -> {
                lines += ROOM to room.type.name.toString()
                sim.moodOf(room)?.let { lines += MOOD to "${it.toInt()} %" }
                lines += tr("Unterwegs", "Out") to tenant.plan.label.toString()
                lines += tr("Pro Nacht", "Per night") to formatMoney((tenant.rentPerDay * sim.starBonus).toLong()) + tr(" beim Auschecken", " at check-out")
                lines += tr("Gäste", "Guests") to "${room.type.capacity}"
                RoomCard(tenant.name.toString(), where, lines, tenant.quirk.toString())
            }
            tenant != null && room.type.role == RoomRole.HOUSEKEEPING -> {
                lines += ROOM to room.type.name.toString()
                lines += HOURS to tenant.plan.label.toString()
                lines += tr("Schmutzig", "Dirty") to tr("${sim.dirtyRooms.size} Hotelzimmer", "${sim.dirtyRooms.size} hotel rooms")
                lines += PEOPLE to "${room.type.capacity}"
                RoomCard(tenant.name.toString(), where, lines, tenant.quirk.toString())
            }
            room.dirty -> {
                lines += STATUS to tr("Muss geputzt werden", "Needs cleaning")
                lines += HINT to if (sim.tower.allRooms.any { it.type.role == RoomRole.HOUSEKEEPING && it.tenant != null }) tr("Die Reinigung kommt", "Housekeeping is coming") else tr("Kein Reinigungsdienst im Turm", "No housekeeping in the tower")
                RoomCard(room.type.name.toString(), where, lines, null)
            }
            tenant != null -> {
                lines += ROOM to room.type.name.toString()
                sim.moodOf(room)?.let { lines += MOOD to "${it.toInt()} %" }
                lines += (if (room.type.role == RoomRole.HOME) tr("Außer Haus", "Out") else HOURS) to tenant.plan.label.toString()
                lines += if (room.type.role == RoomRole.HOME) {
                    tr("Kaufpreis", "Price paid") to if (room.salePaid > 0) formatMoney(room.salePaid) else tr("geschenkt", "a gift")
                } else {
                    tr("Miete", "Rent") to formatMoney((tenant.rentPerDay * sim.starBonus).toLong()) + tr(" nach Feierabend", " after work")
                }
                lines += if (room.type.role == RoomRole.HOME) tr("Bewohner", "Residents") to "${room.type.capacity}" else PEOPLE to "${room.type.capacity}"
                RoomCard(tenant.name.toString(), where, lines, tenant.quirk.toString())
            }
            room.type.role.rentable -> {
                lines += STATUS to if (room.type.role == RoomRole.HOTEL) tr("Frei und sauber", "Free and clean") else tr("Zu vermieten", "For rent")
                lines += HINT to when {
                    sim.nav.findPath(Loc(0, 0f), room.center) == null -> tr("Nicht erreichbar", "Can't be reached")
                    room.type.role == RoomRole.HOTEL -> tr("Check-in ${Simulation.CHECK_IN_FROM}–${Simulation.CHECK_IN_UNTIL} Uhr", "Check-in ${Simulation.CHECK_IN_FROM}:00–${Simulation.CHECK_IN_UNTIL}:00")
                    else -> tr("Mieter kommen bald", "Tenants coming soon")
                }
                RoomCard(room.type.name.toString(), where, lines, null)
            }
            else -> RoomCard(room.type.name.toString(), where, lines, null)
        }
    }
}

/** Text for the inspector's person card. */
object PersonInfo {
    fun role(sim: Simulation, e: com.github.quillraven.fleks.Entity): String = with(sim.world) {
        val member = e.getOrNull(Member)
        when {
            e has Visitor -> tr("Besucher", "Visitor")
            member != null && member.resident -> tr("Wohnt hier", "Lives here")
            member != null -> tr("Arbeitet hier", "Works here")
            else -> tr("Gast", "Guest")
        }
    }

    fun doing(sim: Simulation, e: com.github.quillraven.fleks.Entity): String = with(sim.world) {
        val visitor = e.getOrNull(Visitor)
        when {
            e has Stuck -> tr("Findet keinen Weg", "Can't find a way")
            e has Schedule && e[Schedule].activity == Activity.AT_ROOM && e.getOrNull(Member)?.resident == true ->
                if (sim.clock.isNight) tr("Schläft", "Sleeping") else tr("Ist zu Hause", "At home")
            e has Schedule && e hasNo crazytower.core.ecs.Riding && e hasNo Goal -> e[Schedule].activity.label
            e has crazytower.core.ecs.Riding -> tr("Fährt Aufzug", "Riding the elevator")
            (e.getOrNull(crazytower.core.ecs.Route)?.legs?.firstOrNull() is crazytower.core.nav.Leg.Ride) -> tr("Wartet auf den Aufzug", "Waiting for the elevator")
            (e.getOrNull(crazytower.core.ecs.Route)?.legs?.firstOrNull() is crazytower.core.nav.Leg.Stairs) -> tr("Nimmt die Treppe", "Taking the stairs")
            (e.getOrNull(crazytower.core.ecs.Route)?.legs?.firstOrNull() is crazytower.core.nav.Leg.Teleport) -> tr("Wird teleportiert", "Being teleported")
            e has crazytower.core.ecs.Vip -> tr("Inspiziert den Turm", "Inspecting the tower")
            visitor != null && visitor.leaving -> tr("Verlässt den Turm", "Leaving the tower")
            e has Idle -> tr("Staunt die Lobby an", "Marvelling at the lobby")
            e has Goal -> tr("Läuft herum", "Walking around")
            else -> tr("Überlegt", "Thinking")
        }
    }

    fun extra(sim: Simulation, e: com.github.quillraven.fleks.Entity): List<Pair<String, String>> = with(sim.world) {
        val member = e.getOrNull(Member) ?: return emptyList()
        val schedule = e[Schedule]
        val room = sim.tower.room(member.roomId)
        listOfNotNull(
            room?.tenant?.let { (if (member.resident) tr("Bei", "With") else tr("Firma", "Company")) to it.name.toString() },
            tr("Zeiten", "Hours") to schedule.plan.label.toString(),
            tr("Laune", "Mood") to "${schedule.mood.toInt()} %",
        )
    }
}
