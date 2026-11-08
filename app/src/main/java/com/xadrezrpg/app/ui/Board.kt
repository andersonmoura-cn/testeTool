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
    val red = Color(0xFFD23C3C)
    val move = Color(0xAA3DDC84)
    val target = Color(0xAAFFA726)
    val area = Color(0x99FF1744)
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

/** Dados mínimos para desenhar uma peça (serve para o jogo e para a montagem do exército). */
data class CellPiece(
    val type: PieceType,
    val owner: Int,
    val hp: Int,
    val frozen: Boolean = false,
    val shieldMode: ShieldMode = ShieldMode.NENHUM,
)

enum class Mark { NONE, MOVE, TARGET, AREA }

/**
 * Tabuleiro 8x8. [flipped] = vira 180° (o jogador Vermelho vê seu lado embaixo).
 */
@Composable
fun BoardView(
    pieceAt: (Pos) -> CellPiece?,
    markAt: (Pos) -> Mark,
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
                        Cell(pos, cell, pieceAt(pos), markAt(pos), pos == selected, dimRow(pos.y)) { onCell(pos) }
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
    mark: Mark,
    selected: Boolean,
    dim: Boolean,
    onClick: () -> Unit,
) {
    val base = if ((pos.x + pos.y) % 2 == 0) Palette.dark else Palette.light
    Box(
        Modifier
            .size(size)
            .background(if (dim) base.copy(alpha = 0.35f) else base)
            .then(if (selected) Modifier.border(3.dp, Palette.selected) else Modifier)
            .clickable(onClick = onClick)
            .testTag("cell_${pos.x}_${pos.y}"),
        contentAlignment = Alignment.Center,
    ) {
        when (mark) {
            Mark.MOVE -> Box(Modifier.size(size * 0.35f).background(Palette.move, CircleShape))
            Mark.TARGET -> Box(Modifier.fillMaxSize().background(Palette.target.copy(alpha = 0.35f)))
            Mark.AREA -> Box(Modifier.fillMaxSize().background(Palette.area))
            Mark.NONE -> Unit
        }
        if (piece != null) PieceToken(piece, size)
    }
}

@Composable
fun PieceToken(piece: CellPiece, size: Dp) {
    Box(
        Modifier
            .size(size * 0.86f)
            .background(Palette.player(piece.owner), CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(piece.type.icon(), fontSize = (size.value * 0.4f).sp, textAlign = TextAlign.Center)
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
            Text("❄", fontSize = (size.value * 0.28f).sp, color = Color(0xFF80DEEA),
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
                    .background(Palette.selected, CircleShape)
                    .padding(horizontal = 3.dp),
            )
        }
    }
}
