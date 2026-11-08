package com.xadrezrpg.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Joga milhares de partidas com ações aleatórias válidas e confere as invariantes do jogo. */
class RandomGamesTest {

    private fun randomArmy(rnd: Random, player: Int): List<Placement> {
        val cells = homeRows(player).flatMap { y -> (0 until BOARD_SIZE).map { Pos(it, y) } }.shuffled(rnd)
        val n = rnd.nextInt(1, MAX_PIECES + 1)
        return cells.take(n).map { Placement(PieceType.entries.random(rnd), it) }
    }

    private fun legalActions(s: GameState): List<Action> {
        val me = s.currentPlayer
        val out = mutableListOf<Action>(Action.EndTurn)
        for (p in s.alive(me)) {
            if (!Rules.canAct(s, me, p)) continue
            if (!s.moved) Rules.legalMoves(s, p).forEach { out += Action.Move(p.id, it) }
            if (!s.abilityUsed) for (a in p.type.abilities) {
                if (a.needsTarget) Rules.abilityTargets(s, p, a).forEach { out += Action.UseAbility(p.id, a, it) }
                else out += Action.UseAbility(p.id, a)
            }
        }
        return out
    }

    private fun checkInvariants(s: GameState) {
        assertEquals(s.pieces.size, s.pieces.map { it.pos }.toSet().size, "duas peças na mesma casa")
        for (p in s.pieces) {
            assertTrue(p.pos.inBoard(), "peça fora do tabuleiro")
            assertTrue(p.hp in 1..p.type.maxHp, "vida inválida: $p")
        }
        if (s.phase == Phase.PLAYING) {
            assertTrue(s.alive(0).isNotEmpty() && s.alive(1).isNotEmpty(), "jogo deveria ter acabado")
        }
    }

    @Test
    fun thousandsOfRandomGamesKeepInvariants() {
        val rnd = Random(42)
        var finished = 0
        repeat(2000) { game ->
            var s = Engine.newGame()
            s = Engine.apply(s, 0, Action.PlaceArmy(randomArmy(rnd, 0)))
            s = Engine.apply(s, 1, Action.PlaceArmy(randomArmy(rnd, 1)))
            var steps = 0
            while (s.phase == Phase.PLAYING && steps < 600) {
                // Prefere ações a encerrar turno, para a partida avançar.
                val acts = legalActions(s)
                val a = if (acts.size > 1 && rnd.nextInt(10) > 0) acts.drop(1).random(rnd) else acts.random(rnd)
                val before = s
                s = try {
                    Engine.apply(s, s.currentPlayer, a)
                } catch (e: RuleException) {
                    fail("Ação listada como válida foi recusada (partida $game): $a -> ${e.message}")
                }
                checkInvariants(s)
                // Total de vida nunca aumenta
                assertTrue(s.pieces.sumOf { it.hp } <= before.pieces.sumOf { it.hp })
                steps++
            }
            if (s.phase == Phase.FINISHED) finished++
        }
        assertTrue(finished > 1000, "poucas partidas terminaram: $finished")
    }
}
