package com.example.lanscreendupe

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class RemoteControlService : AccessibilityService() {

    private var currentPath: Path? = null
    private var currentStroke: GestureDescription.StrokeDescription? = null
    private var lastX = 0f
    private var lastY = 0f
    private var swipeDuration: Long = 300L
//this service runs as a separate process (android:process=":remote_control"). for this reason it requires Intent()-s to transmit data with the main process.
//the intents have to be broad enough to reach across process boundaries (RECEIVER_EXPORTED), yet restricted enough (android:protectionLevel="signature") to block other apps from reading or injecting inputs.
//in this app the communications correctly broadcast within the process group of the app, but are invisible to other processes.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val type = intent.getStringExtra("type") ?: return

            val x = intent.getFloatExtra("x", 0f)
            val y = intent.getFloatExtra("y", 0f)

            when (type) {
                "d" -> gestureDown(x, y)
                "m" -> gestureMove(x, y)
                "u" -> gestureUp(x, y)
                "t" -> tap(x, y) //currently not used
                "set_swipe_duration"-> swipeDuration = x.toLong()
                "get_status" -> sendStatus(true)
            }
            return
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    
    override fun onCreate() {
        super.onCreate()

        val filter = IntentFilter("com.example.lanscreendupe.TO_RCS")

        androidx.core.content.ContextCompat.registerReceiver(
            this,
            receiver,
            filter,
            //custom signature permission to keep the broadcasts private to this app
            "com.example.lanscreendupe.RCS_CONTROL",
            null,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try { noForeground()} catch (_: Exception) {}
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        instance = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        sendStatus(true)

        // Tested: it does indeed retain the process running even after swipe-away.
        // Ensure the service stays alive on task removal by becoming a foreground service.
        // Even with stopWithTask="false", systems often kill the process on swipe-away unless it's foreground.
        // Vendors make sensitive APIs fail without a status notification.
        startForeground(101, androidx.core.app.NotificationCompat.Builder(this, "RemoteControlChannel")
            .setContentTitle("Remote control permission")
            .setContentText("Preference persists✅")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        )

    }

    private fun sendStatus(ready: Boolean) {
        val intent = Intent("com.example.lanscreendupe.FROM_RCS").apply {
            putExtra("is_ready", ready)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
    private fun noForeground(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }
    override fun onUnbind(intent: Intent?): Boolean {
        sendStatus(false)
        // Release foreground status so the process can be killed if needed
        try { noForeground() } catch (_: Exception) {}
        instance = null
        return super.onUnbind(intent)
    }

    //single point tap
    private fun tap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path()

            path.moveTo(x,y)
            path.lineTo(x,y)

            dispatchGesture(
                GestureDescription.Builder().addStroke(
                    GestureDescription.StrokeDescription(path, 0, 50)
                ).build(),
                null,
                null
            )
        }
    }

    //detailed motion taps
    private fun gestureDown(x: Float, y: Float) {
        lastX = x
        lastY = y
        //api level 26+ allows for real time high detail swiping motions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 30, true) //down motion requires a long duration to be registered
            currentStroke = stroke
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
            //api level 24+ allows for coarse motions in fully pre-recorded/defined paths only.
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            currentPath = Path().apply { moveTo(x, y) }
        }
    }

    private fun gestureMove(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val prevStroke = currentStroke ?: return
            val path = Path().apply {
                moveTo(lastX, lastY)
                lineTo(x, y)
            }
            val nextStroke = prevStroke.continueStroke(path, 0, 1, true)
            currentStroke = nextStroke
            dispatchGesture(GestureDescription.Builder().addStroke(nextStroke).build(), null, null)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            currentPath?.lineTo(x, y)
        }

        lastX = x
        lastY = y
    }

    private fun gestureUp(x: Float, y: Float) {
        val x = x
        val y = y

        if (Build.VERSION.SDK_INT >=Build.VERSION_CODES.O) {
            val prevStroke = currentStroke ?: return
            val path = Path().apply {
                moveTo(lastX, lastY)
                lineTo(x, y)
            }
            //up motion should be >15ms to be safely accepted by the system, yet short enough not to kill momentum in scrolling menus
            val finalStroke = prevStroke.continueStroke(path, 0, 20, false)
            dispatchGesture(GestureDescription.Builder().addStroke(finalStroke).build(), null, null)
            currentStroke = null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = currentPath ?: return
            path.lineTo(x, y)
           dispatchGesture(
                GestureDescription.Builder().addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0,
                        swipeDuration)
                ).build(),
                null,
                null
            )

            currentPath = null
        }
    }

    companion object {
        var instance: RemoteControlService? = null
    }
}
