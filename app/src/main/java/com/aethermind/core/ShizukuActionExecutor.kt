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

    fun executeSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Int) {
        if (!isAvailable()) { Log.e("Shizuku", "Cannot execute swipe"); return }
        try {
            val command = "input swipe ${startX.toInt()} ${startY.toInt()} ${endX.toInt()} ${endY.toInt()} $durationMs"
            // Shizuku.newProcess(String[], String[], String) is private in api:13.1.5; invoke via reflection.
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
            if (error.isNotEmpty()) Log.e("Shizuku", "Error: $error") else Log.d("Shizuku", "Swipe Executed!")
        } catch (e: Exception) { Log.e("Shizuku", "Execution Failed", e) }
    }
}
