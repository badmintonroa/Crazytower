package crazytower.game

import crazytower.core.Simulation
import crazytower.core.ecs.Goal
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
                image(assets.room(art)) { scale = 96.0 / bitmap.width; xy(16.0, y) }
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
    fun describe(sim: Simulation, room: crazytower.core.Room): RoomCard =
        RoomCard(room.type.name, "${floorName(room.floor)} · ${room.width} Felder breit", emptyList(), null)
}

/** Text for the inspector's person card. */
object PersonInfo {
    fun role(sim: Simulation, e: com.github.quillraven.fleks.Entity): String = with(sim.world) {
        if (e has Visitor) "Besucher" else "Bewohner"
    }

    fun doing(sim: Simulation, e: com.github.quillraven.fleks.Entity): String = with(sim.world) {
        val visitor = e.getOrNull(Visitor)
        when {
            e has Stuck -> "Findet keinen Weg"
            visitor != null && visitor.leaving -> "Verlässt den Turm"
            e has Idle -> "Staunt die Lobby an"
            e has Goal -> "Läuft herum"
            else -> "Überlegt"
        }
    }

    fun extra(sim: Simulation, e: com.github.quillraven.fleks.Entity): List<Pair<String, String>> = emptyList()
}
