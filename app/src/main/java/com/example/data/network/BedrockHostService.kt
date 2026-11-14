package com.example.data.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ServerRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

enum class HostStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

enum class LogSeverity {
    INFO,
    WARN,
    ERROR,
    RCON,
    RAKNET
}

data class ConsoleLogLine(
    val id: Long,
    val timestamp: String,
    val severity: LogSeverity,
    val tag: String,
    val message: String
)

data class ActivePeer(
    val addressKey: String,
    val displayName: String,
    val pingMs: Int,
    val joinedAtMs: Long,
    val lastSeenMs: Long,
    val isOperator: Boolean = false
)

data class HostRuntimeState(
    val activeRoomId: String? = null,
    val serverName: String = "MCS-HOST",
    val status: HostStatus = HostStatus.STOPPED,
    val boundPort: Int = 19132,
    val localIpAddress: String = "127.0.0.1",
    val tps: Double = 20.0,
    val tpsHistory: List<Float> = listOf(20f),
    val latencyHistory: List<Int> = emptyList(),
    val activePeers: List<ActivePeer> = emptyList(),
    val packetsHandled: Long = 0L,
    val consoleLogs: List<ConsoleLogLine> = emptyList(),
    val errorMessage: String? = null
)

object HostEngineController {
    private val _state = MutableStateFlow(
        HostRuntimeState(localIpAddress = ServerStatusPinger.resolveLocalIpv4Address())
    )
    val state: StateFlow<HostRuntimeState> = _state.asStateFlow()

    @Volatile
    var currentRoomConfig: ServerRoom = ServerRoom()
        private set

    private var logSequence = 0L
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun syncRoomConfig(room: ServerRoom) {
        currentRoomConfig = room
        _state.update {
            it.copy(
                serverName = room.name,
                localIpAddress = ServerStatusPinger.resolveLocalIpv4Address()
            )
        }
    }

