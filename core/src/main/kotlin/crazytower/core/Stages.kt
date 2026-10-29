package crazytower.core

/**
 * The tower stages, from the building site (0) to the CrazyTower (5). Each one raises the
 * height limit, changes the lobby and unlocks new nonsense.
 *
 * @param population residents and employees needed to reach this stage
 * @param unlocks what the ticker announces when the stage is reached
 */
data class StageInfo(
    val number: Int,
    val name: String,
    val maxFloor: Int,
    val requirement: String,
    val population: Int,
    val lobbyArt: String,
    val unlocks: String,
)

object Stages {
    val all = listOf(
        StageInfo(0, "Baustelle", maxFloor = 5, requirement = "Start", population = 0,
            lobbyArt = "lobby_stage1_bruchbude", unlocks = ""),
        StageInfo(1, "Bruchbude", maxFloor = 10, requirement = "60 Bewohner", population = 60,
            lobbyArt = "lobby_stage1_bruchbude", unlocks = "Neu: bis zur 10. Etage. Die Statik sagt vorsichtig ja."),
        StageInfo(2, "Hochhaus", maxFloor = 25, requirement = "300 Bewohner", population = 300,
            lobbyArt = "lobby_stage2_hochhaus",
            unlocks = "Neu: der Kopierraum der Ewigkeit. Wer reingeht, kommt mit 400 Kopien wieder raus."),
        StageInfo(3, "Wolkenkratzer", maxFloor = 50, requirement = "1.000 Bewohner, Laune 60 %", population = 1_000,
            lobbyArt = "lobby_stage3_wolkenkratzer",
            unlocks = "Neu: Sicherheitsdienst. Die Herren tragen Sonnenbrillen. Auch nachts."),
        StageInfo(4, "Mega-Tower", maxFloor = 75, requirement = "3.000 Bewohner, Sicherheitsdienst", population = 3_000,
            lobbyArt = "lobby_stage4_megatower",
            unlocks = "Neu: Teleporter. Kommt meistens dort an, wo man hinwollte."),
        StageInfo(5, "CrazyTower", maxFloor = 100, requirement = "6.000 Bewohner, VIP-Besuch überlebt", population = 6_000,
            lobbyArt = "lobby_stage5_crazytower",
            unlocks = "Neu: Ufo-Landeplatz. Die Botschaft von Gliese 581 hat schon angefragt."),
    )

    /** The highest stage. */
    val last: Int get() = all.last().number

    fun info(stage: Int): StageInfo = all[stage.coerceIn(0, all.lastIndex)]

    /** Whether [sim] fulfils everything needed for [stage]. */
    fun reached(sim: Simulation, stage: Int): Boolean {
        val info = info(stage)
        if (sim.population < info.population) return false
        return when (stage) {
            3 -> sim.satisfaction >= 60f
            4 -> sim.tower.allRooms.any { it.type.role == RoomRole.SECURITY }
            5 -> sim.vipSurvived
            else -> true
        }
    }

    /** Progress towards the next stage, 0..1, counting only the population. */
    fun progress(sim: Simulation): Double {
        if (sim.stage >= last) return 1.0
        val from = info(sim.stage).population
        val to = info(sim.stage + 1).population
        return ((sim.population - from).toDouble() / (to - from)).coerceIn(0.0, 1.0)
    }
}
