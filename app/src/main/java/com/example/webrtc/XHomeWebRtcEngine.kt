package com.example.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import com.example.input.GamepadInputEngine
import com.example.model.GamepadState
import com.example.model.LogSeverity
import com.example.model.StreamAspectRatio
import com.example.model.StreamSettings
import com.example.model.TelemetrySnapshot
import com.example.network.IceCandidate as SignalingIceCandidate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.JavaI420Buffer
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Native Xbox Remote Play (xHome) WebRTC engine modeled directly on Greenlight /
 * unknownskl/xbox-xcloud-player (`controlV1`, `messageV1`, `chatV1`, `input 1.0`).
 *
 * Key optimizations for Galaxy A22 4G (Helio G80) & Galaxy Tab A7 (Snapdragon 662):
 * - Hardware MediaCodec H.264 decoding onto shared EglBase OpenGL surface (zero Chromium overhead).
 * - Strict USAGE_GAME / STREAM_MUSIC audio routing (never routes to call earpiece).
 * - Full Greenlight DataChannel authorizationRequest + keyframe/bitrate/characteristics handshakes.
 * - Low-overhead 60Hz binary Gamepad input loop with physicality flags & rumble callback.
 */
class XHomeWebRtcEngine(
    context: Context,
    private val scope: CoroutineScope,
    private val gamepadProvider: () -> GamepadState,
    private val onTelemetry: (TelemetrySnapshot) -> Unit,
    private val onVibration: (GamepadInputEngine.VibrationCommand) -> Unit,
    private val onLog: (tag: String, message: String, severity: LogSeverity) -> Unit,
    private val onConnectionClosed: (reason: String) -> Unit
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val eglBase: EglBase = EglBase.create()
    private val inputExecutor = Executors.newSingleThreadExecutor()
    private val isDisposed = AtomicBoolean(false)
    private val isClosing = AtomicBoolean(false)

    private val audioDeviceModule: JavaAudioDeviceModule
    private val peerFactory: PeerConnectionFactory

    @Volatile
    private var peerConnection: PeerConnection? = null

    @Volatile
    private var loopbackTxPeer: PeerConnection? = null

    @Volatile
    private var loopbackVideoSource: VideoSource? = null

    @Volatile
    private var inputChannel: DataChannel? = null

    @Volatile
    private var controlChannel: DataChannel? = null

    @Volatile
    private var messageChannel: DataChannel? = null

    @Volatile
    private var chatChannel: DataChannel? = null

    @Volatile
    private var remoteVideoTrack: VideoTrack? = null

    @Volatile
    private var remoteAudioTrack: AudioTrack? = null

    @Volatile
    private var attachedRenderer: SurfaceViewRenderer? = null

    @Volatile
    private var currentSettings: StreamSettings = StreamSettings()

    @Volatile
    private var isPhysicalPadActive: Boolean = false

    private val inputSequence = AtomicInteger(1)
    private val gatheredLocalCandidates = mutableListOf<SignalingIceCandidate>()
    private var iceGatheringSignal: CompletableDeferred<Unit>? = null

    private var statsJob: Job? = null
    private var inputLoopJob: Job? = null
    private var loopbackGeneratorJob: Job? = null

    @Volatile
    private var latestInputHz: Int = 0

    @Volatile
    private var lastSubmittedPadState: GamepadState = GamepadState()

    @Volatile
    private var activeIceType: String = "Checking"

    init {
        ensureFactoryInitialized(appContext)
        enforceSpeakerAudioMode()

        // USAGE_GAME + CONTENT_TYPE_MOVIE guarantees Android routes playback to speaker/headphones,
        // NEVER the top phone call earpiece!
        audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .createAudioDeviceModule()

        val options = PeerConnectionFactory.Options().apply {
            disableEncryption = false
            disableNetworkMonitor = false
        }

        peerFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    fun attachSurfaceRenderer(renderer: SurfaceViewRenderer, aspectRatio: StreamAspectRatio) {
        if (isDisposed.get()) return
        synchronized(this) {
            if (attachedRenderer !== renderer) {
                runCatching { renderer.init(eglBase.eglBaseContext, null) }
                renderer.setEnableHardwareScaler(true)
                renderer.setMirror(false)
                attachedRenderer = renderer
                remoteVideoTrack?.addSink(renderer)
            }
            applyScalingType(renderer, aspectRatio)
        }
    }

    fun updateRendererAspectRatio(renderer: SurfaceViewRenderer, aspectRatio: StreamAspectRatio) {
        applyScalingType(renderer, aspectRatio)
    }

    fun detachSurfaceRenderer(renderer: SurfaceViewRenderer) {
        synchronized(this) {
            runCatching { remoteVideoTrack?.removeSink(renderer) }
            if (attachedRenderer === renderer) {
                attachedRenderer = null
            }
            runCatching { renderer.release() }
        }
    }

    private fun applyScalingType(renderer: SurfaceViewRenderer, aspectRatio: StreamAspectRatio) {
        val scalingType = when (aspectRatio) {
            StreamAspectRatio.FIT -> RendererCommon.ScalingType.SCALE_ASPECT_FIT
            StreamAspectRatio.COVER -> RendererCommon.ScalingType.SCALE_ASPECT_FILL
            StreamAspectRatio.STRETCH -> RendererCommon.ScalingType.SCALE_ASPECT_FIT
        }
        runCatching {
            renderer.setScalingType(scalingType)
            renderer.requestLayout()
        }
    }

    suspend fun createXHomeOffer(
        settings: StreamSettings,
        physicalPadConnected: Boolean
    ): Result<Pair<String, List<SignalingIceCandidate>>> = runCatching {
        check(!isDisposed.get()) { "WebRTC engine already disposed" }
        currentSettings = settings
        isPhysicalPadActive = physicalPadConnected
        gatheredLocalCandidates.clear()
        val gatheringDone = CompletableDeferred<Unit>()
        iceGatheringSignal = gatheringDone

        val rtcConfig = buildRtcConfig()
        val pc = peerFactory.createPeerConnection(rtcConfig, createPeerObserver(gatheringDone))
            ?: error("Failed to create PeerConnection")
        peerConnection = pc

        // Receive-only transceivers prevent Android from opening mic capture (keeps speaker audio routing)
        pc.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )
        pc.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )

        createGreenlightDataChannels(pc)

        val offerConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        val rawOffer = pc.awaitCreateOffer(offerConstraints)
        val filteredSdp = SdpCodecFilter.enforceH264ProfileAndBitrate(rawOffer.description, settings)
        pc.awaitSetLocalDescription(SessionDescription(rawOffer.type, filteredSdp))

        // Wait briefly for local host LAN candidates to settle before posting to xHome
        withTimeoutOrNull(1800L) { gatheringDone.await() }
        val localList = synchronized(gatheredLocalCandidates) { gatheredLocalCandidates.toList() }
        onLog("WebRTC", "Local Offer ready (${settings.summaryLabel}, ${localList.size} ICE candidates)", LogSeverity.INFO)
        filteredSdp to localList
    }

    suspend fun setRemoteAnswerAndCandidates(
        answerSdp: String,
        remoteCandidates: List<SignalingIceCandidate>
    ): Result<Unit> = runCatching {
        val pc = peerConnection ?: error("PeerConnection missing")
        val filteredAnswer = SdpCodecFilter.enforceH264ProfileAndBitrate(answerSdp, currentSettings)
        pc.awaitSetRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, filteredAnswer))

        remoteCandidates.forEach { cand ->
            runCatching {
                pc.addIceCandidate(
                    IceCandidate(
                        cand.sdpMid.ifBlank { "0" },
                        cand.sdpMLineIndex.coerceAtLeast(0),
                        cand.sdp
                    )
                )
            }
        }
        startStatsPolling(pc)
        startInputSendLoop()
        onLog("WebRTC", "Applied remote Answer + ${remoteCandidates.size} ICE candidates", LogSeverity.SUCCESS)
    }

    suspend fun startLoopbackTest(
        settings: StreamSettings,
        physicalPadConnected: Boolean
    ): Result<Unit> = runCatching {
        check(!isDisposed.get()) { "WebRTC engine already disposed" }
        currentSettings = settings
        isPhysicalPadActive = physicalPadConnected

        val rxConfig = buildRtcConfig()
        val txConfig = buildRtcConfig()

        val txReady = CompletableDeferred<Unit>()
        lateinit var txPc: PeerConnection
        lateinit var rxPc: PeerConnection

        rxPc = peerFactory.createPeerConnection(
            rxConfig,
            object : SimplePeerObserver() {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate != null) {
                        runCatching { txPc.addIceCandidate(candidate) }
                    }
                }

                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        remoteVideoTrack = track
                        attachedRenderer?.let { renderer ->
                            runCatching { track.addSink(renderer) }
                        }
                    } else if (track is AudioTrack) {
                        remoteAudioTrack = track
                        runCatching { track.setVolume(currentSettings.audioVolume.toDouble()) }
                    }
                    enforceSpeakerAudioMode()
                }

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    if (newState == PeerConnection.IceConnectionState.CONNECTED ||
                        newState == PeerConnection.IceConnectionState.COMPLETED
                    ) {
                        enforceSpeakerAudioMode()
                    }
                }
            }
        ) ?: error("Failed to create loopback receiver PeerConnection")

        txPc = peerFactory.createPeerConnection(
            txConfig,
            object : SimplePeerObserver() {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate != null) {
                        runCatching { rxPc.addIceCandidate(candidate) }
                    }
                }

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                        txReady.complete(Unit)
                    }
                }
            }
        ) ?: error("Failed to create loopback sender PeerConnection")

        peerConnection = rxPc
        loopbackTxPeer = txPc

        // Create loopback DataChannel so input loop metrics & button echo work realistically
        val dcInit = DataChannel.Init().apply {
            ordered = true
            protocol = "1.0"
        }
        val dc = txPc.createDataChannel("input", dcInit)
        inputChannel = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer?) {}
        })

        // Keep loopback encoder lightweight (640x360) for smooth 60 FPS test on Helio G80 / SD 662
        val videoSource = peerFactory.createVideoSource(false)
        loopbackVideoSource = videoSource
        val localTrack = peerFactory.createVideoTrack("loopback_video", videoSource)
        txPc.addTrack(localTrack, listOf("loopback_stream"))

        val offer = txPc.awaitCreateOffer(MediaConstraints())
        val filteredOffer = SdpCodecFilter.enforceH264ProfileAndBitrate(offer.description, settings)
        txPc.awaitSetLocalDescription(SessionDescription(SessionDescription.Type.OFFER, filteredOffer))
        rxPc.awaitSetRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, filteredOffer))

        val answer = rxPc.awaitCreateAnswer(MediaConstraints())
        val filteredAnswer = SdpCodecFilter.enforceH264ProfileAndBitrate(answer.description, settings)
        rxPc.awaitSetLocalDescription(SessionDescription(SessionDescription.Type.ANSWER, filteredAnswer))
        txPc.awaitSetRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, filteredAnswer))

        startLoopbackFrameGenerator(videoSource, settings.targetFps.fps)
        startStatsPolling(rxPc)
        startInputSendLoop()
        withTimeoutOrNull(1500L) { txReady.await() }
        onLog("Loopback", "Hardware H.264 WebRTC Loopback active (${settings.targetFps.fps} FPS)", LogSeverity.SUCCESS)
    }

    fun applyLiveStreamSettings(
        settings: StreamSettings,
        physicalPadConnected: Boolean
    ) {
        val prev = currentSettings
        currentSettings = settings
        isPhysicalPadActive = physicalPadConnected

        runCatching {
            remoteAudioTrack?.setVolume(settings.audioVolume.coerceIn(0f, 2f).toDouble())
        }
        attachedRenderer?.let { applyScalingType(it, settings.aspectRatio) }

        if (prev.maxBitrateMbps != settings.maxBitrateMbps) {
            sendBitrateUpdate(settings.maxBitrateKbps)
        }
        if (prev.resolution != settings.resolution || prev.targetFps != settings.targetFps) {
            sendVideoChannelConfig(settings)
            sendMessageCharacteristics(settings)
            requestKeyframe(ifr = true)
        }
    }

    fun notifyImmediateInput() {
        val channel = inputChannel ?: return
        if (isDisposed.get() || isClosing.get() || channel.state() != DataChannel.State.OPEN) return
        val pad = gamepadProvider()
        if (pad != lastSubmittedPadState) {
            sendBinaryGamepadReport(channel, pad)
        }
    }

    private fun createGreenlightDataChannels(pc: PeerConnection) {
        val inputDc = pc.createDataChannel(
            "input",
            DataChannel.Init().apply {
                ordered = true
                protocol = "1.0"
            }
        )
        inputChannel = inputDc
        inputDc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                val st = inputDc.state()
                if (st == DataChannel.State.OPEN) {
                    onLog("Greenlight", "Input DataChannel opened — sending client metadata", LogSeverity.SUCCESS)
                    sendClientMetadata(inputDc)
                    sendBinaryGamepadReport(inputDc, gamepadProvider())
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                val buf = buffer ?: return
                val bytes = ByteArray(buf.data.remaining())
                buf.data.get(bytes)
                if (GamepadInputEngine.isServerMetadataPacket(bytes)) {
                    sendClientMetadata(inputDc)
                }
                GamepadInputEngine.parseVibrationPacket(bytes)?.let { cmd ->
                    onVibration(cmd)
                }
            }
        })

        val controlDc = pc.createDataChannel(
            "control",
            DataChannel.Init().apply {
                ordered = true
                protocol = "controlV1"
            }
        )
        controlChannel = controlDc
        controlDc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                if (controlDc.state() == DataChannel.State.OPEN) {
                    onLog("Greenlight", "Control DataChannel opened — authorizing stream", LogSeverity.SUCCESS)
                    sendControlAuthAndConfig()
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {}
        })

        val messageDc = pc.createDataChannel(
            "message",
            DataChannel.Init().apply {
                ordered = true
                protocol = "messageV1"
            }
        )
        messageChannel = messageDc
        messageDc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                if (messageDc.state() == DataChannel.State.OPEN) {
                    sendMessageHandshake()
                    sendMessageCharacteristics(currentSettings)
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                val buf = buffer ?: return
                val bytes = ByteArray(buf.data.remaining())
                buf.data.get(bytes)
                handleIncomingMessageJson(bytes.toString(Charsets.UTF_8))
            }
        })

        val chatDc = pc.createDataChannel(
            "chat",
            DataChannel.Init().apply {
                ordered = true
                protocol = "chatV1"
            }
        )
        chatChannel = chatDc
        chatDc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer?) {}
        })
    }

    private fun sendControlAuthAndConfig() {
        val ctrl = controlChannel ?: return
        if (ctrl.state() != DataChannel.State.OPEN) return

        sendTextMessage(
            ctrl,
            JSONObject().apply {
                put("message", "authorizationRequest")
                put("accessKey", GREENLIGHT_ACCESS_KEY)
            }.toString()
        )
        sendTextMessage(
            ctrl,
            JSONObject().apply {
                put("message", "gamepadChanged")
                put("gamepadIndex", 0)
                put("wasAdded", true)
            }.toString()
        )
        sendBitrateUpdate(currentSettings.maxBitrateKbps)
        sendVideoChannelConfig(currentSettings)
        requestKeyframe(ifr = false)
    }

    private fun sendBitrateUpdate(bitrateKbps: Int) {
        val ctrl = controlChannel ?: return
        if (ctrl.state() != DataChannel.State.OPEN) return
        sendTextMessage(
            ctrl,
            JSONObject().apply {
                put("message", "rateControlBitrateUpdate")
                put("bitratebps", bitrateKbps.coerceIn(1500, 25000) * 1000)
            }.toString()
        )
    }

    private fun sendVideoChannelConfig(settings: StreamSettings) {
        val ctrl = controlChannel ?: return
        if (ctrl.state() != DataChannel.State.OPEN) return
        sendTextMessage(
            ctrl,
            JSONObject().apply {
                put("message", "videoChannelConfigUpdate")
                put("maxVideoSsrc", 0)
                put("preferredWidth", settings.resolution.width)
                put("preferredHeight", settings.resolution.height)
                put("preferredFps", settings.targetFps.fps)
            }.toString()
        )
    }

    private fun requestKeyframe(ifr: Boolean) {
        val ctrl = controlChannel ?: return
        if (ctrl.state() != DataChannel.State.OPEN) return
        sendTextMessage(
            ctrl,
            JSONObject().apply {
                put("message", "videoKeyframeRequested")
                put("ifrRequested", ifr)
            }.toString()
        )
    }

    private fun sendMessageHandshake() {
        val msgDc = messageChannel ?: return
        if (msgDc.state() != DataChannel.State.OPEN) return
        sendTextMessage(
            msgDc,
            JSONObject().apply {
                put("type", "Handshake")
                put("version", "messageV1")
                put("id", UUID.randomUUID().toString())
                put("cv", "")
            }.toString()
        )
    }

    private fun sendMessageCharacteristics(settings: StreamSettings) {
        val msgDc = messageChannel ?: return
        if (msgDc.state() != DataChannel.State.OPEN) return
        val w = settings.resolution.width
        val h = settings.resolution.height
        val contentJson = JSONObject().apply {
            put("orientation", 0)
            put("dimensions", JSONObject().apply {
                put("widthInPixels", w)
                put("heightInPixels", h)
            })
            put("safeArea", JSONObject().apply {
                put("left", 0)
                put("top", 0)
                put("right", w)
                put("bottom", h)
                put("supportsSafeArea", false)
            })
            put("locale", "en-US")
        }.toString()

        sendTextMessage(
            msgDc,
            JSONObject().apply {
                put("type", "Message")
                put("content", contentJson)
                put("id", UUID.randomUUID().toString())
                put("target", "/streaming/characteristics")
                put("cv", "")
            }.toString()
        )
    }

    private fun handleIncomingMessageJson(raw: String) {
        val msgDc = messageChannel ?: return
        if (msgDc.state() != DataChannel.State.OPEN) return
        runCatching {
            val obj = JSONObject(raw)
            val type = obj.optString("type")
            val id = obj.optString("id")
            when {
                type.equals("TransactionStart", ignoreCase = true) && id.isNotBlank() -> {
                    sendTextMessage(
                        msgDc,
                        JSONObject().apply {
                            put("type", "TransactionComplete")
                            put("content", "{}")
                            put("id", id)
                            put("cv", obj.optString("cv", ""))
                        }.toString()
                    )
                }
                type.equals("Handshake", ignoreCase = true) -> {
                    sendTextMessage(
                        msgDc,
                        JSONObject().apply {
                            put("type", "HandshakeAck")
                            put("version", "messageV1")
                            put("id", id.ifBlank { UUID.randomUUID().toString() })
                            put("cv", "")
                        }.toString()
                    )
                }
            }
        }
    }

    private fun sendClientMetadata(channel: DataChannel) {
        val frame = GamepadInputEngine.encodeClientMetadataFrame(
            sequence = inputSequence.getAndIncrement(),
            timestampMs = System.currentTimeMillis().toDouble(),
            maxTouchPoints = 0
        )
        sendBinaryBuffer(channel, frame)
    }

    private fun sendBinaryGamepadReport(channel: DataChannel, state: GamepadState) {
        lastSubmittedPadState = state
        val frame = GamepadInputEngine.encodeBinaryFrame(
            state = state,
            sequence = inputSequence.getAndIncrement(),
            timestampMs = System.currentTimeMillis().toDouble(),
            reportPhysical = isPhysicalPadActive || true
        )
        sendBinaryBuffer(channel, frame)
    }

    private fun sendTextMessage(channel: DataChannel, json: String) {
        if (isDisposed.get() || isClosing.get()) return
        inputExecutor.execute {
            if (!isDisposed.get() && !isClosing.get() && channel.state() == DataChannel.State.OPEN) {
                runCatching {
                    val bytes = json.toByteArray(Charsets.UTF_8)
                    channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
                }
            }
        }
    }

    private fun sendBinaryBuffer(channel: DataChannel, bytes: ByteArray) {
        if (isDisposed.get() || isClosing.get()) return
        inputExecutor.execute {
            if (!isDisposed.get() && !isClosing.get() && channel.state() == DataChannel.State.OPEN) {
                runCatching {
                    channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), true))
                }
            }
        }
    }

    private fun startInputSendLoop() {
        inputLoopJob?.cancel()
        inputLoopJob = scope.launch(Dispatchers.Default) {
            var windowStartMs = System.currentTimeMillis()
            var sentFramesInWindow = 0
            var lastHeartbeatMs = 0L
            var wasNonNeutral = false

            while (isActive && !isDisposed.get() && !isClosing.get()) {
                val dc = inputChannel
                if (dc != null && dc.state() == DataChannel.State.OPEN) {
                    val state = gamepadProvider()
                    val nonNeutral = state.buttonsMask != 0 ||
                        abs(state.leftStickX) > 0.02f ||
                        abs(state.leftStickY) > 0.02f ||
                        abs(state.rightStickX) > 0.02f ||
                        abs(state.rightStickY) > 0.02f ||
                        state.leftTrigger > 0.02f ||
                        state.rightTrigger > 0.02f

                    val now = System.currentTimeMillis()
                    val changed = state != lastSubmittedPadState
                    val needsSend = changed || nonNeutral || wasNonNeutral || (now - lastHeartbeatMs >= 250L)

                    if (needsSend) {
                        sendBinaryGamepadReport(dc, state)
                        if (nonNeutral || changed) {
                            sentFramesInWindow++
                        }
                        lastHeartbeatMs = now
                        wasNonNeutral = nonNeutral
                    }

                    val elapsed = now - windowStartMs
                    if (elapsed >= 500L) {
                        latestInputHz = if (sentFramesInWindow > 0) {
                            ((sentFramesInWindow * 1000f) / elapsed.coerceAtLeast(1L)).toInt()
                        } else {
                            0
                        }
                        sentFramesInWindow = 0
                        windowStartMs = now
                    }
                }
                delay(16L)
            }
        }
    }

    private fun startStatsPolling(pc: PeerConnection) {
        statsJob?.cancel()
        statsJob = scope.launch(Dispatchers.IO) {
            var prevBytes = 0L
            var prevFrames = 0L
            var prevDecodeTimeSec = 0.0
            var prevTimestampUs = 0.0
            var zeroFpsStreak = 0

            while (isActive && !isDisposed.get() && !isClosing.get()) {
                delay(1000L)
                if (isDisposed.get() || isClosing.get()) break

                val snapshot = CompletableDeferred<TelemetrySnapshot>()
                runCatching {
                    pc.getStats { report ->
                        var bytesReceived = 0L
                        var framesDecoded = 0L
                        var totalDecodeTimeSec = 0.0
                        var sampleTimestampUs = 0.0
                        var packetsLost = 0L
                        var packetsReceived = 0L
                        var jitterSec = 0.0
                        var width = 0
                        var height = 0
                        var decoderImpl = "Hardware H.264"
                        var reportedFps = 0
                        var rttSec = 0.0
                        var localCandId = ""
                        val candidateTypes = mutableMapOf<String, String>()

                        report.statsMap.values.forEach { stat ->
                            when (stat.type) {
                                "inbound-rtp" -> {
                                    val kind = (stat.members["kind"] ?: stat.members["mediaType"])?.toString()
                                    if (kind == "video") {
                                        bytesReceived = (stat.members["bytesReceived"] as? Number)?.toLong() ?: 0L
                                        framesDecoded = (stat.members["framesDecoded"] as? Number)?.toLong() ?: 0L
                                        totalDecodeTimeSec = (stat.members["totalDecodeTime"] as? Number)?.toDouble() ?: 0.0
                                        jitterSec = (stat.members["jitter"] as? Number)?.toDouble() ?: 0.0
                                        packetsLost = (stat.members["packetsLost"] as? Number)?.toLong() ?: 0L
                                        packetsReceived = (stat.members["packetsReceived"] as? Number)?.toLong() ?: 0L
                                        width = (stat.members["frameWidth"] as? Number)?.toInt() ?: 0
                                        height = (stat.members["frameHeight"] as? Number)?.toInt() ?: 0
                                        reportedFps = (stat.members["framesPerSecond"] as? Number)?.toInt() ?: 0
                                        decoderImpl = stat.members["decoderImplementation"]?.toString()?.takeIf { it.isNotBlank() }
                                            ?: "Hardware H.264"
                                        sampleTimestampUs = stat.timestampUs
                                    }
                                }
                                "candidate-pair" -> {
                                    val nominated = stat.members["nominated"] as? Boolean == true
                                    val stateStr = stat.members["state"]?.toString()
                                    if (nominated || stateStr == "succeeded") {
                                        val rtt = (stat.members["currentRoundTripTime"] as? Number)?.toDouble()
                                        if (rtt != null && rtt > 0) rttSec = rtt
                                        val lid = stat.members["localCandidateId"]?.toString().orEmpty()
                                        if (lid.isNotBlank()) localCandId = lid
                                    }
                                }
                                "local-candidate" -> {
                                    val ctype = stat.members["candidateType"]?.toString().orEmpty()
                                    if (ctype.isNotBlank()) candidateTypes[stat.id] = ctype
                                }
                            }
                        }

                        val dtSec = if (prevTimestampUs > 0.0 && sampleTimestampUs > prevTimestampUs) {
                            (sampleTimestampUs - prevTimestampUs) / 1_000_000.0
                        } else {
                            1.0
                        }
                        val deltaBytes = (bytesReceived - prevBytes).coerceAtLeast(0L)
                        val deltaFrames = (framesDecoded - prevFrames).coerceAtLeast(0L)
                        val deltaDecodeSec = (totalDecodeTimeSec - prevDecodeTimeSec).coerceAtLeast(0.0)

                        val computedFps = if (reportedFps > 0) reportedFps else (deltaFrames / dtSec).toInt()
                        val computedBitrateKbps = ((deltaBytes * 8.0) / (dtSec * 1000.0)).toInt()
                        val decodeMs = if (deltaFrames > 0) ((deltaDecodeSec * 1000.0) / deltaFrames).toFloat() else 0f
                        val totalPkts = (packetsReceived + packetsLost).coerceAtLeast(1L)
                        val lossPct = (packetsLost * 100f) / totalPkts.toFloat()

                        prevBytes = bytesReceived
                        prevFrames = framesDecoded
                        prevDecodeTimeSec = totalDecodeTimeSec
                        prevTimestampUs = sampleTimestampUs

                        if (localCandId.isNotBlank()) {
                            activeIceType = candidateTypes[localCandId] ?: activeIceType
                        }

                        val resText = if (width > 0 && height > 0) {
                            "${width}x${height}"
                        } else {
                            "${currentSettings.resolution.width}x${currentSettings.resolution.height}"
                        }

                        snapshot.complete(
                            TelemetrySnapshot(
                                fps = computedFps,
                                bitrateKbps = computedBitrateKbps,
                                jitterMs = (jitterSec * 1000.0).toFloat(),
                                packetLossPct = lossPct.coerceIn(0f, 100f),
                                decodeTimeMs = decodeMs,
                                rttMs = (rttSec * 1000.0).toInt(),
                                decoderName = decoderImpl,
                                resolutionLabel = resText,
                                iceCandidateType = activeIceType,
                                inputHz = latestInputHz
                            )
                        )
                    }
                }

                val snap = withTimeoutOrNull(800L) { snapshot.await() }
                if (snap != null && !isDisposed.get() && !isClosing.get()) {
                    onTelemetry(snap)
                    if (snap.fps == 0 && loopbackTxPeer == null) {
                        zeroFpsStreak++
                        if (zeroFpsStreak == 3 || (zeroFpsStreak > 3 && zeroFpsStreak % 4 == 0)) {
                            requestKeyframe(ifr = true)
                            sendVideoChannelConfig(currentSettings)
                        }
                    } else {
                        zeroFpsStreak = 0
                    }
                }
            }
        }
    }

    private fun startLoopbackFrameGenerator(videoSource: VideoSource, targetFps: Int) {
        loopbackGeneratorJob?.cancel()
        val width = 640
        val height = 360
        loopbackGeneratorJob = scope.launch(Dispatchers.Default) {
            var tick = 0
            while (isActive && !isDisposed.get() && !isClosing.get()) {
                val frameMs = (1000L / targetFps.coerceIn(20, 60)).coerceAtLeast(16L)
                val i420 = JavaI420Buffer.allocate(width, height)
                val yBuf = i420.dataY
                val uBuf = i420.dataU
                val vBuf = i420.dataV
                val stripePos = (tick * 8) % width

                for (row in 0 until height) {
                    val rowOffset = row * i420.strideY
                    for (col in 0 until width) {
                        val isBand = (col in stripePos until (stripePos + 28).coerceAtMost(width)) ||
                            (row % 60 < 2) || (col % 80 < 2)
                        val luma = if (isBand) 210.toByte() else ((row * 40 / height) + 22).toByte()
                        yBuf.put(rowOffset + col, luma)
                    }
                }
                val uvH = height / 2
                val uvW = width / 2
                for (r in 0 until uvH) {
                    val uOff = r * i420.strideU
                    val vOff = r * i420.strideV
                    for (c in 0 until uvW) {
                        val isGreenBand = (c * 2) in stripePos until (stripePos + 28).coerceAtMost(width)
                        uBuf.put(uOff + c, if (isGreenBand) 88.toByte() else 128.toByte())
                        vBuf.put(vOff + c, if (isGreenBand) 72.toByte() else 128.toByte())
                    }
                }

                val timestampNs = System.nanoTime()
                val frame = VideoFrame(i420, 0, timestampNs)
                runCatching { videoSource.capturerObserver.onFrameCaptured(frame) }
                frame.release()
                tick++
                delay(frameMs)
            }
        }
    }

    private fun buildRtcConfig(): PeerConnection.RTCConfiguration {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        return PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
            enableCpuOveruseDetection = false
        }
    }

    private fun createPeerObserver(gatheringDone: CompletableDeferred<Unit>): PeerConnection.Observer {
        return object : SimplePeerObserver() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    val converted = SignalingIceCandidate(
                        sdpMid = candidate.sdpMid ?: "0",
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        sdp = candidate.sdp
                    )
                    synchronized(gatheredLocalCandidates) {
                        gatheredLocalCandidates.add(converted)
                        if (gatheredLocalCandidates.size >= 4 && !gatheringDone.isCompleted) {
                            gatheringDone.complete(Unit)
                        }
                    }
                }
            }

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                if (newState == PeerConnection.IceGatheringState.COMPLETE && !gatheringDone.isCompleted) {
                    gatheringDone.complete(Unit)
                }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        enforceSpeakerAudioMode()
                        scope.launch {
                            delay(350L)
                            enforceSpeakerAudioMode()
                            delay(800L)
                            enforceSpeakerAudioMode()
                        }
                        onLog("WebRTC", "xHome ICE connected ($newState)", LogSeverity.SUCCESS)
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        if (!isClosing.get() && !isDisposed.get()) {
                            onConnectionClosed("ICE connection lost with console")
                        }
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        if (!isClosing.get() && !isDisposed.get()) {
                            onConnectionClosed("Console closed the Remote Play session")
                        }
                    }
                    else -> {}
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    attachedRenderer?.let { renderer ->
                        runCatching { track.addSink(renderer) }
                    }
                } else if (track is AudioTrack) {
                    remoteAudioTrack = track
                    runCatching { track.setVolume(currentSettings.audioVolume.coerceIn(0f, 2f).toDouble()) }
                    enforceSpeakerAudioMode()
                }
            }
        }
    }

    private fun enforceSpeakerAudioMode() {
        val am = audioManager ?: return
        runCatching {
            am.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
        }
    }

    fun stopAndRelease() {
        if (!isClosing.compareAndSet(false, true)) return
        statsJob?.cancel()
        inputLoopJob?.cancel()
        loopbackGeneratorJob?.cancel()

        synchronized(this) {
            attachedRenderer?.let { renderer ->
                runCatching { remoteVideoTrack?.removeSink(renderer) }
                runCatching { renderer.release() }
                attachedRenderer = null
            }
            remoteVideoTrack = null
            remoteAudioTrack = null
        }

        runCatching { inputChannel?.close(); inputChannel?.dispose() }
        runCatching { controlChannel?.close(); controlChannel?.dispose() }
        runCatching { messageChannel?.close(); messageChannel?.dispose() }
        runCatching { chatChannel?.close(); chatChannel?.dispose() }
        inputChannel = null
        controlChannel = null
        messageChannel = null
        chatChannel = null

        runCatching { loopbackTxPeer?.close(); loopbackTxPeer?.dispose() }
        loopbackTxPeer = null
        runCatching { loopbackVideoSource?.dispose() }
        loopbackVideoSource = null

        runCatching { peerConnection?.close(); peerConnection?.dispose() }
        peerConnection = null

        if (isDisposed.compareAndSet(false, true)) {
            runCatching { audioDeviceModule.release() }
            runCatching { peerFactory.dispose() }
            runCatching { eglBase.release() }
            runCatching { inputExecutor.shutdownNow() }
        }
        enforceSpeakerAudioMode()
    }

    companion object {
        const val GREENLIGHT_ACCESS_KEY = "4BDB3609-C1F1-4195-9B37-FEFF45DA8B8E"

        @Volatile
        private var factoryInitialized = false

        private fun ensureFactoryInitialized(context: Context) {
            if (!factoryInitialized) {
                synchronized(this) {
                    if (!factoryInitialized) {
                        PeerConnectionFactory.initialize(
                            PeerConnectionFactory.InitializationOptions.builder(context)
                                .setEnableInternalTracer(false)
                                .createInitializationOptions()
                        )
                        factoryInitialized = true
                    }
                }
            }
        }
    }
}

