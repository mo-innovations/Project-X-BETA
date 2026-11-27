package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

data class DiscoveredLanXbox(
    val ipAddress: String,
    val liveId: String?,
    val name: String?
)

data class WakeVerifyResult(
    val bytesSent: Int,
    val verifiedOnline: Boolean,
    val discoveredIp: String?,
    val verificationSource: String
)

object WakeOnLanSender {

    const val SMARTGLASS_WOL_PORT = 5050
    private val LIVE_ID_REGEX = Regex("^[A-Za-z0-9]{8,32}$")
    private val IPV4_REGEX = Regex("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$")

    /**
     * Builds an Xbox SmartGlass / xHome UDP power-on magic packet:
     * Header: 0xDD 0x02 0x00 <payloadLen> 0x00 0x00
     * Payload: 0x00 <liveIdLen> <ASCII LiveID bytes> 0x00
     */
    fun buildMagicPacket(liveId: String): ByteArray {
        val sanitized = liveId.trim().uppercase()
        require(LIVE_ID_REGEX.matches(sanitized)) {
            "Invalid Xbox LiveID format (expected 8-32 alphanumeric chars)"
        }
        val liveIdBytes = sanitized.toByteArray(Charsets.US_ASCII)
        val payload = ByteArray(liveIdBytes.size + 3)
        payload[0] = 0x00
        payload[1] = liveIdBytes.size.toByte()
        System.arraycopy(liveIdBytes, 0, payload, 2, liveIdBytes.size)
        payload[payload.size - 1] = 0x00

        val header = byteArrayOf(
            0xDD.toByte(),
            0x02,
            0x00,
            payload.size.toByte(),
            0x00,
            0x00
        )
        return header + payload
    }

    /**
     * Xbox SmartGlass Discovery Request (0xDD 0x00) on UDP:5050.
     * Powered-on Xbox consoles on the LAN reply with a 0xDD 0x01 discovery response.
     */
    fun buildDiscoveryPacket(): ByteArray = byteArrayOf(
        0xDD.toByte(), 0x00, // Type: Discovery Request
        0x00, 0x0A,          // Payload length: 10
        0x00, 0x00,          // Version: 0
        // Flags (4 bytes: 0)
        0x00, 0x00, 0x00, 0x00,
        // Client Type (2 bytes: 3 = Android) + MinVersion (2) + MaxVersion (2)
        0x00, 0x03, 0x00, 0x00, 0x00, 0x02
    )

    fun isValidLanIp(ip: String): Boolean = IPV4_REGEX.matches(ip.trim())

