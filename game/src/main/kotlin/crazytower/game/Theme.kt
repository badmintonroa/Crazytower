package crazytower.game

import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.image.font.TtfFont
import korlibs.image.font.readTtfFont
import korlibs.io.file.std.resourcesVfs

/** Colours and fonts from the UI design (dark panels, teal = selected, pink = alarm). */
object Theme {
    val SKY = Colors["#2B3A67"]
    val SKY_FAR = Colors["#24325A"]
    val SKY_NEAR = Colors["#1D2748"]
    val GROUND = Colors["#3E8E6E"]
    val GROUND_DARK = Colors["#357D60"]
    val STREET = Colors["#3A4060"]
    val NIGHT = Colors["#0B0D1A"]
    val INK = Colors["#12152A"]
    val PANEL = Colors["#1E2236"]
    val BUTTON = Colors["#2A2F4A"]
    val BUTTON_HOVER = Colors["#363C5E"]
    val BUTTON_DOWN = Colors["#23283F"]
    val LOCKED = Colors["#1A1D2E"]
    val SELECT = Colors["#4FE3C1"]
    val SELECT_DARK = Colors["#2BB394"]
    val ON_SELECT = Colors["#0F1220"]
    val TEXT = Colors["#EEF0FA"]
    val TEXT_DIM = Colors["#A3A8C3"]
    val TEXT_LOCKED = Colors["#8A8FA8"]
    val GOLD = Colors["#FFC94D"]
    val ALARM = Colors["#FF5FA2"]
    val GOOD = Colors["#5EE0A0"]
    val EMPTY_STAR = Colors["#3A4060"]

    fun alpha(color: RGBA, a: Double): RGBA = color.withAd(a)

    lateinit var pixel: TtfFont
    lateinit var body: TtfFont
    lateinit var bodyBold: TtfFont

    suspend fun loadFonts() {
        pixel = resourcesVfs["fonts/PixelifySans-Bold.ttf"].readTtfFont()
        body = resourcesVfs["fonts/SpaceGrotesk-Medium.ttf"].readTtfFont()
        bodyBold = resourcesVfs["fonts/SpaceGrotesk-Bold.ttf"].readTtfFont()
    }
}
