package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

class RconClient {
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: OutputStream? = null
    private val mutex = Mutex()
    private val nextRequestId = AtomicInteger(1)

    val isConnected: Boolean
        get() = socket?.let { it.isConnected && !it.isClosed } == true

    suspend fun connect(host: String, port: Int, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    disconnectInternal()
                    val cleanHost = host.trim()
                    require(cleanHost.isNotBlank()) { "Host address cannot be empty." }
                    require(port in 1..65535) { "Invalid RCON port: $port" }

                    val sock = Socket()
                    sock.connect(InetSocketAddress(cleanHost, port), 5000)
                    sock.soTimeout = 6000
                    socket = sock
                    input = DataInputStream(sock.getInputStream())
                    output = sock.getOutputStream()

                    val reqId = nextRequestId.getAndIncrement()
                    sendPacket(reqId, TYPE_AUTH, password)
                    val response = readPacket()
                    if (response.requestId == -1 || response.requestId != reqId) {
                        disconnectInternal()
                        throw IllegalStateException("RCON authentication failed: incorrect password.")
                    }
                    "Authenticated to RCON at $cleanHost:$port"
                }
            }
        }

    suspend fun executeCommand(command: String): Result<String> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    check(isConnected) { "RCON is not connected." }
                    val cleanCmd = command.trim().removePrefix("/")
                    require(cleanCmd.isNotBlank()) { "Command cannot be blank." }

                    val reqId = nextRequestId.getAndIncrement()
                    sendPacket(reqId, TYPE_COMMAND, cleanCmd)
                    val response = readPacket()
                    response.payload.ifBlank { "(Command executed with empty output)" }
                }
            }
        }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                disconnectInternal()
            }
        }
    }

    private fun disconnectInternal() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        input = null
        output = null
        socket = null
    }

    private fun sendPacket(requestId: Int, type: Int, payload: String) {
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val bodyLength = 4 + 4 + payloadBytes.size + 2
        val buffer = ByteBuffer.allocate(4 + bodyLength).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(bodyLength)
        buffer.putInt(requestId)
        buffer.putInt(type)
        buffer.put(payloadBytes)
        buffer.put(0.toByte())
        buffer.put(0.toByte())
        val out = output ?: throw IllegalStateException("RCON output stream unavailable")
        out.write(buffer.array())
        out.flush()
    }

    private fun readPacket(): RconPacket {
        val inp = input ?: throw IllegalStateException("RCON input stream unavailable")
        val lenBytes = ByteArray(4)
        inp.readFully(lenBytes)
        val length = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).int
        require(length in 10..65536) { "Invalid RCON packet length: $length" }

        val packetBytes = ByteArray(length)
        inp.readFully(packetBytes)
        val buffer = ByteBuffer.wrap(packetBytes).order(ByteOrder.LITTLE_ENDIAN)
        val requestId = buffer.int
        val type = buffer.int
        val payloadLen = (length - 10).coerceAtLeast(0)
        val payloadBytes = ByteArray(payloadLen)
        buffer.get(payloadBytes)
        return RconPacket(
            requestId = requestId,
            type = type,
            payload = String(payloadBytes, Charsets.UTF_8)
        )
    }

    private data class RconPacket(
        val requestId: Int,
        val type: Int,
        val payload: String
    )

    private companion object {
        const val TYPE_COMMAND = 2
        const val TYPE_AUTH = 3
    }
}
