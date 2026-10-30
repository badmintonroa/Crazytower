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
    val name: Tr,
    val maxFloor: Int,
    val requirement: Tr,
    val population: Int,
    val lobbyArt: String,
    val unlocks: Tr,
)

object Stages {
    val all = listOf(
        StageInfo(0, Tr("Baustelle", "Building Site"), maxFloor = 5, requirement = Tr("Start", "Start"), population = 0,
            lobbyArt = "lobby_stage1_bruchbude", unlocks = Tr("", "")),
        StageInfo(1, Tr("Bruchbude", "Dump"), maxFloor = 10, requirement = Tr("60 Bewohner", "60 residents"), population = 60,
            lobbyArt = "lobby_stage1_bruchbude", unlocks = Tr("Neu: bis zur 10. Etage. Die Statik sagt vorsichtig ja.", "New: up to floor 10. The structural engineer says a careful yes.")),
        StageInfo(2, Tr("Hochhaus", "High-Rise"), maxFloor = 25, requirement = Tr("300 Bewohner", "300 residents"), population = 300,
            lobbyArt = "lobby_stage2_hochhaus",
            unlocks = Tr("Neu: der Kopierraum der Ewigkeit. Wer reingeht, kommt mit 400 Kopien wieder raus.", "New: the Copy Room of Eternity. Whoever goes in comes out with 400 copies.")),
        StageInfo(3, Tr("Wolkenkratzer", "Skyscraper"), maxFloor = 50, requirement = Tr("1.000 Bewohner, Laune ${STAGE3_MOOD.toInt()} %", "1,000 residents, ${STAGE3_MOOD.toInt()} % mood"), population = 1_000,
            lobbyArt = "lobby_stage3_wolkenkratzer",
            unlocks = Tr("Neu: Sicherheitsdienst. Die Herren tragen Sonnenbrillen. Auch nachts.", "New: security. The gentlemen wear sunglasses. At night too.")),
        StageInfo(4, Tr("Mega-Tower", "Mega Tower"), maxFloor = 75, requirement = Tr("3.000 Bewohner, Sicherheitsdienst", "3,000 residents, security"), population = 3_000,
            lobbyArt = "lobby_stage4_megatower",
            unlocks = Tr("Neu: Teleporter. Kommt meistens dort an, wo man hinwollte.", "New: teleporter. Usually arrives where you wanted to go.")),
        StageInfo(5, Tr("CrazyTower", "CrazyTower"), maxFloor = 100, requirement = Tr("6.000 Bewohner, VIP-Besuch überlebt", "6,000 residents, a happy VIP"), population = 6_000,
            lobbyArt = "lobby_stage5_crazytower",
            unlocks = Tr("Neu: Ufo-Landeplatz. Die Botschaft von Gliese 581 hat schon angefragt.", "New: UFO landing pad. The Embassy of Gliese 581 has already asked.")),
    )

    /** The mood stage 3 asks for. */
    const val STAGE3_MOOD = 65f

    /** The highest stage. */
    val last: Int get() = all.last().number

    fun info(stage: Int): StageInfo = all[stage.coerceIn(0, all.lastIndex)]

    /** Whether [sim] fulfils everything needed for [stage]. */
    fun reached(sim: Simulation, stage: Int): Boolean {
        val info = info(stage)
        if (sim.population < info.population) return false
        return when (stage) {
            3 -> sim.satisfaction >= STAGE3_MOOD
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
