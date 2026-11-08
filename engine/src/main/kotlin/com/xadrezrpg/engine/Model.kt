package com.xadrezrpg.engine

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max

const val BOARD_SIZE = 8
const val MAX_PIECES = 8

@Serializable
data class Pos(val x: Int, val y: Int) {
    fun inBoard(): Boolean = x in 0 until BOARD_SIZE && y in 0 until BOARD_SIZE

    /** Distância de "rei" (Chebyshev): conta casas em qualquer direção, incluindo diagonal. */
    fun dist(o: Pos): Int = max(abs(x - o.x), abs(y - o.y))

    fun isNeighbor(o: Pos): Boolean = this != o && dist(o) == 1

    operator fun plus(d: Pos) = Pos(x + d.x, y + d.y)
}

@Serializable
enum class PieceType(val label: String, val symbol: String, val maxHp: Int) {
    CONJURADOR("Conjurador", "C", 1),
    ATIRADOR("Atirador", "A", 1),
    ESPADACHIM("Espadachim", "E", 2),
    ESCUDEIRO("Escudeiro", "S", 3);

    val abilities: List<Ability> get() = Ability.entries.filter { it.owner == this }
}

@Serializable
enum class Ability(val owner: PieceType, val label: String, val description: String) {
    CHOQUE_DO_TROVAO(PieceType.CONJURADOR, "Choque do trovão", "1 de dano em X (centro + 4 diagonais vizinhas), a até 3 casas."),
    CONGELAMENTO(PieceType.CONJURADOR, "Congelamento", "Área 3x3 a até 5 casas. Peças atingidas ficam paradas no próximo turno do dono. Sem dano."),
    CORTE_GIRATORIO(PieceType.ESPADACHIM, "Corte giratório", "1 de dano em todas as peças vizinhas (inclusive aliadas)."),
    GOLPE_DIVINO(PieceType.ESPADACHIM, "Golpe divino", "2 de dano em um alvo vizinho."),
    CHUVA_DE_FLECHAS(PieceType.ATIRADOR, "Chuva de flechas", "1 de dano em área 2x2 a até 3 casas (inclusive aliadas)."),
    FLECHA_PERFURANTE(PieceType.ATIRADOR, "Flecha perfurante", "1 de dano em linha reta (vertical/horizontal), atravessando até 2 peças."),
    PROTEGER(PieceType.ESCUDEIRO, "Proteger", "Modo contínuo: toma o dano fatal de aliados vizinhos (exceto outros escudeiros)."),
    REFLETIR(PieceType.ESCUDEIRO, "Refletir", "Modo contínuo: devolve dano de alvo único vindo de peça vizinha e não toma nada.");

    /** Habilidade que precisa de uma casa alvo escolhida no tabuleiro. */
    val needsTarget: Boolean
        get() = this == CHOQUE_DO_TROVAO || this == CONGELAMENTO || this == GOLPE_DIVINO ||
            this == CHUVA_DE_FLECHAS || this == FLECHA_PERFURANTE
}

@Serializable
enum class ShieldMode { NENHUM, PROTEGER, REFLETIR }

@Serializable
data class Piece(
    val id: Int,
    val type: PieceType,
    val owner: Int,
    val pos: Pos,
    val hp: Int = type.maxHp,
    /** Número do turno em que esta peça está congelada (não pode ser escolhida). -1 = livre. */
    val frozenTurn: Int = -1,
    val shieldMode: ShieldMode = ShieldMode.NENHUM,
)

@Serializable
enum class Phase { SETUP, PLAYING, FINISHED }

@Serializable
data class Placement(val type: PieceType, val pos: Pos)

@Serializable
data class GameState(
    val phase: Phase = Phase.SETUP,
    val pieces: List<Piece> = emptyList(),
    /** Exército de cada jogador já posicionado? */
    val placed: List<Boolean> = listOf(false, false),
    val turn: Int = 0,
    /** Peça escolhida neste turno (travada após a primeira ação). */
    val activePieceId: Int? = null,
    val moved: Boolean = false,
    val abilityUsed: Boolean = false,
    /** Vencedor (0 ou 1). null = sem vencedor (em andamento ou empate). */
    val winner: Int? = null,
    val draw: Boolean = false,
    val log: List<String> = emptyList(),
    val nextId: Int = 1,
) {
    val currentPlayer: Int get() = turn % 2

    fun pieceAt(p: Pos): Piece? = pieces.firstOrNull { it.pos == p }
    fun piece(id: Int): Piece? = pieces.firstOrNull { it.id == id }
    fun alive(player: Int): List<Piece> = pieces.filter { it.owner == player }

    fun isFrozen(p: Piece): Boolean = p.frozenTurn == turn
}

/** Fileiras iniciais permitidas para cada jogador. */
fun homeRows(player: Int): IntRange = if (player == 0) 0..1 else (BOARD_SIZE - 2) until BOARD_SIZE

fun playerName(player: Int) = if (player == 0) "Azul" else "Vermelho"
