package com.example.lanscreendupe

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import java.net.InetAddress
import java.net.Inet4Address
import java.net.NetworkInterface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlin.text.toLongOrNull

class MainActivity : AppCompatActivity() {
    var statusTextView: TextView? = null
    var bitrateEdit: EditText? = null
    var bitrateToggleGroup: MaterialButtonToggleGroup? = null
    var fpsEdit: EditText? = null
    var fpsToggleGroup: MaterialButtonToggleGroup? = null
    var qualityText: TextView? = null
    var audioMode: String = "OFF"
    var durationEdit: EditText? = null
    var durationToggleGroup: MaterialButtonToggleGroup? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isReady = intent.getBooleanExtra("is_ready", false)
            val btn = findViewById<MaterialButton>(R.id.btnAccessibility) ?: return
            btn.strokeColor = ColorStateList.valueOf(if (isReady) 0xFF50FF50.toInt() else 0xFFFF3055.toInt())
            btn.text = if (isReady) "Remote Control Active" else "Grant Remote Control Permission"
            ScreenCaptureService.rcsOn=isReady
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] ?: false) {
            ScreenCaptureService.activeAudioMode= audioMode
        } else {
            statusTextView?.text = "Microphone permission required for audio!"
            findViewById<MaterialButtonToggleGroup>(R.id.audioToggleGroup)?.check(R.id.btnAudioOff)
        }
    }

    private val screenCapturePermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            ScreenCaptureService.projectionData = result.data
            val serviceIntent = Intent(this, ScreenCaptureService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            val ip = getLocalIpAddress()
            if (ip == null) {
                statusTextView?.text = "You need a valid wireless connection! http://localhost:8080/info for details."
            } else {
                statusTextView?.text = "Ready! Visit http://${ip}:8080\nOr http://${ip}:8080/info for more info"
            }
        } else {
            statusTextView?.text = "Cannot run without permission!"
        }
    }

    private fun getLocalIpAddress(): String? = try {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val cellularAddrs = mutableSetOf<InetAddress>()

        // 1. Identify Cellular IPs in case the user forgot 4g ON - prevents accidental use of cell towers as a router.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            cm.activeNetwork?.let { network ->
                if (cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
                    cm.getLinkProperties(network)?.linkAddresses?.forEach { cellularAddrs.add(it.address) }
                }
            }
        } else {
            cm.allNetworks.forEach { network ->
                if (cm.getNetworkInfo(network)?.type == ConnectivityManager.TYPE_MOBILE) {
                    cm.getLinkProperties(network)?.linkAddresses?.forEach { cellularAddrs.add(it.address) }
                }
            }
        }

        // 2. Hardware Scan: Return first non-cellular, non-loopback IPv4
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .filterNot { addr -> cellularAddrs.any { it == addr } }
            .mapNotNull { it.hostAddress }
            .firstOrNull()
    } catch (_: Exception) { null }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupSettingsListeners()

        ContextCompat.registerReceiver(
            this, statusReceiver, IntentFilter("com.example.lanscreendupe.FROM_RCS"),
            "com.example.lanscreendupe.RCS_CONTROL", null,
            ContextCompat.RECEIVER_EXPORTED
        )

        updateAccessibilityButton()

        if (savedInstanceState == null) {
            statusTextView?.text = "Waiting for permission..."

            //The swipe duration field is a workaround for older API levels, where detailed swipe motions only execute fully pre-defined paths and time durations.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                findViewById<android.view.View>(R.id.SwipeDurationContainer)?.visibility = android.view.View.GONE
            }
            //There were plans to implement system audio capture, but I couldn't make it work. The existing option for microphone capture with no echo cancellation is close enough
            //if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) { findViewById<android.view.View>(R.id.btnAudioSpk)?.visibility = android.view.View.GONE }
            screenCapturePermissionLauncher.launch((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent())
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityButton()
    }

    override fun onPause() {
        super.onPause()
    }
    private fun setSwipeDuration(value: Long){
        // According to the IDE you can "RemoteControlService.swipeDuration = value", but it doesn't actually work for a service that is a separate process
        ScreenCaptureService.sendRCSCommand(this, "set_swipe_duration", value.toFloat(), 0f)
    }
    private fun updateAccessibilityButton() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            ScreenCaptureService.sendRCSCommand(this, "get_status")
        } else {
            findViewById<MaterialButton>(R.id.btnAccessibility)?.apply {
                text = "Remote taps require Android 7+"
                isEnabled = false
            }
        }
    }

    private fun setupSettingsListeners() {
        statusTextView = findViewById(R.id.statusText)
        bitrateEdit = findViewById(R.id.customBitrate)
        bitrateToggleGroup = findViewById(R.id.bitrateToggleGroup)
        qualityText = findViewById(R.id.qualityDenominator)
        fpsEdit = findViewById(R.id.customFps)
        fpsToggleGroup = findViewById(R.id.fpsToggleGroup)
        durationEdit = findViewById(R.id.SwipeDurationInput)
        durationToggleGroup = findViewById(R.id.SwipeDurationToggleGroup)

        bitrateEdit?.doAfterTextChanged {
            val value = it?.toString()?.toIntOrNull() ?: 5
            ScreenCaptureService.activeBitrateMbps = value
        }
        fpsEdit?.doAfterTextChanged {
            val value = it?.toString()?.toIntOrNull() ?: 16
            ScreenCaptureService.activeFps = value
        }
        durationEdit?.doAfterTextChanged {
            val value = it?.toString()?.toLongOrNull() ?: 300L
            // According to the IDE you can "RemoteControlService.swipeDuration = value", but it doesn't actually work for a service that is a separate process
            setSwipeDuration(value)
        }

        bitrateToggleGroup?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val value = when (checkedId) {
                    R.id.btnBitrate1 -> "1"
                    R.id.btnBitrate2 -> "2"
                    R.id.btnBitrate5 -> "5"
                    R.id.btnBitrate16 -> "16"
                    R.id.btnBitrate32 -> "32"
                    else -> "" //intentionally invalid value
                }
                if (value.isNotEmpty()) { //only apply if value is valid
                    //the "" + isNotEmpty() procedure affirms values are set if ui is properly functioning
                    bitrateEdit?.setText(value)
                    ScreenCaptureService.activeBitrateMbps = value.toIntOrNull() ?: 5
                }
            }
        }

        fpsToggleGroup?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val value = when (checkedId) {
                    R.id.btnFps8 -> "8"
                    R.id.btnFps16 -> "16"
                    R.id.btnFps20 -> "20"
                    R.id.btnFps30 -> "30"
                    R.id.btnFps60 -> "60"
                    else -> ""
                }
                if (value.isNotEmpty()) {
                    fpsEdit?.setText(value)
                    ScreenCaptureService.activeFps = value.toIntOrNull() ?: 16
                }
            }
        }

        findViewById<MaterialButtonToggleGroup>(R.id.qualityToggleGroup).addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val value = when (checkedId) {
                    R.id.btnQual100 -> "1"
                    R.id.btnQual67 -> "1.5"
                    R.id.btnQual50 -> "2"
                    R.id.btnQual25 -> "4"
                    R.id.btnQual13 -> "8"
                    else -> ""
                }
                if (value.isNotEmpty()) {
                    qualityText?.text = value
                    ScreenCaptureService.activeQuality = value.toFloatOrNull() ?: 1.0f
                }
            }
        }

        findViewById<MaterialButtonToggleGroup>(R.id.audioToggleGroup).addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val value = when (checkedId) {
                    R.id.btnAudioMic -> "MIC"
                    R.id.btnAudioMicRaw -> "MIC_RAW"
                    R.id.btnAudioOff -> "OFF"
                    //R.id.btnAudioSpk -> "SPK" //Couldn't make system audio capture work (yet)
                    else -> ""
                }
                if (value.isNotEmpty()) {
                    if (value != "OFF" && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        audioPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    }
                    audioMode = value
                    ScreenCaptureService.activeAudioMode = value
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnAccessibility).setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        findViewById<MaterialButtonToggleGroup>(R.id.coordinateLocking).addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val locked = (checkedId == R.id.btnYes)
                ScreenCaptureService.isCoordinateLocked = locked
            }
        }

        durationToggleGroup?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val value = when (checkedId) {
                    R.id.Btn50 -> "50"
                    R.id.Btn300 -> "300"
                    R.id.Btn800 -> "800"
                    else -> ""
                }
                if (value.isNotEmpty()){
                    durationEdit?.setText(value)
                    setSwipeDuration(value.toLong())
                }

            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
        statusTextView = null
        bitrateEdit = null
        bitrateToggleGroup = null
        fpsEdit = null
        fpsToggleGroup = null
        qualityText = null
        durationEdit = null
        durationToggleGroup = null
    }
}
