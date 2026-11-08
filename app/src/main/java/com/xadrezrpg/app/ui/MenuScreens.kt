package com.xadrezrpg.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xadrezrpg.app.net.Discovery
import com.xadrezrpg.engine.PieceType

@Composable
fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.blue),
    ) { Text(text, fontSize = 17.sp) }
}

@Composable
fun Title(text: String) {
    Text(text, color = Palette.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun MenuScreen(onLocal: () -> Unit, onHost: () -> Unit, onJoin: () -> Unit, onRules: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(PieceType.entries.joinToString("  ") { it.icon() }, fontSize = 36.sp)
        Title("Xadrez RPG")
        Text("Tabuleiro tático 8x8 com classes e habilidades", color = Palette.muted)
        Spacer(Modifier.height(16.dp))
        BigButton("Criar partida (Wi‑Fi)", onClick = onHost)
        BigButton("Entrar em partida (Wi‑Fi)", onClick = onJoin)
        BigButton("Jogar no mesmo aparelho", onClick = onLocal)
        OutlinedButton(onClick = onRules, modifier = Modifier.fillMaxWidth()) {
            Text("Regras", color = Palette.text)
        }
    }
}

@Composable
fun JoinScreen(onConnect: (String) -> Unit, onBack: () -> Unit) {
    val discovery = remember { Discovery() }
    DisposableEffect(Unit) {
        discovery.start()
        onDispose { discovery.stop() }
    }
    val hosts by discovery.hosts.collectAsState()
    var ip by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Entrar em partida")
        Text("Os dois aparelhos precisam estar no mesmo Wi‑Fi. Partidas encontradas:", color = Palette.muted)
        if (hosts.isEmpty()) {
            Text("Procurando... (no outro aparelho toque em \"Criar partida\")", color = Palette.muted)
        }
        for (h in hosts) {
            Text(
                "▶ ${h.name}  (${h.address})",
                color = Palette.text,
                fontSize = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.panel, RoundedCornerShape(8.dp))
                    .clickable { onConnect(h.address) }
                    .padding(16.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Ou digite o IP mostrado no aparelho que criou a partida:", color = Palette.muted)
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it.trim() },
            label = { Text("IP (ex.: 192.168.0.10)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        BigButton("Conectar", enabled = ip.isNotBlank()) { onConnect(ip) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar", color = Palette.text) }
    }
}

@Composable
fun RulesScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Title("Regras")
        RuleText(
            """
            • Tabuleiro 8x8. Cada jogador monta seu exército com até 8 peças, em qualquer combinação, nas suas 2 primeiras fileiras.
            • Na sua vez, escolha UMA peça. Ela pode MOVER e usar UMA de suas 2 habilidades, em qualquer ordem (ambos opcionais).
            • Movimento só para casas vazias — não há captura. Peças morrem ao chegar a 0 de vida.
            • Habilidades de área atingem também suas peças!
            • Distâncias contam casas em qualquer direção (inclusive diagonal).
            • Vence quem eliminar todas as peças do adversário (ou se ele desistir).
            """.trimIndent()
        )
        for (t in PieceType.entries) {
            Text("${t.icon()} ${t.label} — ${t.maxHp} de vida", color = Palette.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            RuleText("Movimento: " + when (t) {
                PieceType.CONJURADOR -> "teletransporte em L (como o cavalo, pula peças)."
                PieceType.ATIRADOR -> "diagonal, como o bispo."
                PieceType.ESPADACHIM -> "reto, como a torre."
                PieceType.ESCUDEIRO -> "1 casa em qualquer direção."
            })
            for (a in t.abilities) RuleText("• ${a.label}: ${a.description}")
        }
        RuleText("Símbolos: ❄ = congelada · P = escudeiro protegendo · R = escudeiro refletindo · número no canto da peça = vida.")
        BigButton("Voltar", onClick = onBack)
    }
}

@Composable
private fun RuleText(t: String) = Text(t, color = Palette.muted, fontSize = 15.sp)
