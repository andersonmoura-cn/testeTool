package com.xadrezrpg.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xadrezrpg.engine.BOARD_SIZE
import com.xadrezrpg.engine.PieceType
import com.xadrezrpg.engine.Pos
import com.xadrezrpg.engine.ShieldMode

object Palette {
    val bg = Color(0xFF1B1B24)
    val panel = Color(0xFF2A2A38)
    val light = Color(0xFFE8D9B5)
    val dark = Color(0xFFB08A5E)
    val blue = Color(0xFF2F6FDB)
    val green = Color(0xFF2E9D58)
    val red = Color(0xFFD23C3C)
    val move = Color(0xAA3DDC84)
    val target = Color(0xFFFF9800)
    val range = Color(0x66FFEB3B)
    val area = Color(0x88FF1744)
    val areaBorder = Color(0xFFD50000)
    val auraProtect = Color(0xFFFFD54F)
    val auraReflect = Color(0xFFB388FF)
    val selected = Color(0xFFFFEB3B)
    val text = Color(0xFFF2F2F2)
    val muted = Color(0xFFB0B0C0)

    fun player(p: Int) = if (p == 0) blue else red
}

fun PieceType.icon(): String = when (this) {
    PieceType.ESPADACHIM -> "🗡️" // 🗡️
    PieceType.CONJURADOR -> "🔮"       // 🔮
    PieceType.ATIRADOR -> "🏹"         // 🏹
    PieceType.ESCUDEIRO -> "🛡️"  // 🛡️
}

/** Aura desenhada em volta da peça para mostrar efeitos contínuos a ambos os jogadores. */
enum class Aura { NONE, PROTECTED, REFLECT }

/** Dados mínimos para desenhar uma peça (serve para o jogo e para a montagem do exército). */
data class CellPiece(
    val type: PieceType,
    val owner: Int,
    val hp: Int,
    val frozen: Boolean = false,
    val shieldMode: ShieldMode = ShieldMode.NENHUM,
    val aura: Aura = Aura.NONE,
)

enum class Mark { NONE, MOVE, TARGET, AREA }

/**
 * Decoração de uma casa: [range] = alcance da habilidade (amarelo), [mark] = movimento/alvo/área,
 * [damage] = dano previsto ("-N" no canto), [freeze] = será congelada.
 */
data class CellDeco(
    val mark: Mark = Mark.NONE,
    val range: Boolean = false,
    val damage: Int? = null,
    val freeze: Boolean = false,
)

/**
 * Tabuleiro 8x8. [flipped] = vira 180° (o jogador Vermelho vê seu lado embaixo).
 */
@Composable
fun BoardView(
    pieceAt: (Pos) -> CellPiece?,
    decoAt: (Pos) -> CellDeco,
    selected: Pos?,
    flipped: Boolean,
    onCell: (Pos) -> Unit,
    dimRow: (Int) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f)) {
        val cell: Dp = maxWidth / BOARD_SIZE
        Column {
            for (row in 0 until BOARD_SIZE) {
                Row {
                    for (col in 0 until BOARD_SIZE) {
                        // Sem virar: y=0 embaixo (lado Azul). Virado: y=7 embaixo.
                        val pos = if (flipped) Pos(BOARD_SIZE - 1 - col, row) else Pos(col, BOARD_SIZE - 1 - row)
                        Cell(pos, cell, pieceAt(pos), decoAt(pos), pos == selected, dimRow(pos.y)) { onCell(pos) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(
    pos: Pos,
    size: Dp,
    piece: CellPiece?,
    deco: CellDeco,
    selected: Boolean,
    dim: Boolean,
    onClick: () -> Unit,
) {
    val base = if ((pos.x + pos.y) % 2 == 0) Palette.dark else Palette.light
    Box(
        Modifier
            .size(size)
            .background(if (dim) base.copy(alpha = 0.35f) else base)
            .clickable(onClick = onClick)
            .testTag("cell_${pos.x}_${pos.y}"),
        contentAlignment = Alignment.Center,
    ) {
        if (deco.range) Box(Modifier.fillMaxSize().background(Palette.range))
        if (deco.mark == Mark.AREA) {
            Box(Modifier.fillMaxSize().background(Palette.area).border(2.dp, Palette.areaBorder))
        }
        if (deco.mark == Mark.MOVE && piece == null) {
            Box(Modifier.size(size * 0.35f).background(Palette.move, CircleShape))
        }
        if (piece != null) PieceToken(piece, size)
        if (deco.mark == Mark.TARGET) {
            // Anel por cima da peça: deixa claro quem pode ser alvo
            Box(Modifier.size(size * 0.96f).border(3.dp, Palette.target, CircleShape))
        }
        if (selected) Box(Modifier.fillMaxSize().border(3.dp, Palette.selected))
        val badge = when {
            deco.damage != null -> "-${deco.damage}" to Palette.red
            deco.freeze && piece != null -> "❄" to Color(0xFF0097A7)
            else -> null
        }
        if (badge != null) {
            Text(
                badge.first,
                color = Color.White,
                fontSize = (size.value * 0.24f).sp,
                fontWeight = FontWeight.Black,
                lineHeight = (size.value * 0.26f).sp,
                modifier = Modifier.align(Alignment.TopEnd)
                    .background(badge.second, RoundedCornerShape(4.dp))
                    .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

@Composable
fun PieceToken(piece: CellPiece, size: Dp) {
    val auraColor = when (piece.aura) {
        Aura.PROTECTED -> Palette.auraProtect
        Aura.REFLECT -> Palette.auraReflect
        Aura.NONE -> null
    }
    Box(contentAlignment = Alignment.Center) {
        if (auraColor != null) {
            // Aura: anel brilhante em volta da peça
            Box(
                Modifier.size(size)
                    .background(auraColor.copy(alpha = 0.45f), CircleShape)
                    .border(3.dp, auraColor, CircleShape)
            )
        }
        Box(
            Modifier
                .size(size * 0.8f)
                .background(Palette.player(piece.owner), CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(piece.type.icon(), fontSize = (size.value * 0.38f).sp, textAlign = TextAlign.Center)
            // Vida: número em destaque no canto inferior
            Text(
                "${piece.hp}",
                color = Color.White,
                fontSize = (size.value * 0.24f).sp,
                fontWeight = FontWeight.Black,
                lineHeight = (size.value * 0.26f).sp,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                    .padding(horizontal = 4.dp),
            )
            if (piece.frozen) {
                Text("❄", fontSize = (size.value * 0.26f).sp, color = Color(0xFF80DEEA),
                    modifier = Modifier.align(Alignment.TopEnd).padding(1.dp))
            }
            val modeTag = when (piece.shieldMode) {
                ShieldMode.PROTEGER -> "P"
                ShieldMode.REFLETIR -> "R"
                ShieldMode.NENHUM -> null
            }
            if (modeTag != null) {
                Text(
                    modeTag,
                    fontSize = (size.value * 0.2f).sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    modifier = Modifier.align(Alignment.TopStart)
                        .background(if (modeTag == "P") Palette.auraProtect else Palette.auraReflect, CircleShape)
                        .padding(horizontal = 3.dp),
                )
            }
        }
    }
}
