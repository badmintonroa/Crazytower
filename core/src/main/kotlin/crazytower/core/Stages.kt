package crazytower.core

/**
 * The tower stages, from the building site (0) to the CrazyTower (5). A stage is what the
 * building looks like at a star rating: stage 3 is the tower with three stars. Each one raises
 * the height limit, changes the lobby and unlocks new nonsense. What a star asks for lives in
 * [Rating].
 *
 * @param unlocks what the ticker announces when the stage is reached
 */
data class StageInfo(
    val number: Int,
    val name: Tr,
    val maxFloor: Int,
    val lobbyArt: String,
    val unlocks: Tr,
) {
    /** Everything the star of this stage asks for. */
    val requirement: Tr get() = Rating.requirement(number)

    /** Residents and employees needed to reach this stage. */
    val population: Int get() = Rating.population(number)
}

object Stages {
    val all = listOf(
        StageInfo(0, Tr("Baustelle", "Building Site"), maxFloor = 5,
            lobbyArt = "lobby_stage1_bruchbude", unlocks = Tr("", "")),
        StageInfo(1, Tr("Bruchbude", "Dump"), maxFloor = 10,
            lobbyArt = "lobby_stage1_bruchbude", unlocks = Tr("Neu: bis zur 10. Etage. Die Statik sagt vorsichtig ja.", "New: up to floor 10. The structural engineer says a careful yes.")),
        StageInfo(2, Tr("Hochhaus", "High-Rise"), maxFloor = 25,
            lobbyArt = "lobby_stage2_hochhaus",
            unlocks = Tr("Neu: der Kopierraum der Ewigkeit. Wer reingeht, kommt mit 400 Kopien wieder raus.", "New: the Copy Room of Eternity. Whoever goes in comes out with 400 copies.")),
        StageInfo(3, Tr("Wolkenkratzer", "Skyscraper"), maxFloor = 50,
            lobbyArt = "lobby_stage3_wolkenkratzer",
            unlocks = Tr("Neu: Sicherheitsdienst. Die Herren tragen Sonnenbrillen. Auch nachts.", "New: security. The gentlemen wear sunglasses. At night too.")),
        StageInfo(4, Tr("Mega-Tower", "Mega Tower"), maxFloor = 75,
            lobbyArt = "lobby_stage4_megatower",
            unlocks = Tr("Neu: Teleporter. Kommt meistens dort an, wo man hinwollte.", "New: teleporter. Usually arrives where you wanted to go.")),
        StageInfo(5, Tr("CrazyTower", "CrazyTower"), maxFloor = 100,
            lobbyArt = "lobby_stage5_crazytower",
            unlocks = Tr("Neu: Ufo-Landeplatz. Die Botschaft von Gliese 581 hat schon angefragt.", "New: UFO landing pad. The Embassy of Gliese 581 has already asked.")),
    )

    /** The highest stage. */
    val last: Int get() = all.last().number

    fun info(stage: Int): StageInfo = all[stage.coerceIn(0, all.lastIndex)]

    /** Whether [sim] fulfils everything needed for [stage], which is the same as that many stars. */
    fun reached(sim: Simulation, stage: Int): Boolean = Rating.stars(sim.ratingFacts) >= stage

    /** Progress towards the next stage, 0..1, counting only the population. */
    fun progress(sim: Simulation): Double = Rating.progress(sim.stage, sim.population)
}
