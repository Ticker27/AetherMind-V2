package com.aethermind.core

import android.app.ActivityManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * ตรวจสอบแอพพื้นหน้า เพื่อให้บอทยิงได้เฉพาะเป้าหมาย (com.miniclip.eightballpool) เท่านั้น
 * ใช้หลายวิธีเรียงลำดับ ความน่าเชื่อถือสูง → ต่ำ:
 *   1) Live query จาก accessibility root (สะท้อนหน้าต่างพื้นหน้าจริง ณ เวลานั้น — ไม่พึ่ง event timing)
 *   2) ค่าที่ติดตามจาก event (TYPE_WINDOW_STATE_CHANGED)
 *   3) ActivityManager.getRunningAppProcesses (ไม่ต้องสิทธิ์พิเศษ best-effort)
 *   4) UsageStatsManager (ต้องให้สิทธิ์ Usage Access)
 */
object ForegroundAppDetector {
    const val TARGET_PACKAGE = "com.miniclip.eightballpool"

    fun getForegroundPackage(context: Context): String {
        // 1. Live query จาก accessibility root (วิธีหลัก — ไม่ติดกับว่า event จะฟIRE หรือไม่)
        val svc = AetherAccessibilityService.instance
        if (svc != null) {
            try {
                val live = svc.rootInActiveWindow?.packageName?.toString()
                if (!live.isNullOrBlank()) return live
            } catch (e: Exception) { /* root อาจ null/โยน exception บนบางเครื่อง */ }
            // 2. fallback: ค่าที่เก็บจาก event
            if (AetherAccessibilityService.currentPackageName.isNotBlank())
                return AetherAccessibilityService.currentPackageName
        }
        // 3. ActivityManager running processes (permission-free best-effort)
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.runningAppProcesses?.let { list ->
                for (p in list) {
                    if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && p.processName.isNotBlank())
                        return p.processName
                }
            }
        } catch (e: Exception) { }
        // 4. UsageStats fallback
        val us = getFromUsageStats(context)
        if (!us.isNullOrBlank()) return us
        return ""
    }

    fun isTargetForeground(context: Context): Boolean =
        getForegroundPackage(context) == TARGET_PACKAGE

    private fun getFromUsageStats(context: Context): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10_000, now)
            var recent: UsageStats? = null
            for (s in stats) {
                if (recent == null || s.lastTimeUsed > recent.lastTimeUsed) recent = s
            }
            recent?.packageName
        } catch (e: Exception) { null }
    }
}
