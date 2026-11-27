package com.example

import com.example.model.StreamQualityPreset
import com.example.network.IceCandidatePrioritizer
import com.example.webrtc.SdpCodecFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.network.IceCandidate

class SdpCodecFilterTest {

    @Test
    fun `enforceH264ProfileAndBitrate strips AV1 VP8 VP9 and enforces bitrate`() {
        val sampleSdp = listOf(
            "v=0",
            "o=- 123 2 IN IP4 127.0.0.1",
            "s=-",
            "t=0 0",
            "m=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99 102",
            "c=IN IP4 0.0.0.0",
            "a=rtpmap:96 VP8/90000",
            "a=rtpmap:97 VP9/90000",
            "a=rtpmap:98 AV1/90000",
            "a=rtpmap:99 H264/90000",
            "a=fmtp:99 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f",
            "a=rtpmap:102 rtx/90000",
            "a=fmtp:102 apt=98"
        ).joinToString("\r\n")

        val filtered = SdpCodecFilter.enforceH264ProfileAndBitrate(
            sdp = sampleSdp,
            preset = StreamQualityPreset.FHD_1080P_60
        )

        assertTrue(filtered.contains("m=video 9 UDP/TLS/RTP/SAVPF 99"))
        assertFalse(filtered.contains("VP8/90000"))
        assertFalse(filtered.contains("VP9/90000"))
        assertFalse(filtered.contains("AV1/90000"))
        assertFalse(filtered.contains("apt=98"))
        assertTrue(filtered.contains("b=AS:18000"))
        assertTrue(filtered.contains("x-google-max-bitrate=18000"))
    }

    @Test
    fun `prioritizeLocalLan places matching LAN host candidate first`() {
        val relay = IceCandidate("0", 0, "candidate:1 1 udp 16777215 52.10.20.30 3478 typ relay")
        val srflx = IceCandidate("0", 0, "candidate:2 1 udp 1686052607 203.0.113.5 55100 typ srflx")
        val otherLan = IceCandidate("0", 0, "candidate:3 1 udp 2122260223 192.168.1.99 50000 typ host")
        val consoleLan = IceCandidate("0", 0, "candidate:4 1 udp 2122260223 192.168.1.50 50001 typ host")

        val sorted = IceCandidatePrioritizer.prioritizeLocalLan(
            candidates = listOf(relay, srflx, otherLan, consoleLan),
            preferredLanIp = "192.168.1.50"
        )

        assertEquals(consoleLan, sorted[0])
        assertEquals(otherLan, sorted[1])
        assertEquals(srflx, sorted[2])
        assertEquals(relay, sorted[3])
    }
}