    fun getLocalBroadcastAddresses(): List<String> {
        val targets = linkedSetOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                iface.interfaceAddresses.forEach { addr ->
                    val broadcast = addr.broadcast
                    if (broadcast is Inet4Address) {
                        broadcast.hostAddress?.let { targets.add(it) }
                    }
                }
            }
        }
        targets.add("255.255.255.255")
        return targets.toList()
    }

    /**
     * Broadcasts + unicasts a SmartGlass Discovery ping on UDP:5050 and collects online Xbox responses.
     */
    suspend fun discoverConsolesOnLan(
        preferredIp: String = "",
        timeoutMs: Int = 1100
    ): List<DiscoveredLanXbox> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, DiscoveredLanXbox>()
        val probePacket = buildDiscoveryPacket()
        val destinations = buildList {
            val cleanPreferred = preferredIp.trim()
            if (isValidLanIp(cleanPreferred)) add(cleanPreferred)
            addAll(getLocalBroadcastAddresses())
        }.distinct()

        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = timeoutMs

                destinations.forEach { dest ->
                    runCatching {
                        val addr = InetAddress.getByName(dest)
                        socket.send(DatagramPacket(probePacket, probePacket.size, addr, SMARTGLASS_WOL_PORT))
                    }
                }

                val deadline = System.currentTimeMillis() + timeoutMs
                val recvBuf = ByteArray(1024)
                while (System.currentTimeMillis() < deadline) {
                    val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
                    try {
                        socket.receive(recvPacket)
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                    val senderIp = recvPacket.address?.hostAddress ?: continue
                    val len = recvPacket.length
                    if (len >= 6 && recvBuf[0] == 0xDD.toByte() && recvBuf[1] == 0x01.toByte()) {
                        val parsed = parseDiscoveryResponse(recvBuf, len, senderIp)
                        found[senderIp] = parsed
                    }
                }
            }
        }
        found.values.toList()
    }

    private fun parseDiscoveryResponse(data: ByteArray, length: Int, ipAddress: String): DiscoveredLanXbox {
        val ascii = String(data, 0, length, Charsets.US_ASCII)
        val liveIdMatch = Regex("FD[0-9A-Fa-f]{14}|[0-9A-Fa-f]{16}").find(ascii)?.value?.uppercase()
        val printableChunks = Regex("[A-Za-z0-9 _\\-]{3,32}")
            .findAll(ascii)
            .map { it.value.trim() }
            .filter { it != liveIdMatch && it.any { ch -> ch.isLetter() } }
            .toList()
        return DiscoveredLanXbox(
            ipAddress = ipAddress,
            liveId = liveIdMatch,
            name = printableChunks.firstOrNull()
        )
    }

    suspend fun sendWakePacket(
        targetIp: String,
        liveId: String,
        port: Int = SMARTGLASS_WOL_PORT
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanIp = targetIp.trim()
            val packetBytes = buildMagicPacket(liveId)
            val targets = buildList {
                if (isValidLanIp(cleanIp)) add(cleanIp)
                addAll(getLocalBroadcastAddresses())
            }.distinct()

            require(targets.isNotEmpty()) {
                "No valid IPv4 target or broadcast network available"
            }

            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 2000
                targets.forEach { dest ->
                    runCatching {
                        val address = InetAddress.getByName(dest)
                        val datagram = DatagramPacket(packetBytes, packetBytes.size, address, port)
                        socket.send(datagram)
                    }
                }
                delay(80L)
                targets.forEach { dest ->
                    runCatching {
                        val address = InetAddress.getByName(dest)
                        val datagram = DatagramPacket(packetBytes, packetBytes.size, address, port)
                        socket.send(datagram)
                    }
                }
            }
            packetBytes.size
        }
    }

    /**
     * Sends SmartGlass Wake-on-LAN and actively verifies whether the console actually responds
     * via LAN SmartGlass Discovery (UDP:5050) or Xbox xHome cloud power state.
     */
    suspend fun wakeAndVerify(
        targetIp: String,
        liveId: String,
        onProgress: suspend (String) -> Unit = {},
        cloudOnlineCheck: suspend () -> Boolean = { false }
    ): Result<WakeVerifyResult> = runCatching {
        val bytesSent = sendWakePacket(targetIp, liveId).getOrThrow()

        val maxAttempts = 5
        for (attempt in 1..maxAttempts) {
            onProgress("Checking console power ($attempt/$maxAttempts)...")
            delay(1500L)

            val discovered = discoverConsolesOnLan(preferredIp = targetIp, timeoutMs = 1000)
            val matching = discovered.firstOrNull {
                it.liveId.equals(liveId, ignoreCase = true) ||
                    (isValidLanIp(targetIp) && it.ipAddress == targetIp.trim())
            } ?: discovered.firstOrNull()

            if (matching != null) {
                return@runCatching WakeVerifyResult(
                    bytesSent = bytesSent,
                    verifiedOnline = true,
                    discoveredIp = matching.ipAddress,
                    verificationSource = "LAN UDP:5050"
                )
            }

            if (attempt >= 2 && cloudOnlineCheck()) {
                return@runCatching WakeVerifyResult(
                    bytesSent = bytesSent,
                    verifiedOnline = true,
                    discoveredIp = targetIp.trim().takeIf { isValidLanIp(it) },
                    verificationSource = "Xbox Live xHome"
                )
            }
        }

        WakeVerifyResult(
            bytesSent = bytesSent,
            verifiedOnline = false,
            discoveredIp = null,
            verificationSource = "No reply"
        )
    }
}
