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
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
    private var panelView: View? = null
    private var statusText: TextView? = null
    private var botSwitch: Switch? = null
    private var execJob: Job? = null

    private val screenWidth = 1080
    private val screenHeight = 1920
    private val pixelBuffer = ByteBuffer.allocateDirect(screenWidth * screenHeight * 4)
    private var isProcessing = false
    private var isBotActive = true // สถานะเปิด/ปิดบอท

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        showControlPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        if (intent != null && intent.hasExtra("RESULT_CODE") && projection == null) {
            val resultCode = intent.getIntExtra("RESULT_CODE", 0)
            val data = intent.getParcelableExtra<Intent>("DATA")

            if (data != null) {
                AetherNativeBridge.initEngine(screenWidth, screenHeight)
                startScreenCapture(resultCode, data)
                startExecutionLoop()
                updateStatus("⚡ AETHER: SCANNING...", Color.GREEN)
            }
        }
        return START_STICKY
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = projectionManager.getMediaProjection(resultCode, data)

            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection?.createVirtualDisplay(
                "AetherCapture", screenWidth, screenHeight, 1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, null
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                if (isProcessing) return@setOnImageAvailableListener
                val image = reader?.acquireLatestImage() ?: return@setOnImageAvailableListener

                isProcessing = true
                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val rowStride = plane.rowStride
                    val rowSize = screenWidth * 4
                    var pos = 0
                    val tempRow = ByteArray(rowSize)
                    while (pos < screenHeight * rowSize && buffer.remaining() >= rowStride) {
                        buffer.get(tempRow, 0, rowSize)
                        pixelBuffer.put(tempRow)
                        if (rowStride > rowSize) { buffer.position(buffer.position() + (rowStride - rowSize)) }
                        pos += rowSize
                    }
                    pixelBuffer.rewind()
                    AetherNativeBridge.processScreenFrame(pixelBuffer, screenWidth, screenHeight)
                } catch (e: Exception) {
                    Log.e("AetherFG", "Capture Error", e)
                } finally {
                    image.close()
                    isProcessing = false
                }
            }, null)
        } catch (e: Exception) {
            Log.e("AetherFG", "ScreenCapture Failed", e)
        }
    }

    private fun startExecutionLoop() {
        execJob?.cancel()
        execJob = scope.launch {
            while (isActive) {
                try {
                    if (isBotActive) {
                        val cmd = AetherNativeBridge.checkForShotCommand()
                        if (cmd != null) {
                            updateStatus("🎯 AETHER: SHOOTING!", Color.YELLOW)
                            val accService = AetherAccessibilityService.instance
                            if (accService != null) {
                                withContext(Dispatchers.Main) {
                                    accService.executeSwipe(cmd[1], cmd[2], cmd[3], cmd[4], cmd[5].toInt())
                                }
                                delay(4000)
                                updateStatus("⚡ AETHER: SCANNING...", Color.GREEN)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AetherFG", "Exec Loop Error", e)
                }
                delay(100)
            }
        }
    }

    private fun showControlPanel() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC000000.toInt())
            setPadding(20, 10, 20, 10)
        }

        statusText = TextView(this).apply {
            text = "⚡ AETHER: IDLE"
            setTextColor(Color.GREEN)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        botSwitch = Switch(this).apply {
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                isBotActive = isChecked
                if (isChecked) {
                    updateStatus("⚡ AETHER: SCANNING...", Color.GREEN)
                } else {
                    updateStatus("⛔ AETHER: PAUSED", Color.RED)
                }
            }
        }

        layout.addView(statusText)
        layout.addView(botSwitch)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 50
        }

        windowManager?.addView(layout, params)
        panelView = layout
    }

    private fun updateStatus(text: String, color: Int) {
        scope.launch(Dispatchers.Main) {
            statusText?.text = text
            statusText?.setTextColor(color)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "aether_channel")
            .setContentTitle("AetherMind V2")
            .setContentText("Bot is running...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("aether_channel", "Aether Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        virtualDisplay?.release(); imageReader?.close(); projection?.stop()
        try { windowManager?.removeView(panelView) } catch (e: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
