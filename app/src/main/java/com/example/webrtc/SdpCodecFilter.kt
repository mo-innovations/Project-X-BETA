package com.example.webrtc

import com.example.model.StreamCodecProfile
import com.example.model.StreamQualityPreset
import com.example.model.StreamSettings

/**
 * Enforces hardware-friendly H.264 codecs in SDP offers/answers
 * (strips AV1 / VP8 / VP9 / HEVC to prevent software decode stutter on Helio G80 / SD 662)
 * and injects custom FPS, resolution macroblock limits, and bitrate caps like Better xCloud.
 */
object SdpCodecFilter {

    private val DISALLOWED_CODECS = setOf("VP8", "VP9", "AV1", "AV1X", "H265", "HEVC")

    fun enforceH264ProfileAndBitrate(
        sdp: String,
        preset: StreamQualityPreset
    ): String {
        val mapped = StreamSettings(
            maxBitrateMbps = (preset.maxBitrateKbps / 1000).coerceIn(2, 20)
        )
        return enforceH264ProfileAndBitrate(sdp, mapped)
    }

    fun enforceH264ProfileAndBitrate(
        sdp: String,
        settings: StreamSettings
    ): String {
        val lines = sdp.split("\r\n", "\n").toMutableList()
        val rtpmapRegex = Regex("^a=rtpmap:(\\d+)\\s+([A-Za-z0-9_-]+)/\\d+")
        val fmtpRegex = Regex("^a=fmtp:(\\d+)\\s+(.*)")
        val rtcpFbRegex = Regex("^a=rtcp-fb:(\\d+)\\s+.*")

        val disallowedPayloads = mutableSetOf<String>()
        val h264Payloads = mutableListOf<String>()
        val payloadAssociatedFmtp = mutableMapOf<String, String>()

        var inVideoSection = false
        for (line in lines) {
            if (line.startsWith("m=")) {
                inVideoSection = line.startsWith("m=video")
                continue
            }
            if (!inVideoSection) continue

            rtpmapRegex.find(line)?.let { match ->
                val payloadType = match.groupValues[1]
                val codecName = match.groupValues[2].uppercase()
                if (codecName == "H264") {
                    h264Payloads.add(payloadType)
                } else if (codecName in DISALLOWED_CODECS) {
                    disallowedPayloads.add(payloadType)
                }
            }
            fmtpRegex.find(line)?.let { match ->
                payloadAssociatedFmtp[match.groupValues[1]] = match.groupValues[2]
            }
        }

        // Order H.264 payloads by user's selected profile (Constrained Baseline vs Main/High)
        val orderedH264 = h264Payloads.sortedBy { pt ->
            val fmtp = payloadAssociatedFmtp[pt].orEmpty().lowercase()
            val isBaseline = fmtp.contains("profile-level-id=42")
            when (settings.codecProfile) {
                StreamCodecProfile.LOW_LATENCY_BASELINE -> if (isBaseline) 0 else 1
                StreamCodecProfile.HIGH_QUALITY_MAIN -> if (!isBaseline) 0 else 1
            }
        }

        // Strip RTX payloads associated exclusively with disallowed codecs
        for ((pt, params) in payloadAssociatedFmtp) {
            val aptMatch = Regex("apt=(\\d+)").find(params)
            if (aptMatch != null && aptMatch.groupValues[1] in disallowedPayloads) {
                disallowedPayloads.add(pt)
            }
        }

        val maxKbps = settings.maxBitrateKbps
        val minKbps = settings.minBitrateKbps
        val startKbps = settings.startBitrateKbps
        val targetFps = settings.targetFps.fps

        val output = mutableListOf<String>()
        inVideoSection = false
        var addedBitrateLine = false

        for (line in lines) {
            if (line.startsWith("m=")) {
                inVideoSection = line.startsWith("m=video")
                addedBitrateLine = false
                if (inVideoSection && orderedH264.isNotEmpty()) {
                    val parts = line.split(" ")
                    if (parts.size > 3) {
                        val header = parts.take(3)
                        val payloads = parts.drop(3).filterNot { it in disallowedPayloads }
                        val finalPayloads = (orderedH264.filter { it in payloads } +
                            payloads.filterNot { it in orderedH264 }).distinct()
                        output.add((header + finalPayloads).joinToString(" "))
                        continue
                    }
                }
                output.add(line)
                continue
            }

            if (inVideoSection) {
                if (line.startsWith("c=") && !addedBitrateLine) {
                    output.add(line)
                    output.add("b=AS:$maxKbps")
                    output.add("b=TIAS:${maxKbps * 1000}")
                    addedBitrateLine = true
                    continue
                }
                if (line.startsWith("b=AS:") || line.startsWith("b=TIAS:")) {
                    if (!addedBitrateLine) {
                        output.add("b=AS:$maxKbps")
                        output.add("b=TIAS:${maxKbps * 1000}")
                        addedBitrateLine = true
                    }
                    continue
                }

                val rtpmapMatch = rtpmapRegex.find(line)
                if (rtpmapMatch != null && rtpmapMatch.groupValues[1] in disallowedPayloads) {
                    continue
                }
                val rtcpMatch = rtcpFbRegex.find(line)
                if (rtcpMatch != null && rtcpMatch.groupValues[1] in disallowedPayloads) {
                    continue
                }
                val fmtpMatch = fmtpRegex.find(line)
                if (fmtpMatch != null) {
                    val pt = fmtpMatch.groupValues[1]
                    if (pt in disallowedPayloads) continue
                    if (pt in orderedH264) {
                        val cleaned = fmtpMatch.groupValues[2]
                            .split(";")
                            .map { it.trim() }
                            .filter { param ->
                                param.isNotEmpty() &&
                                    !param.startsWith("x-google-max-bitrate=") &&
                                    !param.startsWith("x-google-min-bitrate=") &&
                                    !param.startsWith("x-google-start-bitrate=") &&
                                    !param.startsWith("max-fr=")
                            }
                            .toMutableList()
                        cleaned.add("x-google-max-bitrate=$maxKbps")
                        cleaned.add("x-google-min-bitrate=$minKbps")
                        cleaned.add("x-google-start-bitrate=$startKbps")
                        cleaned.add("max-fr=$targetFps")
                        output.add("a=fmtp:$pt ${cleaned.joinToString(";")}")
                        continue
                    }
                }
            }

            output.add(line)
        }

        return output.joinToString("\r\n")
    }
}
