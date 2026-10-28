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
import crazytower.game.ui.label
import korlibs.korge.view.*

/** The info card on the right: shows the selected room or person. */
class Inspector(private val sim: Simulation, private val game: GameController, private val assets: Assets) : Container() {
    val panelWidth = 290.0
    var panelHeight = 200.0
        private set
    private var shown: Any? = null
    private var refresh = 0

    init { visible = false }

    fun sync() {
        val sel = game.selection
        if (sel == null) {
            visible = false
            shown = null
            return
        }
        visible = true
        // rebuild when the selection changes and a few times per second for live values
        if (sel != shown || ++refresh % 15 == 0) {
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
                lines += "Macht gerade" to PersonInfo.doing(sim, e)
                PersonInfo.extra(sim, e).forEach { lines += it }
            }
        }
        var y = 16.0
        when {
            art != null -> {
                image(assets.room(art)) { scale = minOf(96.0 / bitmap.width, 32.0 / bitmap.height); xy(16.0, y) }
                label(title.uppercase(), 18.0, Theme.TEXT, Theme.pixel).xy(16.0, y + 42)
                label(subtitle, 12.0, Theme.TEXT_DIM).xy(16.0, y + 66)
                y += 92.0
            }
            look != null -> {
                image(assets.person(look, 0)) { smoothing = false; scale = 2.0; xy(16.0, y) }
                label(title.uppercase(), 18.0, Theme.TEXT, Theme.pixel).xy(52.0, y + 6)
                label(subtitle, 12.0, Theme.TEXT_DIM).xy(52.0, y + 30)
                y += 60.0
            }
        }
        for ((key, value) in lines) {
            label(key, 12.0, Theme.TEXT_DIM).xy(16.0, y)
            label(value, 14.0, Theme.TEXT, Theme.bodyBold).xy(120.0, y - 1)
            y += 22.0
        }
        if (quirk != null) {
            val box = Panel(panelWidth - 32, 56.0, Theme.BUTTON, 6.0).addTo(this).also { it.xy(16.0, y + 4) }
            box.label("Macke", 12.0, Theme.ALARM, Theme.bodyBold).xy(10.0, 6.0)
            box.label(quirk, 12.0, Theme.TEXT).xy(10.0, 24.0)
            y += 66.0
        }
        TextButton(panelWidth - 32, 40.0, "Schließen", 14.0) { game.selection = null }.addTo(this).xy(16.0, y + 8)
        y += 56.0
        panelHeight = y + 8
        bg.resize(panelWidth, panelHeight)
    }
}

fun floorName(floor: Int): String = when {
    floor == 0 -> "Erdgeschoss"
    floor < 0 -> "${-floor}. Untergeschoss"
    else -> "$floor. Etage"
}

/** Text for the inspector's room card. */
data class RoomCard(val title: String, val subtitle: String, val lines: List<Pair<String, String>>, val quirk: String?)

object RoomInfo {
    fun describe(sim: Simulation, room: crazytower.core.Room): RoomCard {
        val where = "${floorName(room.floor)} · ${room.width} Felder breit"
        val tenant = room.tenant
        val lines = ArrayList<Pair<String, String>>()
        return when {
            room.type.role == RoomRole.ELEVATOR -> {
                val car = sim.elevators.cars.values.firstOrNull { it.x == room.x && room.floor in it.floors }
                if (car != null) {
                    lines += "Schacht" to "${floorName(car.bottom)} bis ${floorName(car.top)}"
                    lines += "Fahrgäste" to "${car.riders.size} von ${Elevators.CAPACITY}"
                    lines += "Warten" to "${car.waitingCount} Leute"
                }
                RoomCard(room.type.name, where, lines, null)
            }
            tenant != null -> {
                lines += "Raum" to room.type.name
                sim.moodOf(room)?.let { lines += "Laune" to "${it.toInt()} %" }
                lines += "Schicht" to tenant.shift.label
                lines += if (room.type.role == RoomRole.HOME) "Bewohner" to "${room.type.capacity}" else "Leute" to "${room.type.capacity}"
                RoomCard(tenant.name, where, lines, tenant.quirk)
            }
            room.type.role.rentable -> {
                lines += "Status" to "Zu vermieten"
                lines += "Hinweis" to if (sim.nav.findPath(Loc(0, 0f), room.center) == null) "Nicht erreichbar" else "Mieter kommen bald"
                RoomCard(room.type.name, where, lines, null)
            }
            else -> RoomCard(room.type.name, where, lines, null)
        }
    }
}

/** Text for the inspector's person card. */
object PersonInfo {
    fun role(sim: Simulation, e: com.github.quillraven.fleks.Entity): String = with(sim.world) {
        val member = e.getOrNull(Member)
        when {
            e has Visitor -> "Besucher"
            member != null && member.resident -> "Wohnt hier"
            member != null -> "Arbeitet hier"
            else -> "Gast"
        }
    }

    fun doing(sim: Simulation, e: com.github.quillraven.fleks.Entity): String = with(sim.world) {
        val visitor = e.getOrNull(Visitor)
        when {
            e has Stuck -> "Findet keinen Weg"
            e has Schedule && e[Schedule].activity == Activity.AT_ROOM && e.getOrNull(Member)?.resident == true ->
                if (sim.clock.isNight) "Schläft" else "Ist zu Hause"
            e has Schedule && e hasNo crazytower.core.ecs.Riding && e hasNo Goal -> e[Schedule].activity.label
            e has crazytower.core.ecs.Riding -> "Fährt Aufzug"
            (e.getOrNull(crazytower.core.ecs.Route)?.legs?.firstOrNull() is crazytower.core.nav.Leg.Ride) -> "Wartet auf den Aufzug"
            (e.getOrNull(crazytower.core.ecs.Route)?.legs?.firstOrNull() is crazytower.core.nav.Leg.Stairs) -> "Nimmt die Treppe"
            (e.getOrNull(crazytower.core.ecs.Route)?.legs?.firstOrNull() is crazytower.core.nav.Leg.Teleport) -> "Wird teleportiert"
            e has crazytower.core.ecs.Vip -> "Inspiziert den Turm"
            visitor != null && visitor.leaving -> "Verlässt den Turm"
            e has Idle -> "Staunt die Lobby an"
            e has Goal -> "Läuft herum"
            else -> "Überlegt"
        }
    }

    fun extra(sim: Simulation, e: com.github.quillraven.fleks.Entity): List<Pair<String, String>> = with(sim.world) {
        val member = e.getOrNull(Member) ?: return emptyList()
        val schedule = e[Schedule]
        val room = sim.tower.room(member.roomId)
        listOfNotNull(
            room?.tenant?.let { (if (member.resident) "Bei" else "Firma") to it.name.take(24) },
            "Schicht" to schedule.shift.label,
            "Laune" to "${schedule.mood.toInt()} %",
        )
    }
}
