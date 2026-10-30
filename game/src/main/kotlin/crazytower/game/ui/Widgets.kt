package crazytower.game.ui

import crazytower.game.Sfx
import crazytower.game.Theme
import korlibs.image.color.RGBA
import korlibs.image.font.Font
import korlibs.korge.input.onClick
import korlibs.korge.input.onDown
import korlibs.korge.input.onOut
import korlibs.korge.input.onOver
import korlibs.korge.input.onUpAnywhere
import korlibs.korge.view.*
import korlibs.math.geom.Point
import korlibs.math.geom.vector.VectorBuilder

/** Draws a rectangle with the top-left and bottom-right corners cut off (the low-poly panel shape). */
fun Graphics.chamfer(width: Double, height: Double, cut: Double, color: RGBA) {
    updateShape { fill(color) { chamferPath(width, height, cut, 0.0) } }
}

/** A dark UI panel with chamfered corners. */
class Panel(var panelWidth: Double, var panelHeight: Double, color: RGBA = Theme.PANEL, private val cut: Double = 10.0) : Container() {
    private val bg = graphics { }
    var color: RGBA = color
        set(value) { field = value; redraw() }

    init { redraw() }

    fun resize(width: Double, height: Double) {
        panelWidth = width
        panelHeight = height
        redraw()
    }

    private fun redraw() = bg.chamfer(panelWidth, panelHeight, cut, color)
}

fun Container.panel(width: Double, height: Double, color: RGBA = Theme.PANEL, cut: Double = 10.0): Panel =
    Panel(width, height, color, cut).addTo(this)

fun Container.label(
    value: String,
    size: Double = 14.0,
    color: RGBA = Theme.TEXT,
    font: Font = Theme.body,
): Text = text(value, textSize = size, color = color, font = font)

/**
 * Makes a one-line text at most [maxWidth] wide: a little too long shrinks it, much too long
 * also cuts it with "…". Texts that fit stay as they are, in every language.
 */
fun Text.fitWidth(maxWidth: Double): Text {
    scale = 1.0
    if (width <= maxWidth) return this
    val full = text
    var keep = full.length
    while (width * MIN_FIT > maxWidth && keep > 1) {
        keep--
        text = full.take(keep).trimEnd() + "…"
    }
    if (width > maxWidth) scale = maxWidth / width
    return this
}

/** Texts are shrunk to this share of their size at most before they are cut. */
private const val MIN_FIT = 0.8

/**
 * Breaks [value] into lines of at most [maxWidth] at [size] in [font], at the spaces.
 * Line breaks already in [value] are kept.
 */
fun wrapText(value: String, maxWidth: Double, size: Double, font: Font = Theme.body): String {
    val probe = Text("", size, font = font)
    fun widthOf(s: String): Double { probe.text = s; return probe.width }
    return value.split('\n').joinToString("\n") { paragraph ->
        val lines = ArrayList<String>()
        var line = ""
        for (word in paragraph.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && widthOf(candidate) > maxWidth) {
                lines += line
                line = word
            } else {
                line = candidate
            }
        }
        lines += line
        lines.joinToString("\n")
    }
}

/** A label broken into lines of at most [maxWidth], see [wrapText]. */
fun Container.wrappedLabel(
    value: String,
    maxWidth: Double,
    size: Double = 14.0,
    color: RGBA = Theme.TEXT,
    font: Font = Theme.body,
): Text = label(wrapText(value, maxWidth, size, font), size, color, font)

/**
 * The game's button: hover lifts it with a teal frame, pressed pushes it down,
 * selected fills it teal, disabled shows it greyed out.
 */
open class Button(val buttonWidth: Double, val buttonHeight: Double, private val onPress: () -> Unit) : Container() {
    private val shadow = graphics { }
    private val bg = graphics { }
    val content = Container().addTo(this)
    private var hovered = false
    private var pressed = false

    var selected = false
        set(value) { if (field != value) { field = value; redraw() } }
    var enabled = true
        set(value) { if (field != value) { field = value; redraw() } }

    /** Colour for icons and labels that follow the button state. */
    val ink: RGBA get() = when {
        !enabled -> Theme.TEXT_LOCKED
        selected -> Theme.ON_SELECT
        else -> Theme.TEXT
    }

    /** Called after every state change so subclasses can recolour their content. */
    var onRestyle: (Button) -> Unit = {}

    init {
        onOver { hovered = true; redraw() }
        onOut { hovered = false; pressed = false; redraw() }
        onDown { if (enabled) { pressed = true; redraw() } }
        onUpAnywhere { pressed = false; redraw() }
        onClick { if (enabled) { Sfx.play(Sfx.CLICK); onPress() } }
        redraw()
    }

    protected fun redraw() {
        val fill = when {
            !enabled -> Theme.LOCKED
            selected -> Theme.SELECT
            pressed -> Theme.BUTTON_DOWN
            hovered -> Theme.BUTTON_HOVER
            else -> Theme.BUTTON
        }
        val framed = hovered && enabled && !selected && !pressed
        val lift = when {
            pressed -> 2.0
            framed -> -1.0
            else -> 0.0
        }
        val faceHeight = buttonHeight - 3.0
        shadow.updateShape {
            fill(if (selected) Theme.SELECT_DARK else Theme.INK) { chamferPath(buttonWidth, faceHeight, 8.0, 0.0) }
        }
        shadow.y = 3.0
        shadow.visible = enabled
        bg.updateShape {
            if (framed) {
                fill(Theme.SELECT) { chamferPath(buttonWidth, faceHeight, 8.0, 0.0) }
                fill(fill) { chamferPath(buttonWidth, faceHeight, 8.0, 2.0) }
            } else {
                fill(fill) { chamferPath(buttonWidth, faceHeight, 8.0, 0.0) }
            }
        }
        bg.y = lift
        content.y = lift
        onRestyle(this)
    }
}

/** Outline of a chamfered rectangle, shrunk by [inset] on every side. */
fun VectorBuilder.chamferPath(w: Double, h: Double, cut: Double, inset: Double) {
    moveTo(Point(cut + inset, inset))
    lineTo(Point(w - inset, inset))
    lineTo(Point(w - inset, h - cut))
    lineTo(Point(w - cut, h - inset))
    lineTo(Point(inset, h - inset))
    lineTo(Point(inset, cut))
    close()
}

/** A button with a single centred text label. */
class TextButton(width: Double, height: Double, title: String, size: Double = 15.0, font: Font = Theme.bodyBold, onPress: () -> Unit) :
    Button(width, height, onPress) {
    val text: Text = content.text(title, textSize = size, color = Theme.TEXT, font = font)

    init {
        onRestyle = { text.color = it.ink }
        centre()
        onRestyle(this)
    }

    fun setTitle(title: String) {
        text.text = title
        centre()
    }

    private fun centre() {
        // long titles (some languages need more letters) shrink instead of spilling over the edge
        text.fitWidth(buttonWidth - 12.0)
        text.x = (buttonWidth - text.width * text.scaleX) / 2
        text.y = (buttonHeight - 3.0 - text.height * text.scaleY) / 2
    }
}

/** Numbers in the current language: 48250 is "48.250" or "48,250". */
fun formatNumber(value: Long): String = crazytower.core.formatNumber(value)

/** Money in the current language: "48.250 $" or "$48,250". */
fun formatMoney(value: Long): String = crazytower.core.formatMoney(value)
