package com.xadrezrpg.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xadrezrpg.app.net.Mode
import com.xadrezrpg.app.net.Session
import com.xadrezrpg.engine.Ability
import com.xadrezrpg.engine.Action
import com.xadrezrpg.engine.Engine
import com.xadrezrpg.engine.GameState
import com.xadrezrpg.engine.MAX_PIECES
import com.xadrezrpg.engine.Phase
import com.xadrezrpg.engine.Piece
import com.xadrezrpg.engine.PieceType
import com.xadrezrpg.engine.Placement
import com.xadrezrpg.engine.Pos
import com.xadrezrpg.engine.Rules
import com.xadrezrpg.engine.ShieldMode
import com.xadrezrpg.engine.homeRows
import com.xadrezrpg.engine.playerName
import kotlinx.coroutines.delay

@Composable
fun MatchScreen(session: Session, onExit: () -> Unit) {
    val state by session.state.collectAsState()
    val connected by session.connected.collectAsState()
    val status by session.status.collectAsState()
    val disconnected by session.disconnected.collectAsState()
    val notice by session.notice.collectAsState()

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2500)
            session.clearNotice()
        }
    }

    Column(Modifier.fillMaxSize()) {
        notice?.let {
            Text(
                it,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Palette.red).padding(8.dp),
            )
        }
        when {
            !connected && !disconnected -> LobbyView(session, status, onExit)
            state.phase == Phase.SETUP -> SetupView(session, state, onExit)
            else -> GameView(session, state, onExit)
        }
    }

    if (disconnected) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Conexão") },
            text = { Text(status.ifBlank { "Conexão perdida." }) },
            confirmButton = { TextButton(onClick = onExit) { Text("Voltar ao menu") } },
        )
    }
}

@Composable
private fun LobbyView(session: Session, status: String, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Palette.blue)
        if (session.mode == Mode.HOST) {
            Title("Aguardando jogador")
            Text(status, color = Palette.text, fontSize = 20.sp, textAlign = TextAlign.Center)
            Text(
                "No outro celular (mesmo Wi‑Fi), toque em \"Entrar em partida\". A partida aparece sozinha na lista, ou digite o IP acima.",
                color = Palette.muted, textAlign = TextAlign.Center,
            )
        } else {
            Title("Conectando")
            Text(status, color = Palette.text, textAlign = TextAlign.Center)
        }
        OutlinedButton(onClick = onExit) { Text("Cancelar", color = Palette.text) }
    }
}

// ============================ Montagem do exército ============================

private fun defaultArmy(player: Int): List<Placement> {
    val back = if (player == 0) 0 else 7
    val front = if (player == 0) 1 else 6
    return listOf(
        Placement(PieceType.ATIRADOR, Pos(1, back)),
        Placement(PieceType.CONJURADOR, Pos(2, back)),
        Placement(PieceType.CONJURADOR, Pos(5, back)),
        Placement(PieceType.ATIRADOR, Pos(6, back)),
        Placement(PieceType.ESCUDEIRO, Pos(2, front)),
        Placement(PieceType.ESPADACHIM, Pos(3, front)),
        Placement(PieceType.ESPADACHIM, Pos(4, front)),
        Placement(PieceType.ESCUDEIRO, Pos(5, front)),
    )
}

