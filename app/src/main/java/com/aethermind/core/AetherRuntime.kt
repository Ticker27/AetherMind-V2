package com.aethermind.core

import com.aethermind.vision.AetherNativeBridge
import kotlinx.coroutines.*

object AetherRuntime {
    
    private var runtimeJob: Job? = null
    var isActive = false

    fun start(screenWidth: Int, screenHeight: Int) {
        if (isActive) return
        isActive = true
        
        // 1. สั่ง C++ จอง Memory
        AetherNativeBridge.initEngine(screenWidth, screenHeight)

        runtimeJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                // 2. ถาม C++ ว่าพร้อมยิงไหม (ทุกๆ 16ms)
                val cmd = AetherNativeBridge.checkForShotCommand()
                
                if (cmd != null) {
                    // 3. ถ้าพร้อมยิง สั่ง Accessibility ลากยิงทันที
                    val accService = AetherAccessibilityService.instance
                    if (accService != null) {
                        withContext(Dispatchers.Main) {
                            accService.executeSwipe(
                                cmd[1], cmd[2], // startX, startY
                                cmd[3], cmd[4], // endX, endY
                                cmd[5].toInt()  // duration
                            )
                        }
                        // หน่วงเวลารอให้ยิงเสร็จ (กันการยิงซ้อนทำ)
                        delay(1000) 
                    }
                }
                delay(16) // รอเฟรมถัดไป (60 FPS)
            }
        }
    }

    fun stop() {
        isActive = false
        runtimeJob?.cancel()
    }
}
