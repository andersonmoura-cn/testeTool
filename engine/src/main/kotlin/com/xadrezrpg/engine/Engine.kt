package com.xadrezrpg.engine

/**
 * Motor de regras. Todas as ações passam por [apply], que valida e devolve um novo estado.
 * É usado tanto pelo host (partida em rede) quanto pelo modo "mesmo aparelho".
 */
object Engine {

    fun newGame(): GameState = GameState()

    /** Aplica a ação; lança [RuleException] se ela for inválida. */
    fun apply(state: GameState, player: Int, action: Action): GameState {
        require(player == 0 || player == 1) { "Jogador inválido" }
        return when (action) {
            is Action.PlaceArmy -> placeArmy(state, player, action.placements)
            is Action.Resign -> resign(state, player)
            is Action.Move -> move(state, player, action)
            is Action.UseAbility -> useAbility(state, player, action)
            is Action.EndTurn -> {
                checkTurn(state, player)
                endTurn(state, "${playerName(player)} encerrou o turno.")
            }
        }
    }

    /** Versão que não lança exceção: devolve o novo estado ou a mensagem de erro. */
    fun tryApply(state: GameState, player: Int, action: Action): Pair<GameState?, String?> = try {
        apply(state, player, action) to null
    } catch (e: RuleException) {
        null to e.message
    }

    private fun fail(msg: String): Nothing = throw RuleException(msg)

    // ---------------- Preparação ----------------

    fun validateArmy(player: Int, placements: List<Placement>): String? {
        if (placements.isEmpty()) return "Escolha pelo menos 1 peça."
        if (placements.size > MAX_PIECES) return "Máximo de $MAX_PIECES peças."
        if (placements.map { it.pos }.toSet().size != placements.size) return "Duas peças na mesma casa."
        val rows = homeRows(player)
        if (placements.any { !it.pos.inBoard() || it.pos.y !in rows }) return "Peças só podem ficar nas suas 2 primeiras fileiras."
        return null
    }

    private fun placeArmy(s: GameState, player: Int, placements: List<Placement>): GameState {
        if (s.phase != Phase.SETUP) fail("A partida já começou.")
        if (s.placed[player]) fail("Exército já posicionado.")
        validateArmy(player, placements)?.let { fail(it) }
        var id = s.nextId
        val newPieces = placements.map { Piece(id = id++, type = it.type, owner = player, pos = it.pos) }
        val placed = s.placed.toMutableList().also { it[player] = true }
        val started = placed.all { it }
        return s.copy(
            pieces = s.pieces + newPieces,
            placed = placed,
            nextId = id,
            phase = if (started) Phase.PLAYING else Phase.SETUP,
            log = if (started) listOf("Partida iniciada! ${playerName(0)} começa.") else s.log,
        )
    }

    // ---------------- Turno ----------------

    private fun checkTurn(s: GameState, player: Int) {
        if (s.phase != Phase.PLAYING) fail("A partida não está em andamento.")
        if (s.currentPlayer != player) fail("Não é a sua vez.")
    }

    private fun checkPiece(s: GameState, player: Int, pieceId: Int): Piece {
        checkTurn(s, player)
        val p = s.piece(pieceId) ?: fail("Peça não encontrada.")
        if (p.owner != player) fail("Essa peça não é sua.")
        val active = s.activePieceId
        if (active != null && active != p.id) fail("Neste turno você só pode agir com a peça já escolhida.")
        if (active == null && s.isFrozen(p)) fail("Essa peça está congelada neste turno.")
        return p
    }

    private fun move(s: GameState, player: Int, a: Action.Move): GameState {
        val p = checkPiece(s, player, a.pieceId)
        if (s.moved) fail("Você já moveu neste turno.")
        if (a.to !in Rules.legalMoves(s, p)) fail("Movimento inválido.")
        val ns = s.copy(
            pieces = s.pieces.map { if (it.id == p.id) it.copy(pos = a.to) else it },
            activePieceId = p.id,
            moved = true,
        )
        return afterAction(ns, "${p.type.label} ${playerName(player)} moveu.")
    }

    private fun useAbility(s: GameState, player: Int, a: Action.UseAbility): GameState {
        val p = checkPiece(s, player, a.pieceId)
        if (s.abilityUsed) fail("Você já usou uma habilidade neste turno.")
        if (a.ability.owner != p.type) fail("Essa peça não tem essa habilidade.")
        if (a.ability.needsTarget) {
            val t = a.target ?: fail("Escolha um alvo.")
            if (t !in Rules.abilityTargets(s, p, a.ability)) fail("Alvo inválido.")
        }
        val w = World(s.copy(activePieceId = p.id, abilityUsed = true))
        w.log("${p.type.label} ${playerName(player)} usou ${a.ability.label}.")
        when (a.ability) {
            Ability.CONGELAMENTO -> {
                for (cell in Rules.abilityArea(s, p, a.ability, a.target)) {
                    val victim = w.at(cell) ?: continue
                    val frozenTurn = nextOwnTurn(s.turn, victim.owner)
                    w.update(victim.id) { it.copy(frozenTurn = frozenTurn) }
                    w.log("${victim.type.label} ${playerName(victim.owner)} foi congelado.")
                }
            }
            Ability.CORTE_GIRATORIO, Ability.CHUVA_DE_FLECHAS, Ability.CHOQUE_DO_TROVAO -> {
                for (cell in Rules.abilityArea(s, p, a.ability, a.target)) {
                    val victim = w.at(cell) ?: continue
                    w.damage(victim.id, 1, attackerId = p.id, singleTarget = false)
                }
            }
            Ability.FLECHA_PERFURANTE -> {
                val hits = Rules.abilityArea(s, p, a.ability, a.target)
                    .mapNotNull { s.pieceAt(it) }
                    .take(Rules.PIERCE_MAX_HITS)
                for (victim in hits) w.damage(victim.id, 1, attackerId = p.id, singleTarget = false)
            }
            Ability.GOLPE_DIVINO -> {
                val victim = s.pieceAt(a.target!!)!!
                w.damage(victim.id, 2, attackerId = p.id, singleTarget = true)
            }
            Ability.PROTEGER -> w.update(p.id) { it.copy(shieldMode = ShieldMode.PROTEGER) }
            Ability.REFLETIR -> w.update(p.id) { it.copy(shieldMode = ShieldMode.REFLETIR) }
        }
        return afterAction(w.state, null)
    }

