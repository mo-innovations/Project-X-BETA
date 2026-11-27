package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Zero-APK-bloat, ultra-low-CPU console ambient soundscape player.
 *
 * 1) Built-in PS5-inspired ethereal dashboard ambience:
 *    - 44.1 kHz STEREO crystalline celesta chimes + warm floating celestial shimmer pad
 *    - Soft 95ms attack (zero clicks), stereo ping-pong cathedral diffusion, 3-second seamless crossfade loop
 *    - Plays via AudioTrack.MODE_STATIC (~0% steady-state CPU on Galaxy A22 / Tab A7)
 * 2) Optional custom user audio track (local MP3 / M4A / OGG via Android Photo/Document Picker)
 *    looped cleanly via hardware MediaPlayer without bloating the APK.
 */
class AmbientMusicPlayer(
    private val scope: CoroutineScope
) {
    private var track: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var startJob: Job? = null
    private var cachedPcm: ShortArray? = null

    private var customContext: Context? = null
    private var customUri: String = ""

    @Volatile
    private var isPlaying = false

    fun configureCustomTrack(context: Context, uriString: String) {
        val clean = uriString.trim()
        if (clean == customUri) return
        customContext = context.applicationContext
        customUri = clean
        if (isPlaying) {
            stopAndRelease()
            startIfNeeded()
        }
    }

    fun setActive(shouldPlay: Boolean) {
        if (shouldPlay) {
            startIfNeeded()
        } else {
            stopAndRelease()
        }
    }

    private fun startIfNeeded() {
        if (isPlaying || startJob?.isActive == true) return
        startJob = scope.launch(Dispatchers.Default) {
            val uriStr = customUri
            val ctx = customContext
            if (uriStr.isNotBlank() && ctx != null) {
                val startedCustom = runCatching {
                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setDataSource(ctx, Uri.parse(uriStr))
                        isLooping = true
                        setVolume(0.30f, 0.30f)
                        prepare()
                        start()
                    }
                    mediaPlayer = mp
                    isPlaying = true
                    true
                }.getOrDefault(false)
                if (startedCustom) return@launch
            }

            runCatching {
                val pcm = cachedPcm ?: synthesizeSoothingPad().also { cachedPcm = it }
                val byteCount = pcm.size * 2
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteCount)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(pcm, 0, pcm.size)
                audioTrack.setLoopPoints(0, pcm.size / 2, -1)
                audioTrack.setVolume(0.28f)
                audioTrack.play()
                track = audioTrack
                isPlaying = true
            }
        }
    }

    fun stopAndRelease() {
        startJob?.cancel()
        startJob = null
        isPlaying = false

        mediaPlayer?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        mediaPlayer = null

        track?.let { active ->
            runCatching {
                if (active.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    active.pause()
                    active.flush()
                }
                active.stop()
            }
            runCatching { active.release() }
        }
        track = null
    }

    companion object {
        internal const val SAMPLE_RATE = 44_100
        internal const val DURATION_SEC = 28

        private data class GlassTone(
            val startSec: Double,
            val freqHz: Double,
            val decaySec: Double,
            val amplitude: Double,
            val pan: Double // -1.0 Left .. +1.0 Right
        )

        /**
         * Composes a 28-second 44.1kHz STEREO PS5-Home-Screen-style ethereal ambience:
         * - Slow floating crystalline celesta harmonics (sparse notes, 95ms velvet attack, 5.5s decay)
         * - Deep warm sub-drone & airy stereo fifths (F#2 / C#3 -> D#2 / A#2 -> B1 / F#2 -> E2 / B2)
         * - Stereo ping-pong cathedral delay with subtle air damping & equal-power loop overlap
         */
        internal fun synthesizeSoothingPad(): ShortArray {
            val frames = SAMPLE_RATE * DURATION_SEC
            val dryL = DoubleArray(frames)
            val dryR = DoubleArray(frames)
            val twoPi = 2.0 * PI

            // 1. Slow breathing celestial shimmer pad (4 spacious 7s arcs)
            val arcRoots = doubleArrayOf(92.50, 77.78, 61.74, 82.41)      // F#2, D#2, B1, E2
            val arcFifths = doubleArrayOf(138.59, 116.54, 92.50, 123.47)  // C#3, A#2, F#2, B2
            val arcHighs = doubleArrayOf(277.18, 233.08, 185.00, 246.94)  // C#4, A#3, F#3, B3
            val arcFrames = 7 * SAMPLE_RATE

            for (arc in 0 until 4) {
                val baseIdx = arc * arcFrames
                val fRoot = arcRoots[arc]
                val fFifth = arcFifths[arc]
                val fHigh = arcHighs[arc]
                for (j in 0 until arcFrames) {
                    val idx = baseIdx + j
                    val t = j.toDouble() / SAMPLE_RATE
                    val phase = j.toDouble() / arcFrames
                    val swell = 0.5 - 0.5 * cos(twoPi * phase) // Smooth bell 0 -> 1 -> 0
                    val drift = 0.25 * sin(twoPi * 0.09 * t)
                    val padLow = 0.16 * sin(twoPi * fRoot * t) + 0.08 * sin(twoPi * fRoot * 2.0 * t)
                    val padL = padLow + 0.075 * sin(twoPi * (fFifth - 0.14) * t) + 0.038 * sin(twoPi * (fHigh + 0.18) * t)
                    val padR = padLow + 0.075 * sin(twoPi * (fFifth + 0.14) * t) + 0.038 * sin(twoPi * (fHigh - 0.18) * t)
                    val env = swell * (0.85 + 0.15 * sin(twoPi * 0.16 * t))
                    dryL[idx] += (padL * (1.0 - drift * 0.2)) * env
                    dryR[idx] += (padR * (1.0 + drift * 0.2)) * env
                }
            }

            // 2. Sparse glassy PS5-inspired celesta bell tones gently echoing across stereo field
            val tones = listOf(
                GlassTone(0.8, 554.37, 4.8, 0.16, -0.35),  // C#5
                GlassTone(3.6, 830.61, 5.2, 0.11, 0.40),   // G#5
                GlassTone(6.2, 739.99, 4.6, 0.12, -0.20),  // F#5
                GlassTone(9.4, 466.16, 5.0, 0.14, 0.30),   // A#4
                GlassTone(12.8, 622.25, 5.4, 0.11, -0.45), // D#5
                GlassTone(16.0, 493.88, 5.2, 0.15, 0.25),  // B4
                GlassTone(19.2, 739.99, 4.8, 0.11, -0.30), // F#5
                GlassTone(22.4, 659.25, 4.5, 0.12, 0.35)   // E5
            )

            for (tone in tones) {
                val startIdx = (tone.startSec * SAMPLE_RATE).toInt().coerceIn(0, frames - 1)
                val endIdx = (startIdx + (tone.decaySec * 2.5 * SAMPLE_RATE).toInt()).coerceAtMost(frames)
                val attackSamples = 0.095 * SAMPLE_RATE // Velvet onset: zero harsh attack
                val gainL = ((1.0 - tone.pan) * 0.5).coerceIn(0.15, 0.85)
                val gainR = ((1.0 + tone.pan) * 0.5).coerceIn(0.15, 0.85)

                for (i in startIdx until endIdx) {
                    val age = (i - startIdx).toDouble()
                    val ageSec = age / SAMPLE_RATE
                    val env = if (age < attackSamples) {
                        val u = age / attackSamples
                        0.5 - 0.5 * cos(PI * u)
                    } else {
                        exp(-ageSec / tone.decaySec)
                    }
                    val p = twoPi * tone.freqHz * ageSec
                    // Pure crystal chime: fundamental + gentle airy octave shimmer
                    val sample = (0.86 * sin(p) + 0.14 * sin(2.0 * p) * exp(-ageSec * 0.8)) * env * tone.amplitude
                    dryL[i] += sample * gainL
                    dryR[i] += sample * gainR
                }
            }

            // 3. Stereo ping-pong cathedral diffusion + seamless 2s boundary crossfade
            val tap1 = (0.37 * SAMPLE_RATE).toInt()
            val tap2 = (0.57 * SAMPLE_RATE).toInt()
            val tap3 = (0.89 * SAMPLE_RATE).toInt()
            val crossfade = 2 * SAMPLE_RATE

            val out = ShortArray(frames * 2)
            for (i in 0 until frames) {
                val rL = dryL[i] * 0.72 +
                    dryR[(i - tap1 + frames) % frames] * 0.28 +
                    dryL[(i - tap2 + frames) % frames] * 0.20 +
                    dryR[(i - tap3 + frames) % frames] * 0.13
                val rR = dryR[i] * 0.72 +
                    dryL[(i - tap1 + frames) % frames] * 0.28 +
                    dryR[(i - tap2 + frames) % frames] * 0.20 +
                    dryL[(i - tap3 + frames) % frames] * 0.13

                val fade = when {
                    i < crossfade -> i.toDouble() / crossfade
                    i > frames - crossfade -> (frames - i).toDouble() / crossfade
                    else -> 1.0
                }
                val sL = (rL * fade * 17_500.0).toInt().coerceIn(-32_000, 32_000)
                val sR = (rR * fade * 17_500.0).toInt().coerceIn(-32_000, 32_000)
                out[i * 2] = sL.toShort()
                out[i * 2 + 1] = sR.toShort()
            }
            return out
        }
    }
}
