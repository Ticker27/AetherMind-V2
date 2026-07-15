package com.aethermind.core

import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuActionExecutor {
    fun isAvailable(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) true else { Shizuku.requestPermission(0); false }
        } catch (e: Exception) { Log.e("Shizuku", "Not running", e); false }
    }

    fun isGranted(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }
    }

    fun executeSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Int) {
        if (!isGranted()) { Log.e("Shizuku", "Cannot execute swipe"); return }
        try {
            // Humanizer: jitter จิ๋ว (±5px) ให้ไม่ตรงกระด้างเหมือนหุ่นยนต์ (คง reflection ไว้ ไม่พัง build)
            val jx = (Math.random() * 10f - 5f)
            val jy = (Math.random() * 10f - 5f)
            val sX = (startX + jx).toInt(); val sY = (startY + jy).toInt()
            val eX = (endX + jx).toInt(); val eY = (endY + jy).toInt()
            val dur = durationMs.coerceIn(200, 1000)
            val command = "input swipe $sX $sY $eX $eY $dur"

            // Shizuku.newProcess เป็น private ใน api:13.1.5 -> เรียกผ่าน reflection (ห้ามเรียกตรง else compile พัง)
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val error = errorReader.readText()
            process.waitFor()
            if (error.isNotEmpty()) Log.e("Shizuku", "Error: $error") else Log.d("Shizuku", "Swipe Executed! $command")
        } catch (e: Exception) { Log.e("Shizuku", "Execution Failed", e) }
    }
}
