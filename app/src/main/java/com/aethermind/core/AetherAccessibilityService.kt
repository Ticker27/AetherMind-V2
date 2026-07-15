package com.aethermind.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean

class AetherAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AetherAccessibilityService? = null
    }

    // ล็อคเพื่อป้องกันการสั่งยิงซ้อนทำ (กันค้าง)
    private val isExecuting = AtomicBoolean(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AetherBot", "Accessibility Service Connected!")
    }

    fun executeSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Int) {
        // ถ้ากำลังยิงอยู่แล้ว ให้ข้ามไป
        if (!isExecuting.compareAndSet(false, true)) {
            Log.d("AetherBot", "Swipe skipped: Already executing")
            return
        }

        try {
            // ตรวจสอบพิกัดกันพัง ถ้าหลุดจอให้ยกเลิก
            if (startX < 0 || startY < 0 || endX < 0 || endY < 0) {
                Log.e("AetherBot", "Invalid coordinates, cancelling swipe")
                isExecuting.set(false)
                return
            }

            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }

            // จำกัด Duration ไม่ให้สั้นหรือยาวเกินไป
            val safeDuration = durationMs.toLong().coerceIn(200L, 1000L)

            val stroke = GestureDescription.StrokeDescription(path, 0, safeDuration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gesture: GestureDescription?) {
                    Log.d("AetherBot", "Shot Executed Successfully!")
                    isExecuting.set(false)
                }
                override fun onCancelled(gesture: GestureDescription?) {
                    Log.d("AetherBot", "Shot Cancelled")
                    isExecuting.set(false)
                }
            }, null)
        } catch (e: Exception) {
            Log.e("AetherBot", "Swipe Failed", e)
            isExecuting.set(false)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
