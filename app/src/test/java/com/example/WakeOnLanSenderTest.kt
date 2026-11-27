package com.example

import com.example.network.WakeOnLanSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeOnLanSenderTest {

    @Test
    fun `buildMagicPacket formats SmartGlass 0xDD 0x02 header and ASCII LiveID`() {
        val liveId = "FD00C24A91E73B10"
        val packet = WakeOnLanSender.buildMagicPacket(liveId)

        // Header: 0xDD, 0x02, 0x00, payloadLen (19), 0x00, 0x00
        assertEquals(0xDD.toByte(), packet[0])
        assertEquals(0x02.toByte(), packet[1])
        assertEquals(0x00.toByte(), packet[2])
        assertEquals(19.toByte(), packet[3])
        assertEquals(0x00.toByte(), packet[4])
        assertEquals(0x00.toByte(), packet[5])

        // Payload: 0x00, 16, ASCII bytes, 0x00
        assertEquals(0x00.toByte(), packet[6])
        assertEquals(16.toByte(), packet[7])
        val decodedLiveId = String(packet, 8, 16, Charsets.US_ASCII)
        assertEquals(liveId, decodedLiveId)
        assertEquals(0x00.toByte(), packet[packet.size - 1])
        assertEquals(25, packet.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildMagicPacket rejects invalid LiveID`() {
        WakeOnLanSender.buildMagicPacket("BAD!")
    }

    @Test
    fun `isValidLanIp validates IPv4 syntax`() {
        assertTrue(WakeOnLanSender.isValidLanIp("192.168.1.50"))
        assertTrue(WakeOnLanSender.isValidLanIp("10.0.0.12"))
        assertFalse(WakeOnLanSender.isValidLanIp("999.168.1.1"))
        assertFalse(WakeOnLanSender.isValidLanIp("xbox.local"))
    }
}