private open class SimplePeerObserver : PeerConnection.Observer {
    override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(candidate: IceCandidate?) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
    override fun onAddStream(stream: MediaStream?) {}
    override fun onRemoveStream(stream: MediaStream?) {}
    override fun onDataChannel(dataChannel: DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
}

private suspend fun PeerConnection.awaitCreateOffer(constraints: MediaConstraints): SessionDescription {
    val deferred = CompletableDeferred<SessionDescription>()
    createOffer(
        object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) deferred.complete(desc)
                else deferred.completeExceptionally(IllegalStateException("Null SDP offer"))
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                deferred.completeExceptionally(IllegalStateException("createOffer failed: $err"))
            }
            override fun onSetFailure(err: String?) {}
        },
        constraints
    )
    return deferred.await()
}

private suspend fun PeerConnection.awaitCreateAnswer(constraints: MediaConstraints): SessionDescription {
    val deferred = CompletableDeferred<SessionDescription>()
    createAnswer(
        object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) deferred.complete(desc)
                else deferred.completeExceptionally(IllegalStateException("Null SDP answer"))
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                deferred.completeExceptionally(IllegalStateException("createAnswer failed: $err"))
            }
            override fun onSetFailure(err: String?) {}
        },
        constraints
    )
    return deferred.await()
}

private suspend fun PeerConnection.awaitSetLocalDescription(desc: SessionDescription) {
    val deferred = CompletableDeferred<Unit>()
    setLocalDescription(
        object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() { deferred.complete(Unit) }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                deferred.completeExceptionally(IllegalStateException("setLocalDescription failed: $err"))
            }
        },
        desc
    )
    deferred.await()
}

private suspend fun PeerConnection.awaitSetRemoteDescription(desc: SessionDescription) {
    val deferred = CompletableDeferred<Unit>()
    setRemoteDescription(
        object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() { deferred.complete(Unit) }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                deferred.completeExceptionally(IllegalStateException("setRemoteDescription failed: $err"))
            }
        },
        desc
    )
    deferred.await()
}
