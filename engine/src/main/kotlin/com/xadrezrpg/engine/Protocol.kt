package com.xadrezrpg.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Mensagens trocadas pela rede local (uma mensagem JSON por linha, via TCP).
 * O host é autoritativo: valida as ações com o [Engine] e envia o estado completo.
 */
@Serializable
sealed class NetMessage {
    /** Host -> cliente: qual jogador você é. */
    @Serializable
    data class Welcome(val player: Int, val version: Int = PROTOCOL_VERSION) : NetMessage()

    /** Host -> cliente: estado atual da partida. */
    @Serializable
    data class State(val state: GameState) : NetMessage()

    /** Host -> cliente: ação recusada. */
    @Serializable
    data class Error(val message: String) : NetMessage()

    /** Cliente -> host: ação do jogador. */
    @Serializable
    data class Act(val action: Action) : NetMessage()

    /** Pedido de nova partida (revanche) — qualquer lado. */
    @Serializable
    data object Rematch : NetMessage()

    /** Mantém a conexão viva. */
    @Serializable
    data object Ping : NetMessage()
}

const val PROTOCOL_VERSION = 1
const val GAME_TCP_PORT = 47123
const val DISCOVERY_UDP_PORT = 47124
const val DISCOVERY_MAGIC = "XADREZRPG"

object Protocol {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "t"
    }

    fun encode(m: NetMessage): String = json.encodeToString(NetMessage.serializer(), m)
    fun decode(line: String): NetMessage = json.decodeFromString(NetMessage.serializer(), line)
}