@Composable
private fun SetupView(session: Session, state: GameState, onExit: () -> Unit) {
    val player = session.myPlayer ?: state.placed.indexOfFirst { !it }.coerceAtLeast(0)

    if (state.placed[player]) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = Palette.player(player))
            Title("Exército pronto!")
            Text("Aguardando o adversário montar o exército...", color = Palette.muted)
            OutlinedButton(onClick = onExit) { Text("Sair", color = Palette.text) }
        }
        return
    }

    // Modo local: tela de "passe o aparelho" antes do segundo jogador montar.
    var handoffDone by remember(player) { mutableStateOf(session.mode != Mode.LOCAL || player == 0) }
    if (!handoffDone) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Title("Passe o aparelho")
            Text("Agora o jogador ${playerName(player)} monta o exército.", color = Palette.text)
            BigButton("Estou pronto") { handoffDone = true }
        }
        return
    }

    val placements = remember(player) { mutableStateListOf<Placement>() }
    var brush by remember { mutableStateOf(PieceType.ESPADACHIM) }
    val rows = homeRows(player)
    val error = Engine.validateArmy(player, placements)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Monte seu exército — ${playerName(player)}",
            color = Palette.player(player), fontSize = 20.sp, fontWeight = FontWeight.Bold,
        )
        Text("Escolha a classe e toque nas suas 2 fileiras. Toque numa peça para removê-la. (${placements.size}/$MAX_PIECES)",
            color = Palette.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            for (t in PieceType.entries) {
                val sel = t == brush
                Column(
                    Modifier
                        .weight(1f)
                        .background(if (sel) Palette.player(player) else Palette.panel, RoundedCornerShape(8.dp))
                        .border(if (sel) 2.dp else 0.dp, Color.White.copy(alpha = if (sel) 0.8f else 0f), RoundedCornerShape(8.dp))
                        .clickable { brush = t }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(t.icon(), fontSize = 24.sp)
                    Text(t.label, color = Palette.text, fontSize = 11.sp, maxLines = 1)
                    Text("Vida ${t.maxHp}", color = Palette.text, fontSize = 11.sp)
                }
            }
        }
        BoardView(
            pieceAt = { pos -> placements.firstOrNull { it.pos == pos }?.let { CellPiece(it.type, player, it.type.maxHp) } },
            markAt = { pos -> if (pos.y in rows && placements.none { it.pos == pos } && placements.size < MAX_PIECES) Mark.MOVE else Mark.NONE },
            selected = null,
            flipped = session.myPlayer == 1,
            dimRow = { it !in rows },
            onCell = { pos ->
                if (pos.y !in rows) return@BoardView
                val existing = placements.indexOfFirst { it.pos == pos }
                if (existing >= 0) placements.removeAt(existing)
                else if (placements.size < MAX_PIECES) placements.add(Placement(brush, pos))
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { placements.clear(); placements.addAll(defaultArmy(player)) }, modifier = Modifier.weight(1f)) {
                Text("Sugestão", color = Palette.text)
            }
            OutlinedButton(onClick = { placements.clear() }, modifier = Modifier.weight(1f)) {
                Text("Limpar", color = Palette.text)
            }
        }
        BigButton(if (error == null) "Confirmar exército" else error, enabled = error == null) {
            session.act(Action.PlaceArmy(placements.toList()), playerOverride = player)
        }
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Sair", color = Palette.text) }
    }
}

// ============================ Partida ============================

