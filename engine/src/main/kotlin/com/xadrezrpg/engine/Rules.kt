package com.xadrezrpg.engine

/** Consultas puras sobre o estado: movimentos válidos, alvos e áreas de habilidades. */
object Rules {
    const val FREEZE_RANGE = 5
    const val ARROW_RAIN_RANGE = 3
    const val PIERCE_MAX_HITS = 2

    private val KNIGHT = listOf(
        Pos(1, 2), Pos(2, 1), Pos(-1, 2), Pos(-2, 1),
        Pos(1, -2), Pos(2, -1), Pos(-1, -2), Pos(-2, -1),
    )
    private val ORTHO = listOf(Pos(1, 0), Pos(-1, 0), Pos(0, 1), Pos(0, -1))
    private val DIAG = listOf(Pos(1, 1), Pos(-1, 1), Pos(1, -1), Pos(-1, -1))
    private val ALL_DIRS = ORTHO + DIAG

    fun allCells(): List<Pos> = (0 until BOARD_SIZE).flatMap { y -> (0 until BOARD_SIZE).map { x -> Pos(x, y) } }

    fun neighbors(p: Pos): List<Pos> = ALL_DIRS.map { p + it }.filter { it.inBoard() }

    private fun knightJumps(s: GameState, from: Pos): List<Pos> =
        KNIGHT.map { from + it }.filter { it.inBoard() && s.pieceAt(it) == null }

    private fun slide(s: GameState, from: Pos, dirs: List<Pos>): List<Pos> {
        val out = mutableListOf<Pos>()
        for (d in dirs) {
            var p = from + d
            while (p.inBoard() && s.pieceAt(p) == null) {
                out += p
                p += d
            }
        }
        return out
    }

    /** Casas para onde a peça pode se mover (somente casas vazias; não há captura). */
    fun legalMoves(s: GameState, piece: Piece): List<Pos> = when (piece.type) {
        PieceType.CONJURADOR -> knightJumps(s, piece.pos)
        PieceType.ATIRADOR -> slide(s, piece.pos, DIAG)
        PieceType.ESPADACHIM -> slide(s, piece.pos, ORTHO)
        PieceType.ESCUDEIRO -> neighbors(piece.pos).filter { s.pieceAt(it) == null }
    }

    /** Casas que podem ser escolhidas como alvo da habilidade. Vazio para habilidades sem alvo. */
    fun abilityTargets(s: GameState, piece: Piece, ability: Ability): List<Pos> = when (ability) {
        Ability.TELEPORTE -> knightJumps(s, piece.pos)
        Ability.CONGELAMENTO -> allCells().filter { it.dist(piece.pos) <= FREEZE_RANGE }
        Ability.GOLPE_DIVINO -> neighbors(piece.pos).filter { s.pieceAt(it) != null }
        Ability.CHUVA_DE_FLECHAS -> allCells().filter {
            it.dist(piece.pos) <= ARROW_RAIN_RANGE && it.x <= BOARD_SIZE - 2 && it.y <= BOARD_SIZE - 2
        }
        Ability.FLECHA_PERFURANTE -> allCells().filter {
            it != piece.pos && (it.x == piece.pos.x || it.y == piece.pos.y)
        }
        Ability.CORTE_GIRATORIO, Ability.PROTEGER, Ability.REFLETIR -> emptyList()
    }

    /** Casas afetadas pela habilidade (para pré-visualização e resolução). */
    fun abilityArea(s: GameState, piece: Piece, ability: Ability, target: Pos?): List<Pos> = when (ability) {
        Ability.TELEPORTE -> listOfNotNull(target)
        Ability.CONGELAMENTO -> target?.let { t ->
            (-1..1).flatMap { dy -> (-1..1).map { dx -> Pos(t.x + dx, t.y + dy) } }.filter { it.inBoard() }
        } ?: emptyList()
        Ability.CORTE_GIRATORIO -> neighbors(piece.pos)
        Ability.GOLPE_DIVINO -> listOfNotNull(target)
        Ability.CHUVA_DE_FLECHAS -> target?.let { t ->
            listOf(t, Pos(t.x + 1, t.y), Pos(t.x, t.y + 1), Pos(t.x + 1, t.y + 1)).filter { it.inBoard() }
        } ?: emptyList()
        Ability.FLECHA_PERFURANTE -> target?.let { pierceLine(piece.pos, it) } ?: emptyList()
        Ability.PROTEGER, Ability.REFLETIR -> listOf(piece.pos)
    }

    /** Linha reta inteira partindo do atirador na direção do alvo. */
    fun pierceLine(from: Pos, target: Pos): List<Pos> {
        if (from == target || (from.x != target.x && from.y != target.y)) return emptyList()
        val d = Pos(Integer.signum(target.x - from.x), Integer.signum(target.y - from.y))
        val out = mutableListOf<Pos>()
        var p = from + d
        while (p.inBoard()) {
            out += p
            p += d
        }
        return out
    }

    /** O jogador pode escolher esta peça para agir agora? */
    fun canAct(s: GameState, player: Int, piece: Piece): Boolean {
        if (s.phase != Phase.PLAYING || s.currentPlayer != player || piece.owner != player) return false
        val active = s.activePieceId
        if (active != null) return active == piece.id
        return !s.isFrozen(piece)
    }
}
