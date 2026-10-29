package crazytower.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTest {
    @Test
    fun virtualScreenHasTheWindowsAspectRatio() {
        val s = Settings()
        assertEquals(1280 to 800, s.virtualSize(1280, 800))
        assertEquals(1422 to 800, s.virtualSize(1920, 1080))
        assertEquals(1280 to 800, s.virtualSize(1920, 1200))
        assertEquals(1422 to 800, s.virtualSize(2560, 1440))
    }

    @Test
    fun biggerInterfaceMeansSmallerVirtualScreen() {
        assertEquals(1293 to 727, Settings(uiScale = 1.1).virtualSize(1920, 1080))
        // too narrow for the top bar: widened to the minimum, same aspect ratio
        assertEquals(1240 to 698, Settings(uiScale = 1.3).virtualSize(1920, 1080))
    }

    @Test
    fun narrowWindowsKeepTheTopBar() {
        val (w, h) = Settings().virtualSize(1024, 768)
        assertEquals(1240, w)
        assertEquals(930, h)
    }

    @Test
    fun brokenFileGivesDefaults() {
        assertEquals(Settings(), Settings.fromJson("{kaputt"))
        val s = Settings(fullscreen = true, crowd = 0.5, volume = 30)
        assertEquals(s, Settings.fromJson(s.toJson()))
    }
}
