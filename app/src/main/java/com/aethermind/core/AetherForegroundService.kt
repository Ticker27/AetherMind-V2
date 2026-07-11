package com.aethermind.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
import android.view.ViewGroup
import android.view.WindowManager
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
    private var overlayView: TextView? = null
    private var captureJob: Job? = null
    private var execJob: Job? = null

    private val screenWidth = 1080
    private val screenHeight = 1920
    private val pixelBuffer = ByteBuffer.allocateDirect(screenWidth * screenHeight * 4)
    private var isProcessing = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        showOverlayIndicator()
        Log.d("AetherFG", "Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        if (intent != null && intent.hasExtra("RESULT_CODE") && projection == null) {
            val resultCode = intent.getIntExtra("RESULT_CODE", 0)
            val data = intent.getParcelableExtra<Intent>("DATA")
            
            if (data != null) {
                try {
                    AetherNativeBridge.initEngine(screenWidth, screenHeight)
                    startScreenCapture(resultCode, data)
                    startExecutionLoop()
                    updateOverlayStatus("⚡ AETHER: SCANNING...")
                } catch (e: Exception) {
                    Log.e("AetherFG", "Init Failed", e)
                    updateOverlayStatus("❌ AETHER: ERROR")
                }
            }
        }
        return START_STICKY
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = projectionManager.getMediaProjection(resultCode, data)
            
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d("AetherFG", "Projection Stopped")
                    cleanup()
                }
            }, null)
            
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
            
            Log.d("AetherFG", "Screen Capture Started Successfully")
        } catch (e: SecurityException) {
            Log.e("AetherFG", "Security Exception", e)
            updateOverlayStatus("❌ AETHER: PERMISSION DENIED")
        } catch (e: Exception) {
            Log.e("AetherFG", "ScreenCapture Failed", e)
        }
    }

    private fun startExecutionLoop() {
        execJob?.cancel()
        execJob = scope.launch {
            while (isActive) {
                try {
                    val cmd = AetherNativeBridge.checkForShotCommand()
                    if (cmd != null) {
                        updateOverlayStatus("🎯 AETHER: SHOOTING!")
                        val accService = AetherAccessibilityService.instance
                        if (accService != null) {
                            withContext(Dispatchers.Main) {
                                accService.executeSwipe(cmd[1], cmd[2], cmd[3], cmd[4], cmd[5].toInt())
                            }
                            delay(3000)
                            updateOverlayStatus("⚡ AETHER: SCANNING...")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AetherFG", "Exec Loop Error", e)
                }
                delay(16)
            }
        }
    }

    private fun showOverlayIndicator() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = TextView(this).apply {
            text = "⚡ AETHER: STARTING..."
            setBackgroundColor(0x99000000.toInt())
            setTextColor(0xFF00FF00.toInt())
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(10, 5, 10, 5)
        }
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.TOP or Gravity.START
            x = 10
            y = 10
        }
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e("AetherFG", "Overlay Failed", e)
        }
    }

    private fun updateOverlayStatus(status: String) {
        scope.launch(Dispatchers.Main) {
            try {
                overlayView?.text = status
            } catch (e: Exception) {}
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
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun cleanup() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            projection?.stop()
        } catch (e: Exception) {}
        virtualDisplay = null
        imageReader = null
        projection = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        cleanup()
        try { windowManager?.removeView(overlayView) } catch (e: Exception) {}
        overlayView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
