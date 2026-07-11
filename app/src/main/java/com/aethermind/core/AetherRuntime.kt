package com.aethermind.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.util.Log
import com.aethermind.vision.AetherNativeBridge
import kotlinx.coroutines.*

object AetherRuntime {
    
    private var runtimeJob: Job? = null
    private var screenManager: ScreenCaptureManager? = null
    var isActive = false

    fun start(context: Context, screenWidth: Int, screenHeight: Int, data: Intent) {
        if (isActive) return
        isActive = true
        
        // 1. สั่ง C++ จอง Memory
        AetherNativeBridge.initEngine(screenWidth, screenHeight)

        // 2. เริ่มจับภาพหน้าจอ
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(Activity.RESULT_OK, data)
        screenManager = ScreenCaptureManager(screenWidth, screenHeight)
        screenManager?.start(projection)

        // 3. ลูปตรวจสอบคำสั่งยิงจาก C++
        runtimeJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val cmd = AetherNativeBridge.checkForShotCommand()
                
                if (cmd != null) {
                    val accService = AetherAccessibilityService.instance
                    if (accService != null) {
                        Log.d("AetherBot", "Executing Shot!")
                        withContext(Dispatchers.Main) {
                            accService.executeSwipe(
                                cmd[1], cmd[2], 
                                cmd[3], cmd[4], 
                                cmd[5].toInt()
                            )
                        }
                        delay(2000) // รอ 2 วินาทีกันยิงซ้อนทำ
                    }
                }
                delay(16) // รอเฟรมถัดไป
            }
        }
    }

    fun stop() {
        isActive = false
        screenManager?.stop()
        screenManager = null
        runtimeJob?.cancel()
    }
}