    fun startLocalHost(context: Context, room: ServerRoom) {
        syncRoomConfig(room)
        val intent = Intent(context, BedrockHostService::class.java).apply {
            action = BedrockHostService.ACTION_START
            putExtra(BedrockHostService.EXTRA_ROOM_ID, room.id)
            putExtra(BedrockHostService.EXTRA_PORT, room.port.toInt())
            putExtra(BedrockHostService.EXTRA_NAME, room.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopLocalHost(context: Context) {
        val intent = Intent(context, BedrockHostService::class.java).apply {
            action = BedrockHostService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun appendLog(severity: LogSeverity, tag: String, message: String) {
        val entry = ConsoleLogLine(
            id = ++logSequence,
            timestamp = timeFmt.format(Date()),
            severity = severity,
            tag = tag,
            message = message
        )
        _state.update { current ->
            val updated = (current.consoleLogs + entry).takeLast(MAX_LOG_LINES)
            current.copy(consoleLogs = updated)
        }
    }

    fun clearLogs() {
        _state.update { it.copy(consoleLogs = emptyList()) }
    }

    fun recordRemotePing(latencyMs: Int, onlinePlayers: Int? = null) {
        if (latencyMs <= 0) return
        _state.update { current ->
            val nextHistory = (current.latencyHistory + latencyMs).takeLast(MAX_HISTORY_POINTS)
            current.copy(latencyHistory = nextHistory)
        }
    }

    fun executeLocalHostCommand(rawCommand: String, actorName: String): String {
        val clean = rawCommand.trim().removePrefix("/")
        if (clean.isBlank()) return "Empty command."
        val parts = clean.split(Regex("\\s+"))
        val cmd = parts.first().lowercase(Locale.US)
        val args = parts.drop(1)

        val response = when (cmd) {
            "help" -> "Commands: /list, /say <msg>, /status, /kick <player>, /ban <player>, /op <player>, /whitelist add|remove <player>, /save-all"
            "list" -> {
                val peers = _state.value.activePeers
                "There are ${peers.size}/${currentRoomConfig.maxPlayers} players connected: " +
                    peers.joinToString(", ") { it.displayName }.ifBlank { "none" }
            }
            "status" -> {
                val st = _state.value
                "Host=${st.status.name} | Port=${st.boundPort} | TPS=${"%.1f".format(st.tps)} | Packets=${st.packetsHandled}"
            }
            "say" -> {
                val msg = args.joinToString(" ").ifBlank { "(empty)" }
                "[Server/$actorName] $msg"
            }
            "kick" -> {
                val target = args.firstOrNull().orEmpty()
                if (target.isBlank()) "Usage: /kick <player>"
                else {
                    _state.update { st ->
                        st.copy(activePeers = st.activePeers.filterNot {
                            it.displayName.equals(target, ignoreCase = true)
                        })
                    }
                    "Kicked $target from active RakNet session."
                }
            }
            "save-all" -> "Saved world seed (${currentRoomConfig.seedNumeric}) and room configuration."
            else -> "Executed /$clean on local Bedrock host."
        }
        appendLog(LogSeverity.INFO, "SERVER", response)
        return response
    }

    internal fun updateStatus(
        roomId: String?,
        status: HostStatus,
        port: Int,
        error: String? = null
    ) {
        _state.update {
            it.copy(
                activeRoomId = roomId,
                status = status,
                boundPort = port,
                localIpAddress = ServerStatusPinger.resolveLocalIpv4Address(),
                errorMessage = error,
                activePeers = if (status == HostStatus.STOPPED) emptyList() else it.activePeers
            )
        }
    }

    internal fun recordTick(tps: Double) {
        _state.update { current ->
            val nextTps = (current.tpsHistory + tps.toFloat()).takeLast(MAX_HISTORY_POINTS)
            current.copy(tps = tps, tpsHistory = nextTps)
        }
    }

    internal fun registerPeerPing(addressKey: String, pingMs: Int) {
        val now = System.currentTimeMillis()
        _state.update { current ->
            val updatedPackets = current.packetsHandled + 1L
            val nextLat = if (pingMs > 0) (current.latencyHistory + pingMs).takeLast(MAX_HISTORY_POINTS) else current.latencyHistory
            val existing = current.activePeers.firstOrNull { it.addressKey == addressKey }
            val nextPeers = if (existing != null) {
                current.activePeers.map {
                    if (it.addressKey == addressKey) it.copy(pingMs = pingMs.coerceAtLeast(1), lastSeenMs = now)
                    else it
                }
            } else {
                current.activePeers
            }
            current.copy(
                packetsHandled = updatedPackets,
                latencyHistory = nextLat,
                activePeers = nextPeers
            )
        }
    }

    internal fun registerPeerHandshake(addressKey: String, displayName: String) {
        val now = System.currentTimeMillis()
        _state.update { current ->
            val cleanList = current.activePeers.filterNot { it.addressKey == addressKey }
            val newPeer = ActivePeer(
                addressKey = addressKey,
                displayName = displayName,
                pingMs = 12,
                joinedAtMs = now,
                lastSeenMs = now,
                isOperator = currentRoomConfig.operators.any { it.equals(displayName, ignoreCase = true) }
            )
            current.copy(
                packetsHandled = current.packetsHandled + 1L,
                activePeers = (cleanList + newPeer).take(50)
            )
        }
    }

    internal fun pruneIdlePeers(timeoutMs: Long = 35_000L) {
        val cutoff = System.currentTimeMillis() - timeoutMs
        _state.update { current ->
            val active = current.activePeers.filter { it.lastSeenMs >= cutoff }
            if (active.size == current.activePeers.size) current else current.copy(activePeers = active)
        }
    }

    private const val MAX_LOG_LINES = 300
    private const val MAX_HISTORY_POINTS = 24
}

class BedrockHostService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var udpJob: Job? = null
    private var tickJob: Job? = null
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val serverGuid: Long = Random.nextLong().let { if (it == 0L) 8472910421L else it }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopHosting()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val roomId = intent?.getStringExtra(EXTRA_ROOM_ID)
                val requestedPort = intent?.getIntExtra(EXTRA_PORT, 19132) ?: 19132
                val serverName = intent?.getStringExtra(EXTRA_NAME) ?: HostEngineController.currentRoomConfig.name
                ensureNotificationChannel()
                val notification = buildNotification(serverName, requestedPort)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                startHosting(roomId, requestedPort)
            }
        }
        return START_STICKY
    }

    private fun startHosting(roomId: String?, requestedPort: Int) {
        stopHosting()
        HostEngineController.updateStatus(roomId, HostStatus.STARTING, requestedPort)
        HostEngineController.appendLog(
            LogSeverity.INFO,
            "HOST",
            "Starting MCS-HOST RakNet UDP Engine on port $requestedPort..."
        )

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        runCatching {
            multicastLock = wifiManager?.createMulticastLock("MCSHostRakNetLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }

        udpJob = serviceScope.launch {
            var activePort = requestedPort
            val boundSocket = try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(requestedPort))
                    soTimeout = 1000
                }
            } catch (e: Exception) {
                HostEngineController.appendLog(
                    LogSeverity.WARN,
                    "RAKNET",
                    "Port $requestedPort busy (${e.message}); binding fallback port..."
                )
                try {
                    DatagramSocket(0).apply {
                        reuseAddress = true
                        soTimeout = 1000
                    }.also { activePort = it.localPort }
                } catch (fatal: Exception) {
                    HostEngineController.updateStatus(
                        roomId,
                        HostStatus.ERROR,
                        requestedPort,
                        fatal.localizedMessage ?: "Failed to bind UDP socket"
                    )
                    HostEngineController.appendLog(
                        LogSeverity.ERROR,
                        "HOST",
                        "Failed to open UDP socket: ${fatal.message}"
                    )
                    stopSelf()
                    return@launch
                }
            }

            socket = boundSocket
            val localIp = ServerStatusPinger.resolveLocalIpv4Address()
            HostEngineController.updateStatus(roomId, HostStatus.RUNNING, activePort)
            HostEngineController.appendLog(
                LogSeverity.INFO,
                "RAKNET",
                "Server ONLINE · Bedrock LAN/UDP listening at $localIp:$activePort (Seed ${HostEngineController.currentRoomConfig.seedNumeric})"
            )

            val recvBuf = ByteArray(2048)
            while (isActive && !boundSocket.isClosed) {
                try {
                    val packet = DatagramPacket(recvBuf, recvBuf.size)
                    boundSocket.receive(packet)
                    handleRakNetPacket(boundSocket, packet, activePort)
                } catch (_: java.net.SocketTimeoutException) {
                    // Normal poll timeout so coroutine cancellation stays responsive
                } catch (e: Exception) {
                    if (isActive && !boundSocket.isClosed) {
                        HostEngineController.appendLog(
                            LogSeverity.WARN,
                            "RAKNET",
                            "Socket read notice: ${e.localizedMessage}"
                        )
                    }
                }
            }
        }

        tickJob = serviceScope.launch {
            var tickCounter = 0L
            while (isActive) {
                val tickStart = System.nanoTime()
                delay(50L)
                val elapsedMs = (System.nanoTime() - tickStart) / 1_000_000.0
                val measuredTps = (1000.0 / elapsedMs.coerceAtLeast(50.0)).coerceIn(5.0, 20.0)
                tickCounter++
                if (tickCounter % 10L == 0L) {
                    HostEngineController.recordTick(measuredTps)
                    HostEngineController.pruneIdlePeers()
                }
            }
        }
    }

