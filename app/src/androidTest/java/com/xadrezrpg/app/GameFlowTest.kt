package com.xadrezrpg.app

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Teste de ponta a ponta no app real: abre o app, monta os dois exércitos (modo mesmo aparelho)
 * e joga alguns turnos conferindo movimento, habilidades e vida.
 * Tira fotos da tela em /sdcard/Android/data/com.xadrezrpg.app/files/shots.
 */
@RunWith(AndroidJUnit4::class)
class GameFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        val bmp = inst.uiAutomation.takeScreenshot() ?: return
        val dir = File(inst.targetContext.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun click(text: String) {
        val node = rule.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.performClick()
    }
    private fun cell(x: Int, y: Int) = rule.onNodeWithTag("cell_${x}_$y").performClick()
    private fun expect(text: String) = rule.onNodeWithText(text, substring = true).assertExists()

    @Test
    fun fullLocalGameFlow() {
        // Abertura do app
        rule.onNodeWithText("Xadrez RPG").assertIsDisplayed()
        shot("01_menu")

        // Regras abrem e voltam
        click("Regras")
        expect("Corte giratório")
        shot("02_regras")
        click("Voltar")

        // Montagem: Azul
        click("Jogar no mesmo aparelho")
        expect("Monte seu exército — Azul")
        click("Sugestão")
        shot("03_montagem_azul")
        click("Confirmar exército")

        // Troca de jogador e montagem: Vermelho
        click("Estou pronto")
        expect("Monte seu exército — Vermelho")
        click("Sugestão")
        click("Confirmar exército")

        // Partida
        expect("Vez do Azul")
        expect("Peças: 8 × 8")
        shot("04_inicio_partida")

        // Turno 1 (Azul): Espadachim (3,1) anda como torre até (3,5) e usa Golpe divino em (3,6)
        cell(3, 1)
        shot("05_movimentos_espadachim")
        cell(3, 5)
        click("Golpe divino")
        cell(3, 6)
        shot("06_alvo_golpe_divino")
        cell(3, 6) // toque de novo no alvo confirma
        expect("Espadachim Vermelho levou 2 de dano e caiu!")
        expect("Vez do Vermelho") // moveu + habilidade = turno acaba sozinho
        expect("Peças: 8 × 7")
        shot("07_apos_golpe_divino")

        // Turno 2 (Vermelho): Espadachim (4,6) usa Corte giratório (atinge aliados também)
        cell(4, 6)
        click("Corte giratório")
        shot("08_area_corte_giratorio")
        click("Usar Corte giratório")
        expect("Espadachim Azul levou 1 de dano (1 de vida).")
        expect("Escudeiro Vermelho levou 1 de dano (2 de vida).")
        expect("Conjurador Vermelho levou 1 de dano e caiu!")
        expect("Peças: 8 × 6")
        // Depois da habilidade ainda pode mover: anda até (4,4) e o turno acaba
        cell(4, 4)
        expect("Vez do Azul")
        shot("09_apos_corte_giratorio")

        // Turno 3 (Azul): Escudeiro (2,1) ativa Proteger e encerra o turno
        cell(2, 1)
        click("Proteger")
        click("Usar Proteger")
        expect("Proteger ✓")
        click("Encerrar turno")
        expect("Vez do Vermelho")
        shot("10_escudeiro_proteger")

        // Desistência
        click("Desistir")
        rule.onNodeWithText("O adversário vence a partida.").assertExists()
        rule.onAllNodesWithText("Desistir").onLast().performClick() // botão do diálogo
        expect("Azul venceu!")
        shot("11_fim_de_jogo")
    }
}
