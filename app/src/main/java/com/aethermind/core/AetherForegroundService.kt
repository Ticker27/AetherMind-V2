package com.aethermind.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.aethermind.vision.AetherNativeBridge
import kotlinx.coroutines.*
import java.nio.ByteBuffer

class AetherForegroundService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var windowManager: WindowManager? = null
    private var panelView: android.view.View? = null
    private var debugTextView: TextView? = null
    private var botSwitch: Switch? = null
    private var execJob: Job? = null
    private var debugJob: Job? = null

    private var screenWidth = 1080
    private var screenHeight = 1920
    private var pixelBuffer: ByteBuffer? = null
    private var isProcessing = false
    private var isBotActive = true
    private var useShizuku = false
    private var frameCount = 0
    private var lastFrameCount = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        showDebugPanel()
        useShizuku = ShizukuActionExecutor.isAvailable()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        if (intent != null && intent.hasExtra("RESULT_CODE") && projection == null) {
            screenWidth = intent.getIntExtra("WIDTH", 1080)
            screenHeight = intent.getIntExtra("HEIGHT", 1920)
            pixelBuffer = ByteBuffer.allocateDirect(screenWidth * screenHeight * 4)
            val resultCode = intent.getIntExtra("RESULT_CODE", 0)
            val data = intent.getParcelableExtra<Intent>("DATA")
            if (data != null && pixelBuffer != null) {
                AetherNativeBridge.initEngine(screenWidth, screenHeight)
                startScreenCapture(resultCode, data)
                startExecutionLoop()
                startDebugLoop()
            }
        }
        return START_STICKY
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = projectionManager.getMediaProjection(resultCode, data)
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection?.createVirtualDisplay("AetherCapture", screenWidth, screenHeight, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader!!.surface, null, null)
            imageReader?.setOnImageAvailableListener({ reader ->
                if (isProcessing) return@setOnImageAvailableListener
                val image = reader?.acquireLatestImage() ?: return@setOnImageAvailableListener
                isProcessing = true
                try {
                    val plane = image.planes[0]; val buffer = plane.buffer; val rowStride = plane.rowStride; val rowSize = screenWidth * 4
                    var pos = 0; val tempRow = ByteArray(rowSize)
                    while (pos < screenHeight * rowSize && buffer.remaining() >= rowStride) {
                        buffer.get(tempRow, 0, rowSize); pixelBuffer?.put(tempRow)
                        if (rowStride > rowSize) buffer.position(buffer.position() + (rowStride - rowSize))
                        pos += rowSize
                    }
                    pixelBuffer?.rewind()
                    AetherNativeBridge.processScreenFrame(pixelBuffer!!, screenWidth, screenHeight)
                    frameCount++
                } catch (e: Exception) { Log.e("AetherFG", "Capture Error", e) } finally { image.close(); isProcessing = false }
            }, null)
        } catch (e: Exception) { Log.e("AetherFG", "ScreenCapture Failed", e) }
    }

    private fun startExecutionLoop() {
        execJob?.cancel()
        execJob = scope.launch {
            while (isActive) {
                try {
                    if (isBotActive) {
                        useShizuku = ShizukuActionExecutor.isGranted()
                        val cmd = AetherNativeBridge.checkForShotCommand()
                        if (cmd != null) {
                            if (useShizuku) {
                                ShizukuActionExecutor.executeSwipe(cmd[1], cmd[2], cmd[3], cmd[4], cmd[5].toInt())
                            } else {
                                val accService = AetherAccessibilityService.instance
                                if (accService != null) withContext(Dispatchers.Main) { accService.executeSwipe(cmd[1], cmd[2], cmd[3], cmd[4], cmd[5].toInt()) }
                            }
                            delay(4000)
                        }
                    }
                } catch (e: Exception) { Log.e("AetherFG", "Exec Loop Error", e) }
                delay(100)
            }
        }
    }

    // ลูปแสดง Debug Info แบบ Real-time (อัปเดตทุก 500ms)
    private fun startDebugLoop() {
        debugJob?.cancel()
        debugJob = scope.launch {
            while (isActive) {
                try {
                    val dbg = AetherNativeBridge.getDebugInfo()
                    val fps = (frameCount - lastFrameCount) * 2 // ทุก 500ms = x2 for per second
                    lastFrameCount = frameCount

                    val statusText = buildString {
                        appendLine("═══ AETHER DEBUG ═══")
                        appendLine("Mode: ${if (useShizuku) "SHIZUKU" else "ACCESSIBILITY"}")
                        appendLine("Screen: ${screenWidth}x${screenHeight}")
                        appendLine("Capture FPS: $fps")
                        appendLine("Frames: $frameCount")
                        appendLine("Bot: ${if (isBotActive) "ON" else "OFF"}")
                        appendLine("─────────────────")
                        if (dbg != null) {
                            appendLine("Cue Ball: ${if (dbg[0] == 1f) "FOUND (${dbg[2].toInt()},${dbg[3].toInt()})" else "NOT FOUND"}")
                            appendLine("Target:   ${if (dbg[1] == 1f) "FOUND (${dbg[4].toInt()},${dbg[5].toInt()})" else "NOT FOUND"}")
                            appendLine("Pocket:   ${if (dbg[6] >= 0) "#${dbg[6].toInt()}" else "N/A"}")
                            appendLine("─────────────────")
                            if (dbg[0] != 1f) appendLine("⚠️ NO CUE BALL DETECTED")
                            else if (dbg[1] != 1f) appendLine("⚠️ NO TARGET DETECTED")
                            else appendLine("✅ READY TO SHOOT")
                        }
                    }

                    withContext(Dispatchers.Main) {
                        debugTextView?.text = statusText
                    }
                } catch (e: Exception) { Log.e("AetherFG", "Debug Error", e) }
                delay(500)
            }
        }
    }

    private fun showDebugPanel() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD000000.toInt())
            setPadding(15, 10, 15, 10)
        }

        debugTextView = TextView(this).apply {
            text = "═══ AETHER DEBUG ═══\nStarting..."
            setTextColor(Color.GREEN)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            textAlignment = android.widget.TextView.TEXT_ALIGNMENT_TEXT_START
        }

        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 5, 0, 0)
        }
        val switchLabel = TextView(this).apply {
            text = "BOT: "
            setTextColor(Color.WHITE); textSize = 11f; typeface = Typeface.DEFAULT_BOLD
        }
        botSwitch = Switch(this).apply {
            isChecked = true
            setOnCheckedChangeListener { _, isChecked -> isBotActive = isChecked }
        }
        switchRow.addView(switchLabel)
        switchRow.addView(botSwitch)

        layout.addView(debugTextView)
        layout.addView(switchRow)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 5; y = 5 }

        try { windowManager?.addView(layout, params); panelView = layout } catch (e: Exception) {}
    }

    private fun createNotification(): Notification { return NotificationCompat.Builder(this, "aether_channel").setContentTitle("AetherMind V2").setContentText("Bot is running...").setSmallIcon(android.R.drawable.ic_menu_camera).setOngoing(true).build() }
    private fun createNotificationChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { val channel = NotificationChannel("aether_channel", "Aether Service", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager::class.java).createNotificationChannel(channel) } }
    override fun onDestroy() { super.onDestroy(); scope.cancel(); virtualDisplay?.release(); imageReader?.close(); projection?.stop(); try { windowManager?.removeView(panelView) } catch (e: Exception) {} }
    override fun onBind(intent: Intent?): IBinder? = null
}
