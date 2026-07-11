package com.aethermind

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aethermind.core.AetherRuntime

class MainActivity : ComponentActivity() {
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
                        onClick = { 
                            // เริ่มการทำงานของบอท (เป้าหมายหลัก)
                            AetherRuntime.start(1080, 1920) 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("START AETHER BOT")
                    }
                }
            }
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
