package com.xadrezrpg.engine

import kotlinx.serialization.Serializable

@Serializable
sealed class Action {
    @Serializable
    data class PlaceArmy(val placements: List<Placement>) : Action()

    @Serializable
    data class Move(val pieceId: Int, val to: Pos) : Action()

    /** [target] é obrigatório para habilidades com [Ability.needsTarget]. */
    @Serializable
    data class UseAbility(val pieceId: Int, val ability: Ability, val target: Pos? = null) : Action()

    @Serializable
    data object EndTurn : Action()

    @Serializable
    data object Resign : Action()
}

class RuleException(message: String) : Exception(message)
