package com.xadrezrpg.app

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
    private fun expect(text: String) = rule.onAllNodesWithText(text, substring = true).onFirst().assertExists()

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

        // Turno 1 (Azul): Espadachim (3,1) anda como torre até (3,5) e DEPOIS usa Golpe divino em (3,6)
        cell(3, 1)
        shot("05_movimentos_espadachim")
        cell(3, 5)
        expect("Mover: feito")
        click("Golpe divino")
        expect("Toque num alvo com anel laranja.")
        expect("-2") // dano previsto em cima dos alvos possíveis
        shot("06_alvos_golpe_divino")
        cell(3, 6)
        cell(3, 6) // toque de novo no alvo confirma
        expect("Espadachim Vermelho levou 2 de dano e caiu!")
        expect("Peças: 8 × 7")
        // O turno NÃO passa sozinho
        expect("Vez do Azul")
        expect("Aperte \"Encerrar turno\"")
        shot("07_apos_golpe_divino")
        click("Encerrar turno")
        expect("Vez do Vermelho")

        // Turno 2 (Vermelho): Espadachim (4,6) usa Corte giratório (atinge aliados também) e depois anda
        cell(4, 6)
        click("Corte giratório")
        expect("-1")
        shot("08_area_corte_giratorio")
        click("Usar Corte giratório")
        expect("Espadachim Azul levou 1 de dano (1 de vida).")
        expect("Escudeiro Vermelho levou 1 de dano (2 de vida).")
        expect("Conjurador Vermelho levou 1 de dano e caiu!")
        expect("Peças: 8 × 6")
        cell(4, 4)
        expect("Vez do Vermelho")
        click("Encerrar turno")
        expect("Vez do Azul")
        shot("09_apos_corte_giratorio")

        // Turno 3 (Azul): pré-visualiza a Chuva de flechas do Atirador (1,0), cancela e usa o Escudeiro
        cell(1, 0)
        click("Chuva de flechas")
        expect("Amarelo = alcance")
        shot("10_alcance_chuva_de_flechas")
        cell(3, 3)
        expect("Vermelho = área atingida")
        shot("11_area_chuva_de_flechas")
        click("Cancelar")
        cell(2, 1)
        click("Proteger")
        click("Usar Proteger")
        expect("Proteger ✓")
        click("Encerrar turno")
        expect("Vez do Vermelho")
        shot("12_escudeiro_proteger_aura")

        // Turno 4 (Vermelho): Conjurador (2,7) usa Choque do trovão em X centrado em (3,5)
        cell(2, 7)
        click("Choque do trovão")
        cell(3, 5)
        expect("-1")
        shot("13_area_choque_do_trovao")
        cell(3, 5)
        expect("Espadachim Azul levou 1 de dano e caiu!")
        expect("Espadachim Vermelho levou 1 de dano (1 de vida).")
        expect("Peças: 7 × 6")
        click("Encerrar turno")
        expect("Vez do Azul")

        // Desistência (Azul desiste)
        click("Desistir")
        rule.onNodeWithText("O adversário vence a partida.").assertExists()
        rule.onAllNodesWithText("Desistir").onLast().performClick() // botão do diálogo
        expect("Vermelho venceu!")
        shot("14_fim_de_jogo")
    }
}
