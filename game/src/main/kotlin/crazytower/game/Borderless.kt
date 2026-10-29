package crazytower.game

import korlibs.render.GameWindow
import korlibs.render.awt.AwtGameWindow
import java.awt.EventQueue
import java.awt.GraphicsEnvironment

/**
 * Windowed fullscreen: a window without frame that covers the whole screen, so switching to
 * other programs stays quick. The frame can only be left off when the window is created
 * (taking it down later loses the graphics), so switching takes effect at the next start.
 */
object Borderless {
    /** The size of the main screen in pixels. */
    fun screenSize(): Pair<Int, Int> {
        val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
        return bounds.width to bounds.height
    }

    /** Moves the frameless [window] over the whole main screen. */
    fun cover(window: GameWindow) {
        val frame = (window as? AwtGameWindow)?.frame ?: return
        EventQueue.invokeLater {
            frame.bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
        }
    }
}
