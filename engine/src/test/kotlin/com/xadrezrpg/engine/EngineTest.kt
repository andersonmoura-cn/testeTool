package com.xadrezrpg.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineTest {

    /** Monta um estado em jogo diretamente com as peças dadas. */
    private fun game(vararg pieces: Piece, turn: Int = 0) = GameState(
        phase = Phase.PLAYING,
        pieces = pieces.toList(),
        placed = listOf(true, true),
        turn = turn,
        nextId = 100,
    )

    private fun p(id: Int, type: PieceType, owner: Int, x: Int, y: Int, mode: ShieldMode = ShieldMode.NENHUM) =
        Piece(id = id, type = type, owner = owner, pos = Pos(x, y), shieldMode = mode)

    @Test
    fun setupStartsGameWhenBothPlaced() {
        var s = Engine.newGame()
        s = Engine.apply(s, 0, Action.PlaceArmy(listOf(Placement(PieceType.ESPADACHIM, Pos(0, 0)))))
        assertEquals(Phase.SETUP, s.phase)
        s = Engine.apply(s, 1, Action.PlaceArmy(listOf(Placement(PieceType.ATIRADOR, Pos(3, 7)))))
        assertEquals(Phase.PLAYING, s.phase)
        assertEquals(2, s.pieces.size)
        assertEquals(2, s.pieces.find { it.type == PieceType.ESPADACHIM }!!.hp)
    }

    @Test
    fun setupRejectsInvalidArmies() {
        val s = Engine.newGame()
        assertFailsWith<RuleException> {
            Engine.apply(s, 0, Action.PlaceArmy(listOf(Placement(PieceType.ESPADACHIM, Pos(0, 3)))))
        }
        val nine = (0 until 9).map { Placement(PieceType.ESCUDEIRO, Pos(it % 8, it / 8)) }
        assertFailsWith<RuleException> { Engine.apply(s, 0, Action.PlaceArmy(nine)) }
        assertFailsWith<RuleException> {
            Engine.apply(s, 1, Action.PlaceArmy(listOf(Placement(PieceType.ESPADACHIM, Pos(0, 0)))))
        }
    }

    @Test
    fun movementPatterns() {
        val s = game(
            p(1, PieceType.ESPADACHIM, 0, 0, 0),
            p(2, PieceType.ATIRADOR, 0, 3, 3),
            p(3, PieceType.ESCUDEIRO, 0, 7, 7),
            p(4, PieceType.CONJURADOR, 1, 0, 3),
        )
        // Torre: bloqueada pela peça em (0,3)
        val rook = Rules.legalMoves(s, s.piece(1)!!)
        assertTrue(Pos(0, 2) in rook && Pos(0, 3) !in rook && Pos(7, 0) in rook)
        // Bispo
        val bishop = Rules.legalMoves(s, s.piece(2)!!)
        assertTrue(Pos(0, 0) !in bishop) // ocupado
        assertTrue(Pos(1, 1) in bishop && Pos(6, 6) in bishop && Pos(3, 4) !in bishop)
        // Escudeiro: 1 casa
        assertEquals(setOf(Pos(6, 6), Pos(6, 7), Pos(7, 6)), Rules.legalMoves(s, s.piece(3)!!).toSet())
        // Conjurador: salto em L
        assertTrue(Pos(1, 5) in Rules.legalMoves(s, s.piece(4)!!))
        assertTrue(Pos(1, 3) !in Rules.legalMoves(s, s.piece(4)!!))
    }

    @Test
    fun moveThenAbilityWithSamePieceTurnOnlyEndsOnButton() {
        var s = game(p(1, PieceType.ESPADACHIM, 0, 0, 0), p(2, PieceType.ESCUDEIRO, 1, 2, 2))
        s = Engine.apply(s, 0, Action.Move(1, Pos(1, 0)))
        assertEquals(0, s.currentPlayer)
        // Segundo movimento no mesmo turno não é permitido
        assertFailsWith<RuleException> { Engine.apply(s, 0, Action.Move(1, Pos(1, 1))) }
        s = Engine.apply(s, 0, Action.UseAbility(1, Ability.CORTE_GIRATORIO))
        // Turno NÃO passa sozinho
        assertEquals(0, s.currentPlayer)
        assertFailsWith<RuleException> { Engine.apply(s, 0, Action.UseAbility(1, Ability.CORTE_GIRATORIO)) }
        s = Engine.apply(s, 0, Action.EndTurn)
        assertEquals(1, s.currentPlayer)
        assertNull(s.activePieceId)
    }

    @Test
    fun everyClassCanMoveThenUseAbility() {
        for (type in PieceType.entries) {
            var s = game(p(1, type, 0, 3, 3), p(2, PieceType.ESCUDEIRO, 1, 7, 7), p(3, PieceType.ESCUDEIRO, 1, 0, 7))
            val to = Rules.legalMoves(s, s.piece(1)!!).first()
            s = Engine.apply(s, 0, Action.Move(1, to))
            val a = type.abilities.first { a -> !a.needsTarget || Rules.abilityTargets(s, s.piece(1)!!, a).isNotEmpty() }
            val target = if (a.needsTarget) Rules.abilityTargets(s, s.piece(1)!!, a).first() else null
            s = Engine.apply(s, 0, Action.UseAbility(1, a, target))
            assertTrue(s.moved && s.abilityUsed, "falhou para $type")
            assertEquals(0, s.currentPlayer)
        }
    }

    @Test
    fun abilityThenMoveIsAllowed() {
        var s = game(p(1, PieceType.ESPADACHIM, 0, 0, 0), p(2, PieceType.ESCUDEIRO, 1, 5, 5))
        s = Engine.apply(s, 0, Action.UseAbility(1, Ability.CORTE_GIRATORIO))
        assertEquals(0, s.currentPlayer)
        s = Engine.apply(s, 0, Action.Move(1, Pos(0, 4)))
        assertEquals(Pos(0, 4), s.piece(1)!!.pos)
        assertEquals(0, s.currentPlayer)
    }

    @Test
    fun cannotUseOtherPieceAfterActing() {
        var s = game(
            p(1, PieceType.ESPADACHIM, 0, 0, 0),
            p(2, PieceType.ATIRADOR, 0, 5, 0),
            p(3, PieceType.ESCUDEIRO, 1, 7, 7),
        )
        s = Engine.apply(s, 0, Action.Move(1, Pos(0, 3)))
        assertFailsWith<RuleException> { Engine.apply(s, 0, Action.UseAbility(2, Ability.CHUVA_DE_FLECHAS, Pos(5, 2))) }
    }

    @Test
    fun spinHitsAlliesAndEnemies() {
        var s = game(
            p(1, PieceType.ESPADACHIM, 0, 3, 3),
            p(2, PieceType.ATIRADOR, 0, 3, 4), // aliado, 1 vida
            p(3, PieceType.ESPADACHIM, 1, 4, 4), // inimigo, 2 vidas
            p(4, PieceType.ESCUDEIRO, 1, 7, 7),
        )
        s = Engine.apply(s, 0, Action.UseAbility(1, Ability.CORTE_GIRATORIO))
        assertNull(s.piece(2))
        assertEquals(1, s.piece(3)!!.hp)
    }

    @Test
    fun divineStrikeDealsTwo() {
        var s = game(p(1, PieceType.ESPADACHIM, 0, 3, 3), p(2, PieceType.ESCUDEIRO, 1, 4, 4))
        s = Engine.apply(s, 0, Action.UseAbility(1, Ability.GOLPE_DIVINO, Pos(4, 4)))
        assertEquals(1, s.piece(2)!!.hp)
        assertFailsWith<RuleException> {
            Engine.apply(game(p(1, PieceType.ESPADACHIM, 0, 0, 0), p(2, PieceType.ESCUDEIRO, 1, 4, 4)), 0,
                Action.UseAbility(1, Ability.GOLPE_DIVINO, Pos(4, 4)))
        }
    }

    @Test
    fun arrowRainArea2x2WithinRange3() {
        val base = game(
            p(1, PieceType.ATIRADOR, 0, 0, 0),
            p(2, PieceType.ESPADACHIM, 1, 3, 3),
            p(3, PieceType.ESPADACHIM, 1, 4, 4),
            p(4, PieceType.ESPADACHIM, 1, 5, 5),
        )
        val s = Engine.apply(base, 0, Action.UseAbility(1, Ability.CHUVA_DE_FLECHAS, Pos(3, 3)))
        assertEquals(1, s.piece(2)!!.hp)
        assertEquals(1, s.piece(3)!!.hp)
        assertEquals(2, s.piece(4)!!.hp)
        assertFailsWith<RuleException> {
            Engine.apply(base, 0, Action.UseAbility(1, Ability.CHUVA_DE_FLECHAS, Pos(4, 4)))
        }
    }

    @Test
    fun piercingArrowHitsFirstTwo() {
        var s = game(
            p(1, PieceType.ATIRADOR, 0, 0, 0),
            p(2, PieceType.ESPADACHIM, 1, 0, 2),
            p(3, PieceType.ATIRADOR, 0, 0, 4), // aliado também é atingido
            p(4, PieceType.ESPADACHIM, 1, 0, 6), // terceiro: não atingido
        )
        s = Engine.apply(s, 0, Action.UseAbility(1, Ability.FLECHA_PERFURANTE, Pos(0, 1)))
        assertEquals(1, s.piece(2)!!.hp)
        assertNull(s.piece(3))
        assertEquals(2, s.piece(4)!!.hp)
    }

    @Test
    fun freezeBlocksPiecesOnOwnersNextTurn() {
        var s = game(
            p(1, PieceType.CONJURADOR, 0, 0, 0),
            p(2, PieceType.ESPADACHIM, 1, 4, 4),
            p(3, PieceType.ESCUDEIRO, 1, 7, 7),
        )
        s = Engine.apply(s, 0, Action.UseAbility(1, Ability.CONGELAMENTO, Pos(4, 4)))
        s = Engine.apply(s, 0, Action.EndTurn)
        assertEquals(1, s.currentPlayer)
        assertEquals(2, s.piece(2)!!.hp) // sem dano
        assertFailsWith<RuleException> { Engine.apply(s, 1, Action.Move(2, Pos(4, 5))) }
        // Outra peça pode agir
        s = Engine.apply(s, 1, Action.Move(3, Pos(6, 6)))
        s = Engine.apply(s, 1, Action.EndTurn)
        s = Engine.apply(s, 0, Action.EndTurn)
        // Turno seguinte do jogador 1: descongelado
        s = Engine.apply(s, 1, Action.Move(2, Pos(4, 5)))
        assertEquals(Pos(4, 5), s.piece(2)!!.pos)
    }

    @Test
    fun freezeOutOfRangeRejected() {
        val s = game(p(1, PieceType.CONJURADOR, 0, 0, 0), p(2, PieceType.ESCUDEIRO, 1, 7, 7))
        assertFailsWith<RuleException> { Engine.apply(s, 0, Action.UseAbility(1, Ability.CONGELAMENTO, Pos(6, 6))) }
    }

    @Test
    fun thunderHitsXShapeIncludingAllies() {
        val base = game(
            p(1, PieceType.CONJURADOR, 0, 0, 0),
            p(2, PieceType.ESPADACHIM, 1, 3, 3), // centro
            p(3, PieceType.ESCUDEIRO, 1, 2, 2), // diagonal
            p(4, PieceType.ATIRADOR, 0, 4, 2), // diagonal, aliado: cai
            p(5, PieceType.ESPADACHIM, 1, 3, 2), // ortogonal: não atingido
        )
        val prev = Rules.previewDamage(base, base.piece(1)!!, Ability.CHOQUE_DO_TROVAO, Pos(3, 3))
        assertEquals(mapOf(Pos(3, 3) to 1, Pos(2, 2) to 1, Pos(4, 2) to 1), prev)
        val s = Engine.apply(base, 0, Action.UseAbility(1, Ability.CHOQUE_DO_TROVAO, Pos(3, 3)))
        assertEquals(1, s.piece(2)!!.hp)
        assertEquals(2, s.piece(3)!!.hp)
        assertNull(s.piece(4))
        assertEquals(2, s.piece(5)!!.hp)
        assertFailsWith<RuleException> {
            Engine.apply(base, 0, Action.UseAbility(1, Ability.CHOQUE_DO_TROVAO, Pos(4, 4)))
        }
    }

    @Test
    fun pierceAreaStopsAtSecondPiece() {
        val s = game(
            p(1, PieceType.ATIRADOR, 0, 0, 0),
            p(2, PieceType.ESPADACHIM, 1, 0, 2),
            p(3, PieceType.ESPADACHIM, 1, 0, 4),
            p(4, PieceType.ESPADACHIM, 1, 0, 6),
        )
        val area = Rules.abilityArea(s, s.piece(1)!!, Ability.FLECHA_PERFURANTE, Pos(0, 5))
        assertEquals((1..4).map { Pos(0, it) }, area)
    }

    @Test
    fun protectedPiecesAreDetected() {
        val s = game(
            p(1, PieceType.ATIRADOR, 0, 0, 0),
            p(2, PieceType.ESCUDEIRO, 0, 1, 1, ShieldMode.PROTEGER),
            p(3, PieceType.ESCUDEIRO, 0, 2, 2),
            p(4, PieceType.ATIRADOR, 0, 5, 5),
        )
        assertTrue(Rules.isProtected(s, s.piece(1)!!))
        assertTrue(!Rules.isProtected(s, s.piece(3)!!)) // escudeiro não é protegido
        assertTrue(!Rules.isProtected(s, s.piece(4)!!))
    }

    @Test
    fun guardAbsorbsLethalDamageForNeighbor() {
        var s = game(
            p(1, PieceType.ESPADACHIM, 0, 3, 3),
            p(2, PieceType.ATIRADOR, 1, 4, 4),
            p(3, PieceType.ESCUDEIRO, 1, 5, 5, ShieldMode.PROTEGER),
        )
        s = Engine.apply(s, 0, Action.UseAbility(1, Ability.GOLPE_DIVINO, Pos(4, 4)))
        assertEquals(1, s.piece(2)!!.hp) // atirador salvo
        assertEquals(1, s.piece(3)!!.hp) // escudeiro tomou 2
    }

    @Test
    fun guardIgnoresNonLethalDamage() {
        var s = game(
            p(1, PieceType.ATIRADOR, 0, 3, 0),
            p(2, PieceType.ESPADACHIM, 1, 3, 4),
            p(3, PieceType.ESCUDEIRO, 1, 4, 4, ShieldMode.PROTEGER),
        )
        s = Engine.apply(s, 0, Action.UseAbility(1, Ability.FLECHA_PERFURANTE, Pos(3, 1)))
        assertEquals(1, s.piece(2)!!.hp)
        assertEquals(3, s.piece(3)!!.hp)
    }

    @Test
    fun guardDoesNotProtectOtherGuardsNoChain() {
        var s = game(
            p(1, PieceType.ESPADACHIM, 0, 3, 3),
            p(2, PieceType.ESCUDEIRO, 1, 4, 4, ShieldMode.PROTEGER),
            p(3, PieceType.ESCUDEIRO, 1, 5, 5, ShieldMode.PROTEGER),
        )
        s = s.copy(pieces = s.pieces.map { if (it.id == 2) it.copy(hp = 1) else it })
        s = Engine.apply(s, 0, Action.UseAbility(1, Ability.GOLPE_DIVINO, Pos(4, 4)))
        assertNull(s.piece(2)) // morreu: não foi protegido pelo escudeiro vizinho
        assertEquals(3, s.piece(3)!!.hp)
    }

    @Test
    fun reflectReturnsSingleTargetDamage() {
        var s = game(
            p(1, PieceType.ESPADACHIM, 0, 3, 3),
            p(2, PieceType.ESCUDEIRO, 1, 4, 4, ShieldMode.REFLETIR),
            p(3, PieceType.ATIRADOR, 0, 0, 7),
        )
        s = Engine.apply(s, 0, Action.UseAbility(1, Ability.GOLPE_DIVINO, Pos(4, 4)))
        assertEquals(3, s.piece(2)!!.hp)
        assertNull(s.piece(1)) // espadachim tomou 2 e caiu
        assertEquals(0, s.currentPlayer) // turno só passa no botão
        assertEquals(1, Engine.apply(s, 0, Action.EndTurn).currentPlayer)
    }

    @Test
    fun reflectDoesNotAffectAreaDamage() {
        var s = game(
            p(1, PieceType.ESPADACHIM, 0, 3, 3),
            p(2, PieceType.ESCUDEIRO, 1, 4, 4, ShieldMode.REFLETIR),
        )
        s = Engine.apply(s, 0, Action.UseAbility(1, Ability.CORTE_GIRATORIO))
        assertEquals(2, s.piece(2)!!.hp)
        assertEquals(2, s.piece(1)!!.hp)
    }

    @Test
    fun shieldModePersistsAcrossTurns() {
        var s = game(p(1, PieceType.ESCUDEIRO, 0, 0, 0), p(2, PieceType.ESCUDEIRO, 1, 7, 7))
        s = Engine.apply(s, 0, Action.UseAbility(1, Ability.PROTEGER))
        s = Engine.apply(s, 0, Action.EndTurn)
        s = Engine.apply(s, 1, Action.EndTurn)
        assertEquals(ShieldMode.PROTEGER, s.piece(1)!!.shieldMode)
        s = Engine.apply(s, 0, Action.UseAbility(1, Ability.REFLETIR))
        assertEquals(ShieldMode.REFLETIR, s.piece(1)!!.shieldMode)
    }

    @Test
    fun victoryWhenAllEnemyPiecesDie() {
        var s = game(p(1, PieceType.ESPADACHIM, 0, 3, 3), p(2, PieceType.CONJURADOR, 1, 4, 4))
        s = Engine.apply(s, 0, Action.UseAbility(1, Ability.GOLPE_DIVINO, Pos(4, 4)))
        assertEquals(Phase.FINISHED, s.phase)
        assertEquals(0, s.winner)
    }

    @Test
    fun resignGivesVictoryToOpponent() {
        val s = Engine.apply(game(p(1, PieceType.ESPADACHIM, 0, 0, 0), p(2, PieceType.ESPADACHIM, 1, 7, 7)), 1, Action.Resign)
        assertEquals(0, s.winner)
    }

    @Test
    fun notYourTurn() {
        val s = game(p(1, PieceType.ESPADACHIM, 0, 0, 0), p(2, PieceType.ESPADACHIM, 1, 7, 7))
        assertFailsWith<RuleException> { Engine.apply(s, 1, Action.Move(2, Pos(7, 6))) }
    }

    @Test
    fun protocolRoundTrip() {
        val s = game(p(1, PieceType.ESCUDEIRO, 0, 0, 0, ShieldMode.REFLETIR), p(2, PieceType.ESPADACHIM, 1, 7, 7))
        val msgs = listOf(
            NetMessage.Welcome(1),
            NetMessage.State(s),
            NetMessage.Act(Action.UseAbility(1, Ability.GOLPE_DIVINO, Pos(1, 1))),
            NetMessage.Act(Action.EndTurn),
            NetMessage.Act(Action.PlaceArmy(listOf(Placement(PieceType.ATIRADOR, Pos(2, 0))))),
            NetMessage.Rematch,
        )
        for (m in msgs) {
            val line = Protocol.encode(m)
            assertTrue('\n' !in line)
            assertEquals(m, Protocol.decode(line))
        }
        assertNotNull(Protocol.decode(Protocol.encode(NetMessage.Ping)))
    }
}