    /** Próximo turno (estritamente depois de [turn]) em que [owner] joga. */
    fun nextOwnTurn(turn: Int, owner: Int): Int = if (turn % 2 == owner) turn + 2 else turn + 1

    private fun afterAction(s0: GameState, msg: String?): GameState {
        // O turno só passa quando o jogador aperta "Encerrar turno".
        val s = if (msg != null) s0.copy(log = s0.log + msg) else s0
        return checkVictory(s)
    }

    private fun checkVictory(s: GameState): GameState {
        val a0 = s.alive(0).isNotEmpty()
        val a1 = s.alive(1).isNotEmpty()
        return when {
            a0 && a1 -> s
            !a0 && !a1 -> s.copy(phase = Phase.FINISHED, draw = true, log = s.log + "Empate! Todos caíram.")
            else -> {
                val w = if (a0) 0 else 1
                s.copy(phase = Phase.FINISHED, winner = w, log = s.log + "${playerName(w)} venceu!")
            }
        }
    }

    private fun endTurn(s: GameState, msg: String?): GameState {
        val nextTurn = s.turn + 1
        val logs = s.log + listOfNotNull(msg) + "Vez de ${playerName(nextTurn % 2)}."
        return s.copy(
            turn = nextTurn,
            activePieceId = null,
            moved = false,
            abilityUsed = false,
            log = logs.takeLast(40),
        )
    }

    private fun resign(s: GameState, player: Int): GameState {
        if (s.phase == Phase.FINISHED) fail("A partida já terminou.")
        val w = 1 - player
        return s.copy(
            phase = Phase.FINISHED,
            winner = w,
            log = s.log + "${playerName(player)} desistiu. ${playerName(w)} venceu!",
        )
    }

    /** Estado mutável temporário para resolver uma habilidade. */
    private class World(var state: GameState) {
        fun at(p: Pos) = state.pieceAt(p)
        fun get(id: Int) = state.piece(id)

        fun log(msg: String) {
            state = state.copy(log = state.log + msg)
        }

        fun update(id: Int, f: (Piece) -> Piece) {
            state = state.copy(pieces = state.pieces.map { if (it.id == id) f(it) else it })
        }

        private fun applyRaw(id: Int, amount: Int) {
            val p = get(id) ?: return
            val hp = p.hp - amount
            if (hp <= 0) {
                state = state.copy(pieces = state.pieces.filter { it.id != id })
                log("${p.type.label} ${playerName(p.owner)} levou $amount de dano e caiu!")
            } else {
                update(id) { it.copy(hp = hp) }
                log("${p.type.label} ${playerName(p.owner)} levou $amount de dano ($hp de vida).")
            }
        }

        /**
         * Resolve dano aplicando as regras do escudeiro:
         * - REFLETIR: dano de alvo único vindo de vizinho volta para o atacante; o escudeiro não toma nada.
         * - PROTEGER: se o dano mataria um aliado vizinho (não escudeiro), o escudeiro absorve o dano.
         */
        fun damage(targetId: Int, amount: Int, attackerId: Int?, singleTarget: Boolean) {
            val target = get(targetId) ?: return
            val attacker = attackerId?.let { get(it) }

            if (target.type == PieceType.ESCUDEIRO && target.shieldMode == ShieldMode.REFLETIR &&
                singleTarget && attacker != null && attacker.id != target.id && attacker.pos.isNeighbor(target.pos)
            ) {
                log("Escudeiro ${playerName(target.owner)} refletiu o golpe!")
                // O dano refletido não é refletido de novo (evita ciclos).
                damage(attacker.id, amount, attackerId = null, singleTarget = false)
                return
            }

            if (target.type != PieceType.ESCUDEIRO && target.hp - amount <= 0) {
                val guard = state.pieces
                    .filter {
                        it.owner == target.owner && it.type == PieceType.ESCUDEIRO &&
                            it.shieldMode == ShieldMode.PROTEGER && it.pos.isNeighbor(target.pos)
                    }
                    .maxByOrNull { it.hp }
                if (guard != null) {
                    log("Escudeiro ${playerName(guard.owner)} protegeu ${target.type.label}!")
                    // Escudeiro não é protegido por outro escudeiro: dano direto.
                    applyRaw(guard.id, amount)
                    return
                }
            }
            applyRaw(target.id, amount)
        }
    }
}
