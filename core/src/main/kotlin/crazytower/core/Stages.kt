package crazytower.core

/** The five tower stages. Each one raises the height limit and unlocks new nonsense. */
data class StageInfo(val number: Int, val name: String, val maxFloor: Int, val requirement: String)

object Stages {
    val all = listOf(
        StageInfo(1, "Bruchbude", maxFloor = 10, requirement = "Start"),
        StageInfo(2, "Hochhaus", maxFloor = 25, requirement = "300 Bewohner"),
        StageInfo(3, "Wolkenkratzer", maxFloor = 50, requirement = "1.000 Bewohner, Zufriedenheit 60 %"),
        StageInfo(4, "Mega-Tower", maxFloor = 75, requirement = "3.000 Bewohner, Sicherheitsdienst"),
        StageInfo(5, "CrazyTower", maxFloor = 100, requirement = "6.000 Bewohner, VIP-Besuch überlebt"),
    )

    fun info(stage: Int): StageInfo = all[(stage - 1).coerceIn(0, all.lastIndex)]
}
