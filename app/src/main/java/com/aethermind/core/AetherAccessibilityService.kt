package com.aethermind.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AetherAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AetherAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AetherBot", "Accessibility Service Connected!")
    }

    fun executeSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Int) {
        try {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.toLong())
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gesture: GestureDescription?) { Log.d("AetherBot", "Shot Executed!") }
                override fun onCancelled(gesture: GestureDescription?) { Log.d("AetherBot", "Shot Cancelled") }
            }, null)
        } catch (e: Exception) {
            Log.e("AetherBot", "Swipe Failed", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // ไม่ทำอะไร เพื่อไม่ให้เด้ง Dialog รบกวนเกม
    }
    
    override fun onInterrupt() {}
    
    override fun onDestroy() { 
        super.onDestroy()
        instance = null 
    }
}
