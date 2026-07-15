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
        @Volatile var currentPackageName: String = ""
    }
    private val isExecuting = AtomicBoolean(false)

    override fun onServiceConnected() { super.onServiceConnected(); instance = this; Log.d("AetherBot", "Accessibility Connected!") }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg.isBlank()) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> currentPackageName = pkg
        }
    }
    fun executeSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Int) {
        if (!isExecuting.compareAndSet(false, true)) return
        try {
            if (startX < 0 || startY < 0 || endX < 0 || endY < 0) { isExecuting.set(false); return }
            val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
            val safeDuration = durationMs.toLong().coerceIn(200L, 1000L)
            val stroke = GestureDescription.StrokeDescription(path, 0, safeDuration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gesture: GestureDescription?) { isExecuting.set(false) }
                override fun onCancelled(gesture: GestureDescription?) { isExecuting.set(false) }
            }, null)
        } catch (e: Exception) { isExecuting.set(false) }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { super.onDestroy(); instance = null }
}
