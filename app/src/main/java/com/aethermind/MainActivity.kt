package com.aethermind

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aethermind.core.AetherAccessibilityService
import com.aethermind.core.AetherForegroundService

class MainActivity : ComponentActivity() {
    
    companion object { const val TARGET_PACKAGE = "com.miniclip.eightballpool" }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, AetherForegroundService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            Toast.makeText(this, "บอทเริ่มทำงานแล้ว! กำลังเปิดเกม...", Toast.LENGTH_SHORT).show()
            
            // หน่วงเวลา 1 วินาทีก่อนเปิดเกม (ให้ Service เริ่มทำงานก่อน)
            Thread {
                Thread.sleep(1000)
                launchGame()
            }.start()
        } else {
            Toast.makeText(this, "ยกเลิกการจับภาพหน้าจอ", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("AetherMind V2", style = MaterialTheme.typography.headlineLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Target: 8 Ball Pool", color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(onClick = { requestOverlayPermission() }, modifier = Modifier.fillMaxWidth()) {
                        Text("1. Request Overlay Permission")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(onClick = { openAccessibilitySettings() }, modifier = Modifier.fillMaxWidth()) {
                        Text("2. Open Accessibility Settings")
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { checkPermissionsAndStart() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("START AETHER BOT & GAME") }
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        // 1. เช็ค Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "กรุณาเปิดสิทธิ์ Overlay ก่อน (ปุ่มที่ 1)", Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }
        
        // 2. เช็ค Accessibility Permission
        if (AetherAccessibilityService.instance == null) {
            Toast.makeText(this, "กรุณาเปิดสิทธิ์ Accessibility ก่อน (ปุ่มที่ 2)", Toast.LENGTH_SHORT).show()
            openAccessibilitySettings()
            return
        }
        
        // 3. เช็คว่าเกมติดตั้งไว้ไหม
        if (!isGameInstalled()) {
            Toast.makeText(this, "กรุณาติดตั้ง 8 Ball Pool ก่อน", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 4. ทุกอย่างพร้อม ขอจับภาพหน้าจอ
        requestScreenCapture()
    }

    private fun requestOverlayPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun openAccessibilitySettings() { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

    private fun requestScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun isGameInstalled(): Boolean = try { packageManager.getPackageInfo(TARGET_PACKAGE, 0); true } catch (e: Exception) { false }

    private fun launchGame() {
        val intent = packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
