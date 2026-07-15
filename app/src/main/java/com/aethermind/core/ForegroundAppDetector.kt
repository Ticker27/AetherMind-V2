package com.aethermind.core

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * ตรวจสอบแอพพื้นหน้า เพื่อให้บอทยิงได้เฉพาะเป้าหมาย (com.miniclip.eightballpool) เท่านั้น
 * - หลัก: ดึงจาก AetherAccessibilityService (รับ event เปลี่ยนหน้าต่าง — แอพนี้บังคับเปิด Accessibility อยู่แล้ว)
 * - รอง: UsageStatsManager (ทำงานได้หากผู้ใช้ให้สิทธิ์ Usage Access)
 */
object ForegroundAppDetector {
    const val TARGET_PACKAGE = "com.miniclip.eightballpool"

    fun getForegroundPackage(context: Context): String {
        val acc = AetherAccessibilityService.currentPackageName
        if (acc.isNotBlank()) return acc
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
