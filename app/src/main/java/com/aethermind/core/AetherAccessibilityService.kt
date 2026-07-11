package com.aethermind.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class AetherAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AetherAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AetherBot", "Accessibility Service Connected!")
    }

    // รับคำสั่งจาก C++ แล้วลากยิงทันที
    fun executeSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Int) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.toLong())
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gesture: GestureDescription?) {
                Log.d("AetherBot", "Shot Executed Successfully!")
            }
            override fun onCancelled(gesture: GestureDescription?) {
                Log.d("AetherBot", "Shot Cancelled")
            }
        }, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
