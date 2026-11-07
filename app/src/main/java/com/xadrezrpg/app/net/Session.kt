package com.xadrezrpg.app.net

import com.xadrezrpg.engine.Action
import com.xadrezrpg.engine.DISCOVERY_MAGIC
import com.xadrezrpg.engine.DISCOVERY_UDP_PORT
import com.xadrezrpg.engine.Engine
import com.xadrezrpg.engine.GAME_TCP_PORT
import com.xadrezrpg.engine.GameState
import com.xadrezrpg.engine.NetMessage
import com.xadrezrpg.engine.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

enum class Mode { LOCAL, HOST, CLIENT }

/**
 * Uma partida em andamento. Em [Mode.LOCAL] os dois jogadores usam o mesmo aparelho.
 * Em [Mode.HOST] este aparelho é o jogador 0 e o juiz da partida; em [Mode.CLIENT] é o jogador 1.
 */
class Session private constructor(val mode: Mode) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(Engine.newGame())
    val state: StateFlow<GameState> = _state

    /** Mensagem de erro/aviso mais recente (consumida pela UI). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    /** Para rede: conectado ao outro jogador. LOCAL: sempre true. */
    private val _connected = MutableStateFlow(mode == Mode.LOCAL)
    val connected: StateFlow<Boolean> = _connected

    /** Texto de status da conexão (ex.: IP do host, "conectando..."). */
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _disconnected = MutableStateFlow(false)
    val disconnected: StateFlow<Boolean> = _disconnected

    /** Jogador deste aparelho (null no modo local: quem estiver na vez). */
    val myPlayer: Int? = when (mode) {
        Mode.LOCAL -> null
        Mode.HOST -> 0
        Mode.CLIENT -> 1
    }

    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private val writeLock = Any()

    fun clearNotice() {
        _notice.value = null
    }

    /** Envia uma ação feita neste aparelho. */
    fun act(action: Action, playerOverride: Int? = null) {
        when (mode) {
            Mode.LOCAL -> {
                val s = _state.value
                val player = playerOverride ?: s.currentPlayer
                val (ns, err) = Engine.tryApply(s, player, action)
                if (ns != null) _state.value = ns else _notice.value = err
            }
            Mode.HOST -> scope.launch { hostApply(0, action) }
            Mode.CLIENT -> scope.launch { send(NetMessage.Act(action)) }
        }
    }

    fun rematch() {
        when (mode) {
            Mode.LOCAL -> _state.value = Engine.newGame()
            Mode.HOST -> scope.launch {
                _state.value = Engine.newGame()
                send(NetMessage.State(_state.value))
            }
            Mode.CLIENT -> scope.launch { send(NetMessage.Rematch) }
        }
    }

    fun close() {
        runCatching { socket?.close() }
        runCatching { serverSocket?.close() }
        scope.cancel()
    }

    // ---------------- Host ----------------

    @Synchronized
    private fun hostApply(player: Int, action: Action) {
        val (ns, err) = Engine.tryApply(_state.value, player, action)
        if (ns != null) {
            _state.value = ns
            send(NetMessage.State(ns))
        } else if (player == 0) {
            _notice.value = err
        } else {
            send(NetMessage.Error(err ?: "Ação inválida"))
        }
    }

    private fun startHost() {
        val ips = localIpv4Addresses()
        _status.value = if (ips.isEmpty()) "Sem Wi‑Fi? Nenhum IP encontrado."
        else "Seu IP: ${ips.joinToString(" / ") { it.hostAddress ?: "" }}"
        scope.launch {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(GAME_TCP_PORT))
                serverSocket = server
                val discovery = launch { broadcastLoop() }
                val client = server.accept()
                discovery.cancel()
                attach(client)
                send(NetMessage.Welcome(1))
                send(NetMessage.State(_state.value))
                readLoop(client) { msg ->
                    when (msg) {
                        is NetMessage.Act -> hostApply(1, msg.action)
                        is NetMessage.Rematch -> {
                            _state.value = Engine.newGame()
                            send(NetMessage.State(_state.value))
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                if (scope.isActive) _status.value = "Erro ao criar partida: ${e.message}"
            }
        }
    }

    /** Anuncia a partida na rede local por broadcast UDP até alguém conectar. */
    private suspend fun broadcastLoop() {
        DatagramSocket().use { ds ->
            ds.broadcast = true
            val payload = "$DISCOVERY_MAGIC|${android.os.Build.MODEL}".toByteArray()
            while (true) {
                for (addr in broadcastAddresses()) {
                    runCatching { ds.send(DatagramPacket(payload, payload.size, addr, DISCOVERY_UDP_PORT)) }
                }
                delay(1000)
            }
        }
    }

    // ---------------- Cliente ----------------

    private fun startClient(host: String) {
        _status.value = "Conectando a $host..."
        scope.launch {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, GAME_TCP_PORT), 8000)
                attach(s)
                readLoop(s) { msg ->
                    when (msg) {
                        is NetMessage.State -> _state.value = msg.state
                        is NetMessage.Error -> _notice.value = msg.message
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                if (scope.isActive) {
                    _status.value = "Não foi possível conectar: ${e.message}"
                    _disconnected.value = true
                }
            }
        }
    }

    // ---------------- Comum ----------------

    private fun attach(s: Socket) {
        s.tcpNoDelay = true
        s.keepAlive = true
        socket = s
        writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
        _connected.value = true
        _status.value = "Conectado"
    }

    private fun send(m: NetMessage) {
        val w = writer ?: return
        synchronized(writeLock) {
            try {
                w.write(Protocol.encode(m))
                w.write("\n")
                w.flush()
            } catch (e: Exception) {
                onLost()
            }
        }
    }

    private suspend fun readLoop(s: Socket, handle: (NetMessage) -> Unit) {
        // Ping periódico para detectar queda de conexão.
        val pinger = scope.launch {
            while (isActive) {
                delay(5000)
                send(NetMessage.Ping)
            }
        }
        try {
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val msg = runCatching { Protocol.decode(line) }.getOrNull() ?: continue
                handle(msg)
            }
        } catch (_: Exception) {
        } finally {
            pinger.cancel()
            onLost()
        }
    }

    private fun onLost() {
        if (!scope.isActive) return
        _connected.value = false
        _disconnected.value = true
        _status.value = "Conexão perdida."
    }

    companion object {
        fun local() = Session(Mode.LOCAL)
        fun host() = Session(Mode.HOST).also { it.startHost() }
        fun join(host: String) = Session(Mode.CLIENT).also { it.startClient(host) }

        fun localIpv4Addresses(): List<InetAddress> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filter { it is Inet4Address && !it.isLoopbackAddress }
        }.getOrDefault(emptyList())

        fun broadcastAddresses(): List<InetAddress> {
            val list = runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.interfaceAddresses }
                    .mapNotNull { it.broadcast }
            }.getOrDefault(emptyList())
            return (list + InetAddress.getByName("255.255.255.255")).distinct()
        }
    }
}

/** Partida encontrada na rede local. */
data class FoundHost(val address: String, val name: String)

/** Escuta anúncios de partidas (broadcast UDP) na rede local. */
class Discovery {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _hosts = MutableStateFlow<List<FoundHost>>(emptyList())
    val hosts: StateFlow<List<FoundHost>> = _hosts
    private var socket: DatagramSocket? = null

    fun start() {
        scope.launch {
            try {
                val ds = DatagramSocket(null)
                ds.reuseAddress = true
                ds.broadcast = true
                ds.bind(InetSocketAddress(DISCOVERY_UDP_PORT))
                socket = ds
                val buf = ByteArray(512)
                while (isActive) {
                    val pkt = DatagramPacket(buf, buf.size)
                    ds.receive(pkt)
                    val text = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                    if (!text.startsWith(DISCOVERY_MAGIC)) continue
                    val name = text.substringAfter('|', "Partida")
                    val addr = pkt.address.hostAddress ?: continue
                    if (_hosts.value.none { it.address == addr }) {
                        _hosts.value = _hosts.value + FoundHost(addr, name)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun stop() {
        runCatching { socket?.close() }
        scope.cancel()
    }
}
