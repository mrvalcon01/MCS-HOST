package com.example.data.network

import com.example.data.model.GameEdition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

data class ServerPingResult(
    val isOnline: Boolean,
    val latencyMs: Int,
    val motd: String,
    val versionName: String,
    val onlinePlayers: Int,
    val maxPlayers: Int,
    val errorMessage: String? = null
)

object ServerStatusPinger {
    private val RAKNET_MAGIC = byteArrayOf(
        0x00, 0xFF.toByte(), 0xFF.toByte(), 0x00,
        0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(),
        0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(),
        0x12, 0x34, 0x56, 0x78
    )

    suspend fun pingServer(host: String, port: Int, edition: GameEdition): ServerPingResult {
        return when (edition) {
            GameEdition.BEDROCK -> pingBedrock(host, port)
            GameEdition.JAVA -> pingJava(host, port)
        }
    }

    suspend fun pingBedrock(host: String, port: Int): ServerPingResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().ifBlank { "127.0.0.1" }
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = 3000
            val address = InetAddress.getByName(cleanHost)

            val buffer = ByteBuffer.allocate(33).order(ByteOrder.BIG_ENDIAN)
            buffer.put(0x01.toByte())
            buffer.putLong(System.currentTimeMillis())
            buffer.put(RAKNET_MAGIC)
            buffer.putLong(Random.nextLong())

            val startNs = System.nanoTime()
            val sendData = buffer.array()
            socket.send(DatagramPacket(sendData, sendData.size, address, port))

            val recvBuf = ByteArray(2048)
            val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
            socket.receive(recvPacket)
            val rttMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)

            val data = recvPacket.data
            if (recvPacket.length < 35 || data[0] != 0x1C.toByte()) {
                return@withContext ServerPingResult(
                    isOnline = false,
                    latencyMs = rttMs,
                    motd = "",
                    versionName = "",
                    onlinePlayers = 0,
                    maxPlayers = 0,
                    errorMessage = "Invalid RakNet pong packet"
                )
            }

            val pongBuf = ByteBuffer.wrap(data, 0, recvPacket.length).order(ByteOrder.BIG_ENDIAN)
            pongBuf.position(33)
            val strLen = pongBuf.short.toInt() and 0xFFFF
            val strBytes = ByteArray(strLen.coerceAtMost(recvPacket.length - 35))
            pongBuf.get(strBytes)
            val motdRaw = String(strBytes, Charsets.UTF_8)
            val parts = motdRaw.split(';')

            ServerPingResult(
                isOnline = true,
                latencyMs = rttMs,
                motd = parts.getOrNull(1).orEmpty().ifBlank { "Bedrock Server" },
                versionName = parts.getOrNull(3).orEmpty().ifBlank { "Bedrock" },
                onlinePlayers = parts.getOrNull(4)?.toIntOrNull() ?: 0,
                maxPlayers = parts.getOrNull(5)?.toIntOrNull() ?: 20
            )
        } catch (e: Exception) {
            ServerPingResult(
                isOnline = false,
                latencyMs = -1,
                motd = "",
                versionName = "",
                onlinePlayers = 0,
                maxPlayers = 0,
                errorMessage = e.localizedMessage ?: "Bedrock ping timed out"
            )
        } finally {
            runCatching { socket?.close() }
        }
    }

    suspend fun pingJava(host: String, port: Int): ServerPingResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().ifBlank { "127.0.0.1" }
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(cleanHost, port), 3500)
            socket.soTimeout = 3500

            val out = DataOutputStream(socket.getOutputStream())
            val inp = DataInputStream(socket.getInputStream())

            val handshakeBytes = ByteArrayOutputStream()
            val hsOut = DataOutputStream(handshakeBytes)
            writeVarInt(hsOut, 0x00)
            writeVarInt(hsOut, 767)
            val hostBytes = cleanHost.toByteArray(Charsets.UTF_8)
            writeVarInt(hsOut, hostBytes.size)
            hsOut.write(hostBytes)
            hsOut.writeShort(port)
            writeVarInt(hsOut, 1)

            val hsPayload = handshakeBytes.toByteArray()
            writeVarInt(out, hsPayload.size)
            out.write(hsPayload)

            writeVarInt(out, 1)
            writeVarInt(out, 0x00)
            out.flush()

            readVarInt(inp) // packet length
            val packetId = readVarInt(inp)
            if (packetId != 0x00) {
                throw IllegalStateException("Unexpected SLP packet ID: $packetId")
            }
            val jsonLength = readVarInt(inp).coerceIn(0, 65535)
            val jsonBytes = ByteArray(jsonLength)
            inp.readFully(jsonBytes)
            val json = JSONObject(String(jsonBytes, Charsets.UTF_8))

            // Measure ping RTT
            val pingStart = System.nanoTime()
            val pingPayload = ByteArrayOutputStream()
            val pingOut = DataOutputStream(pingPayload)
            writeVarInt(pingOut, 0x01)
            pingOut.writeLong(System.currentTimeMillis())
            val pingBytes = pingPayload.toByteArray()
            writeVarInt(out, pingBytes.size)
            out.write(pingBytes)
            out.flush()
            runCatching {
                readVarInt(inp)
                readVarInt(inp)
                inp.readLong()
            }
            val rttMs = ((System.nanoTime() - pingStart) / 1_000_000L).toInt().coerceAtLeast(1)

            val versionName = json.optJSONObject("version")?.optString("name").orEmpty()
            val playersObj = json.optJSONObject("players")
            val online = playersObj?.optInt("online") ?: 0
            val max = playersObj?.optInt("max") ?: 20
            val motd = when (val desc = json.opt("description")) {
                is JSONObject -> desc.optString("text").ifBlank { "Minecraft Server" }
                is String -> desc.ifBlank { "Minecraft Server" }
                else -> "Minecraft Server"
            }

            ServerPingResult(
                isOnline = true,
                latencyMs = rttMs,
                motd = motd.replace(Regex("§."), ""),
                versionName = versionName,
                onlinePlayers = online,
                maxPlayers = max
            )
        } catch (e: Exception) {
            ServerPingResult(
                isOnline = false,
                latencyMs = -1,
                motd = "",
                versionName = "",
                onlinePlayers = 0,
                maxPlayers = 0,
                errorMessage = e.localizedMessage ?: "Java ping failed"
            )
        } finally {
            runCatching { socket?.close() }
        }
    }

    fun resolveLocalIpv4Address(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val candidates = interfaces
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress }
            candidates.firstOrNull { it.isSiteLocalAddress }?.hostAddress
                ?: candidates.firstOrNull()?.hostAddress
                ?: "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }

    private fun writeVarInt(out: DataOutputStream, value: Int) {
        var v = value
        while (true) {
            if ((v and 0xFFFFFF80.toInt()) == 0) {
                out.writeByte(v)
                return
            }
            out.writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
    }

    private fun readVarInt(inp: DataInputStream): Int {
        var numRead = 0
        var result = 0
        var read: Int
        do {
            read = inp.readUnsignedByte()
            val value = read and 0x7F
            result = result or (value shl (7 * numRead))
            numRead++
            if (numRead > 5) throw IllegalStateException("VarInt is too big")
        } while ((read and 0x80) != 0)
        return result
    }
}
