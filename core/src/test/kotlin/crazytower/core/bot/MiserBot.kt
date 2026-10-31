package crazytower.core.bot

import crazytower.core.Simulation

/**
 * Geizhals: builds like the express-stairs bot but never buys the optional extras (upgrades,
 * tuning, insurance, ads, party, decorations, side entrances, research). Only the mission costs
 * money. Shows whether the extras are worth it and whether the mission can be won without them.
 */
class MiserBot(sim: Simulation) : HubBot(sim) {
    override val id = "miser"
    override val title = "Geizhals"
    override val idea = "baut wie Express-Treppen, kauft aber keine Extras, nur was die Mondmission braucht"
    override val spends = false
}
