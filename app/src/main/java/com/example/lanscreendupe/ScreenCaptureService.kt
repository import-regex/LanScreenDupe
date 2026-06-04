package com.example.lanscreendupe

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import livekit.org.webrtc.*
import livekit.org.webrtc.audio.AudioDeviceModule
import livekit.org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScreenCaptureService : Service() {

    override fun onCreate() {
        super.onCreate()
        instance = this

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { //kill if falsely triggered
            android.os.Process.killProcess(android.os.Process.myPid())
            return START_NOT_STICKY
        }

        // Boost priority to the level standard for video processing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_VIDEO)
        }

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .createInitializationOptions()
        )

        HttpServer.startServer(this)
        startForegroundNotification()
        return START_NOT_STICKY
    }

    fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LanScreenDupe running")
            .setContentText("Capturing screen...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

            }
            startForeground(1, notification, type)
        } else {
            startForeground(1, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "ScreenCaptureChannel"
        private var peerConnection: PeerConnection? = null
        private var factory: PeerConnectionFactory? = null
        private var videoTrack: VideoTrack? = null
        private var videoSource: VideoSource? = null
        private var audioTrack: AudioTrack? = null
        private var audioSource: AudioSource? = null
        private var audioDeviceModule: AudioDeviceModule? = null
        private var dataChannel: DataChannel? = null
        private var surfaceTextureHelper: SurfaceTextureHelper? = null
        private var screenCapturer: VideoCapturer? = null
        private var eglBase: EglBase? = null
        private var firstTap: Boolean = false
        private var tapX: Float = 0.5f
        private var tapY: Float = 0.5f
        @Volatile var rcsOn: Boolean=false
        var instance: ScreenCaptureService? = null
        var projectionData: Intent? = null
        var activeBitrateMbps: Int = 5
        var activeFps: Int = 16
        var activeQuality: Float = 1.0f
        var activeAudioMode: String = "OFF" // "OFF", "MIC", "MIC_RAW"
        var isCoordinateLocked: Boolean = false

        fun getOffer(): String {
            stopScreenCapture(keepForeground = true)

            val srv = instance ?: return "Error: Service not running"
            val data = projectionData ?: return "Error: No projection permission"

            val offerLatch = CountDownLatch(1) //the offer gathering process needs to be forced synchronous
            var generatedSdp: String? = null

            val options = PeerConnectionFactory.Options().apply {
                disableNetworkMonitor = true
                networkIgnoreMask = 0
            }

            val eb = EglBase.create()
            eglBase = eb

            val factoryBuilder = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eb.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eb.eglBaseContext))

            if (activeAudioMode != "OFF") {
                val adm = JavaAudioDeviceModule.builder(srv)
                    //echo cancellation and noise suppression stop speaker audio from being captured into the microphone audio stream
                    .setUseHardwareAcousticEchoCanceler(activeAudioMode == "MIC")
                    .setUseHardwareNoiseSuppressor(activeAudioMode == "MIC")
                    .setUseStereoInput(true)
                    .setUseStereoOutput(true)
                    .createAudioDeviceModule()
                audioDeviceModule = adm
                factoryBuilder.setAudioDeviceModule(adm)
            }

            val f = factoryBuilder.createPeerConnectionFactory()
            factory = f

            if (activeAudioMode != "OFF") {
                val asrc = f.createAudioSource(MediaConstraints())
                audioSource = asrc
                val at = f.createAudioTrack("AUDIO_TRACK", asrc)
                audioTrack = at
            }

            factory = f
            val vs = f.createVideoSource(false)
            videoSource = vs

            val sth = SurfaceTextureHelper.create("CaptureThread", eb.eglBaseContext)
            surfaceTextureHelper = sth

            val capturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {})

            screenCapturer = capturer
            capturer.initialize(sth, srv, vs.capturerObserver)

            val wm = srv.getSystemService(WINDOW_SERVICE) as WindowManager
            val width: Int
            val height: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                width = bounds.width()
                height = bounds.height()
            } else {
                val metrics = DisplayMetrics()
                wm.defaultDisplay.getRealMetrics(metrics)
                width = metrics.widthPixels
                height = metrics.heightPixels
            }

            capturer.startCapture(width, height, activeFps)

            val vt = f.createVideoTrack("VIDEO_TRACK", vs)
            videoTrack = vt

            val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                iceCandidatePoolSize = 2  //this setting somehow allows the use of hotspot and bluetooth tethering networks started from the host device such as 10.107.x.x and 10.196.x.x
            }

            val pc = f.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver() {
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                    if (newState == PeerConnection.IceGatheringState.COMPLETE) {
                        generatedSdp = instance?.let { peerConnection?.localDescription?.description }
                        offerLatch.countDown()
                    }
                }
            }) ?: return "Error: Failed to create PeerConnection"

            peerConnection = pc

            val dc = pc.createDataChannel("tapChannel", DataChannel.Init())
            dataChannel = dc

            dc.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(l: Long) {}
                override fun onStateChange() {}
                override fun onMessage(buffer: DataChannel.Buffer) {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    val msg = String(bytes)

                    val parts = msg.split(",")
                    val type = if (parts.size == 3) parts[0] else "t"
                    val x = ((if (parts.size == 3) parts[1] else parts[0]).toFloatOrNull() ?: return) * width
                    val y = ((if (parts.size == 3) parts[2] else parts[1]).toFloatOrNull() ?: return) * height

                    if (isCoordinateLocked) {
                        if (!firstTap) {
                            tapX = x
                            tapY = y
                            firstTap = true
                        }
                        sendRCSCommand(srv, type, tapX, tapY)
                    } else {
                        sendRCSCommand(srv, type, x, y)
                    }
                }
            })

            pc.addTransceiver(
                vt,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, listOf("webRTCStream"))
            )

            audioTrack?.let {
                pc.addTransceiver(
                    it,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, listOf("webRTCStream"))
                )
            }

            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    val sdpDescription = sdp.description
                        .replace("a=rtcp-fb:.*nack\r\n".toRegex(), "") //disable NACK to skip dropped frames for lower stream latency
                        .replace("(a=fmtp:\\d+ \\S+)".toRegex(), "$1;x-google-max-keyframe-interval-ms=3000")
                    pc.setLocalDescription(SimpleSdpObserver(), SessionDescription(sdp.type, sdpDescription))
                }
            }, MediaConstraints())

            //release the latch that was coercing offer gathering to be synchronous
            if (!offerLatch.await(10, TimeUnit.SECONDS)) return "Error: ICE Gathering Timeout"
            return generatedSdp ?: "Error: SDP Generation Failed"
        }

        fun setAnswer(sdpAnswer: String) {
            val pc = peerConnection ?: return
            pc.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    applyBitrateSettings()
                }
            }, SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer))
        }

        fun sendRCSCommand(context: Context, type: String, x: Float = 0f, y: Float = 0f){
            context.sendBroadcast(Intent("com.example.lanscreendupe.TO_RCS").apply {
                putExtra("type", type)
                putExtra("x", x)
                putExtra("y", y)
                setPackage(context.packageName)
            })
        }

        private fun applyBitrateSettings() {
            val pc = peerConnection ?: return
            for (sender in pc.senders) {
                val track = sender.track()
                if (track != null && track.kind() == "video") {
                    val parameters = sender.parameters
                    for (encoding in parameters.encodings) {
                        encoding.maxBitrateBps = activeBitrateMbps * 1_000_000
                        encoding.minBitrateBps = 100_000
                        encoding.maxFramerate = activeFps
                        encoding.networkPriority = 3
                        encoding.numTemporalLayers = 1 //use a single stream for the frames data
                    }
                    parameters.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE
                    sender.parameters = parameters
                }
            }
        }

        private fun stopScreenCapture(keepForeground: Boolean = false) {
            dataChannel?.unregisterObserver()
            dataChannel?.dispose()
            dataChannel = null
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            screenCapturer = null
            videoTrack?.dispose()
            videoTrack = null
            videoSource?.dispose()
            videoSource = null
            audioTrack?.dispose()
            audioTrack = null
            audioSource?.dispose()
            audioSource = null
            audioDeviceModule?.release()
            audioDeviceModule = null
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
            peerConnection?.dispose()
            peerConnection = null
            factory?.dispose()
            factory = null
            eglBase?.release()
            eglBase = null
            firstTap = false
            if (!keepForeground) {
                instance?.let { srv ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        srv.stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        srv.stopForeground(true)
                    }
                }
            }
        }

    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Set to the absolute lowest priority (19) so the kernel ignores this process if anything else needs CPU
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST)
        // 2. Stop WebRTC and cleanup ScreenCapture resources
        stopScreenCapture()
        HttpServer.stopServer()
        // 3. Stop the service
        stopSelf()

        // Null out static references and suggest GC to the system
        instance = null
        projectionData = null
        //System.gc()
        //Runtime.getRuntime().gc()

        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenCapture()
        HttpServer.stopServer()
        instance = null
    }
}
//without "private" these open classes make the service a top class and impossible to properly wipe out on task removal
private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String) {}
    override fun onSetFailure(error: String) {}
}

private open class SimplePeerConnectionObserver : PeerConnection.Observer {
    override fun onIceCandidate(candidate: IceCandidate) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {}
    override fun onSignalingChange(newState: PeerConnection.SignalingState) {}
    override fun onAddStream(stream: MediaStream) {}
    override fun onRemoveStream(stream: MediaStream) {}
    override fun onDataChannel(dataChannel: DataChannel) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {}
    override fun onTrack(transceiver: RtpTransceiver) {}
}