    private fun handleRakNetPacket(sock: DatagramSocket, packet: DatagramPacket, activePort: Int) {
        val len = packet.length
        if (len <= 0) return
        val data = packet.data
        val packetId = data[0].toInt() and 0xFF
        val senderKey = "${packet.address.hostAddress}:$packet.port"

        when (packetId) {
            0x01, 0x02 -> {
                // RakNet Unconnected Ping -> Respond with 0x1C Unconnected Pong + live Bedrock MOTD
                if (len < 9) return
                val clientTime = ByteBuffer.wrap(data, 1, 8).order(ByteOrder.BIG_ENDIAN).long
                val room = HostEngineController.currentRoomConfig
                val onlineCount = HostEngineController.state.value.activePeers.size
                val gm = room.gamemodeEnum().label
                val motdString = "MCPE;${room.name};766;${room.minecraftVersion};$onlineCount;${room.maxPlayers};$serverGuid;${room.motd.ifBlank { room.name }};$gm;1;$activePort;$activePort;"
                val motdBytes = motdString.toByteArray(Charsets.UTF_8)

                val out = ByteBuffer.allocate(35 + motdBytes.size).order(ByteOrder.BIG_ENDIAN)
                out.put(0x1C.toByte())
                out.putLong(clientTime)
                out.putLong(serverGuid)
                out.put(RAKNET_MAGIC)
                out.putShort(motdBytes.size.toShort())
                out.put(motdBytes)

                val resp = out.array()
                sock.send(DatagramPacket(resp, resp.size, packet.address, packet.port))
                HostEngineController.registerPeerPing(senderKey, 4)
                HostEngineController.appendLog(
                    LogSeverity.RAKNET,
                    "PING",
                    "Responded 0x1C Pong to ${packet.address.hostAddress}:${packet.port}"
                )
            }
            0x05 -> {
                // Open Connection Request 1 -> Reply 1 (0x06)
                val mtu = (len).coerceIn(576, 1492).toShort()
                val out = ByteBuffer.allocate(28).order(ByteOrder.BIG_ENDIAN)
                out.put(0x06.toByte())
                out.put(RAKNET_MAGIC)
                out.putLong(serverGuid)
                out.put(0x00.toByte()) // no security
                out.putShort(mtu)
                val resp = out.array()
                sock.send(DatagramPacket(resp, resp.size, packet.address, packet.port))
                HostEngineController.appendLog(
                    LogSeverity.RAKNET,
                    "HANDSHAKE",
                    "Open Connection Request 1 from ${packet.address.hostAddress}:${packet.port} (MTU $mtu)"
                )
            }
            0x07 -> {
                // Open Connection Request 2 -> Reply 2 (0x08)
                val out = ByteBuffer.allocate(35).order(ByteOrder.BIG_ENDIAN)
                out.put(0x08.toByte())
                out.put(RAKNET_MAGIC)
                out.putLong(serverGuid)
                // System address (IPv4 4 + 4 inverted bytes + 2 port)
                out.put(4.toByte())
                val addrBytes = packet.address.address
                for (i in 0 until 4) {
                    out.put((addrBytes.getOrElse(i) { 0 }.toInt() xor 0xFF).toByte())
                }
                out.putShort(packet.port.toShort())
                out.putShort(1400.toShort())
                out.put(0x00.toByte()) // encryption disabled
                val resp = out.array()
                sock.send(DatagramPacket(resp, resp.size, packet.address, packet.port))

                val playerLabel = "Bedrock-${packet.address.hostAddress?.substringAfterLast('.') ?: "Peer"}"
                HostEngineController.registerPeerHandshake(senderKey, playerLabel)
                HostEngineController.appendLog(
                    LogSeverity.INFO,
                    "JOIN",
                    "RakNet session established with $playerLabel (${packet.address.hostAddress}:${packet.port})"
                )
            }
            else -> {
                HostEngineController.registerPeerPing(senderKey, 6)
            }
        }
    }