@Composable
private fun GameView(session: Session, state: GameState, onExit: () -> Unit) {
    val actingPlayer = session.myPlayer ?: state.currentPlayer
    val myTurn = state.phase == Phase.PLAYING && state.currentPlayer == actingPlayer

    var selectedId by remember { mutableStateOf<Int?>(null) }
    var ability by remember { mutableStateOf<Ability?>(null) }
    var pendingTarget by remember { mutableStateOf<Pos?>(null) }
    var confirmResign by remember { mutableStateOf(false) }

    // Ao mudar de turno ou de peça ativa, ajusta a seleção.
    LaunchedEffect(state.turn, state.activePieceId, state.abilityUsed) {
        ability = null
        pendingTarget = null
        selectedId = if (myTurn) state.activePieceId else selectedId?.takeIf { state.piece(it) != null }
    }

    val selected: Piece? = selectedId?.let { state.piece(it) }
    val canAct = selected != null && myTurn && Rules.canAct(state, actingPlayer, selected)
    val moves = if (canAct && !state.moved && ability == null) Rules.legalMoves(state, selected!!).toSet() else emptySet()
    val targets = if (canAct && ability != null) Rules.abilityTargets(state, selected!!, ability!!).toSet() else emptySet()
    val area = if (canAct && ability != null && (!ability!!.needsTarget || pendingTarget != null))
        Rules.abilityArea(state, selected!!, ability!!, pendingTarget).toSet() else emptySet()

    fun confirmAbility() {
        val a = ability ?: return
        val p = selected ?: return
        session.act(Action.UseAbility(p.id, a, if (a.needsTarget) pendingTarget else null))
        ability = null
        pendingTarget = null
    }

    fun onCell(pos: Pos) {
        val piece = state.pieceAt(pos)
        if (ability != null && canAct) {
            if (ability!!.needsTarget && pos in targets) {
                if (pendingTarget == pos) confirmAbility() else pendingTarget = pos
                return
            }
            ability = null
            pendingTarget = null
        }
        if (canAct && pos in moves) {
            session.act(Action.Move(selected!!.id, pos))
            return
        }
        // Com uma peça já ativa no turno, não dá para trocar para outra sua.
        if (myTurn && state.activePieceId != null && piece != null && piece.owner == actingPlayer &&
            piece.id != state.activePieceId
        ) return
        selectedId = piece?.id
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TurnBanner(session, state, myTurn)
        BoardView(
            pieceAt = { pos ->
                state.pieceAt(pos)?.let { CellPiece(it.type, it.owner, it.hp, state.isFrozen(it) || it.frozenTurn > state.turn, it.shieldMode) }
            },
            markAt = { pos ->
                when {
                    pos in area -> Mark.AREA
                    pos in targets -> Mark.TARGET
                    pos in moves -> Mark.MOVE
                    else -> Mark.NONE
                }
            },
            selected = selected?.pos,
            flipped = session.myPlayer == 1,
            onCell = ::onCell,
        )

        if (selected != null) {
            PieceInfo(state, selected)
        } else if (myTurn) {
            Text("Toque em uma das suas peças para agir.", color = Palette.muted)
        }

        if (canAct) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for (a in selected!!.type.abilities) {
                    val isSel = ability == a
                    val active = a == Ability.PROTEGER && selected.shieldMode == ShieldMode.PROTEGER ||
                        a == Ability.REFLETIR && selected.shieldMode == ShieldMode.REFLETIR
                    Button(
                        onClick = { ability = if (isSel) null else a; pendingTarget = null },
                        enabled = !state.abilityUsed,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isSel) Palette.target else Palette.panel),
                    ) { Text(a.label + if (active) " ✓" else "", fontSize = 13.sp, color = Palette.text) }
                }
            }
            ability?.let { a ->
                Text(a.description, color = Palette.muted, fontSize = 13.sp)
                val ready = !a.needsTarget || pendingTarget != null
                Text(
                    if (ready) "Área em vermelho. Confirme (ou toque de novo no alvo)." else "Escolha um alvo nas casas destacadas.",
                    color = Palette.text, fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = ::confirmAbility, enabled = ready, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.red)) { Text("Usar ${a.label}") }
                    OutlinedButton(onClick = { ability = null; pendingTarget = null }, modifier = Modifier.weight(1f)) {
                        Text("Cancelar", color = Palette.text)
                    }
                }
            }
            Text(
                "Mover: ${if (state.moved) "feito" else "disponível"}  ·  Habilidade: ${if (state.abilityUsed) "usada" else "disponível"}",
                color = Palette.muted, fontSize = 13.sp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { session.act(Action.EndTurn) },
                enabled = myTurn,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.blue),
            ) { Text("Encerrar turno") }
            OutlinedButton(onClick = { confirmResign = true }, enabled = state.phase == Phase.PLAYING, modifier = Modifier.weight(1f)) {
                Text("Desistir", color = Palette.text)
            }
        }

        Column(
            Modifier.fillMaxWidth().background(Palette.panel, RoundedCornerShape(8.dp)).padding(8.dp),
        ) {
            for (line in state.log.takeLast(6).reversed()) Text(line, color = Palette.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
    }

    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text("Desistir?") },
            text = { Text("O adversário vence a partida.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmResign = false
                    session.act(Action.Resign, playerOverride = actingPlayer)
                }) { Text("Desistir") }
            },
            dismissButton = { TextButton(onClick = { confirmResign = false }) { Text("Continuar jogando") } },
        )
    }

    if (state.phase == Phase.FINISHED) {
        val title = when {
            state.draw -> "Empate!"
            session.myPlayer == null -> "${playerName(state.winner ?: 0)} venceu!"
            state.winner == session.myPlayer -> "Você venceu! 🏆"
            else -> "Você perdeu."
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text(title) },
            text = { Text(state.log.lastOrNull() ?: "") },
            confirmButton = { TextButton(onClick = { session.rematch() }) { Text("Revanche") } },
            dismissButton = { TextButton(onClick = onExit) { Text("Menu") } },
        )
    }
}

@Composable
private fun TurnBanner(session: Session, state: GameState, myTurn: Boolean) {
    val cur = state.currentPlayer
    val text = when {
        session.myPlayer == null -> "Vez do ${playerName(cur)}"
        myTurn -> "Sua vez (${playerName(cur)})"
        else -> "Vez do adversário (${playerName(cur)})"
    }
    Row(
        Modifier.fillMaxWidth().background(Palette.player(cur), RoundedCornerShape(8.dp)).padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Peças: ${state.alive(0).size} × ${state.alive(1).size}", color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun PieceInfo(state: GameState, p: Piece) {
    val extra = buildList {
        if (state.isFrozen(p) || p.frozenTurn > state.turn) add("❄ congelada")
        when (p.shieldMode) {
            ShieldMode.PROTEGER -> add("modo Proteger")
            ShieldMode.REFLETIR -> add("modo Refletir")
            ShieldMode.NENHUM -> Unit
        }
    }
    Text(
        "${p.type.icon()} ${p.type.label} (${playerName(p.owner)}) — ${"♥".repeat(p.hp)} ${p.hp}/${p.type.maxHp}" +
            if (extra.isNotEmpty()) " · " + extra.joinToString(" · ") else "",
        color = Palette.player(p.owner).copy(alpha = 1f).let { if (p.owner == 0) Color(0xFF8AB4FF) else Color(0xFFFF8A8A) },
        fontWeight = FontWeight.Bold,
    )
}