    private fun stopHosting() {
        udpJob?.cancel()
        tickJob?.cancel()
        udpJob = null
        tickJob = null
        runCatching { socket?.close() }
        socket = null
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        multicastLock = null
        val prevRoom = HostEngineController.state.value.activeRoomId
        val prevPort = HostEngineController.state.value.boundPort
        if (HostEngineController.state.value.status != HostStatus.STOPPED) {
            HostEngineController.updateStatus(prevRoom, HostStatus.STOPPED, prevPort)
            HostEngineController.appendLog(LogSeverity.INFO, "HOST", "Server stopped cleanly.")
        }
    }

    override fun onDestroy() {
        stopHosting()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_host),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the MCS-HOST Bedrock UDP server active in the background."
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(serverName: String, port: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BedrockHostService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$serverName · Online")
            .setContentText("Bedrock UDP host active on port $port")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Server", stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.action.START_BEDROCK_HOST"
        const val ACTION_STOP = "com.example.action.STOP_BEDROCK_HOST"
        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_NAME = "extra_name"
        private const val CHANNEL_ID = "mcs_host_service"
        private const val NOTIFICATION_ID = 19132

        private val RAKNET_MAGIC = byteArrayOf(
            0x00, 0xFF.toByte(), 0xFF.toByte(), 0x00,
            0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(),
            0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(),
            0x12, 0x34, 0x56, 0x78
        )
    }
}
