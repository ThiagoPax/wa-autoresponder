package com.seuapp.whatsautoresponder.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Prefs {
    private const val FILE = "tennis_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LOG = "log"
    private const val KEY_LOG_DATE = "log_date"
    private const val KEY_LAST_REPLY_DATE = "last_reply_date"
    private const val KEY_DAY_PREFIX = "day_"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // Monitoring state
    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    // Log management with auto-clear after 24h
    fun readLog(ctx: Context): String {
        clearLogIfOld(ctx)
        return prefs(ctx).getString(KEY_LOG, "") ?: ""
    }

    fun appendLog(ctx: Context, line: String) {
        clearLogIfOld(ctx)
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val ts = sdf.format(Date())
        val entry = "[$ts] $line"
        val cur = prefs(ctx).getString(KEY_LOG, "") ?: ""
        val newLog = if (cur.isEmpty()) entry else "$cur\n$entry"
        prefs(ctx).edit()
            .putString(KEY_LOG, newLog)
            .putString(KEY_LOG_DATE, todayDateString())
            .apply()
    }

    fun clearLog(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_LOG)
            .remove(KEY_LOG_DATE)
            .apply()
    }

    private fun clearLogIfOld(ctx: Context) {
        val logDate = prefs(ctx).getString(KEY_LOG_DATE, null)
        if (logDate != null && logDate != todayDateString()) {
            clearLog(ctx)
        }
    }

    // Reply tracking (one per calendar day)
    fun hasRepliedToday(ctx: Context): Boolean {
        val lastDate = prefs(ctx).getString(KEY_LAST_REPLY_DATE, null)
        return lastDate == todayDateString()
    }

    fun markRepliedToday(ctx: Context) {
        prefs(ctx).edit().putString(KEY_LAST_REPLY_DATE, todayDateString()).apply()
    }

    fun clearReplyFlag(ctx: Context) {
        prefs(ctx).edit().remove(KEY_LAST_REPLY_DATE).apply()
    }

    // Schedule configuration
    fun isDayEnabled(ctx: Context, dayKey: String): Boolean =
        prefs(ctx).getBoolean("${KEY_DAY_PREFIX}${dayKey}_enabled", false)

    fun setDayEnabled(ctx: Context, dayKey: String, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("${KEY_DAY_PREFIX}${dayKey}_enabled", enabled).apply()
    }

    fun getDayStart(ctx: Context, dayKey: String): String =
        prefs(ctx).getString("${KEY_DAY_PREFIX}${dayKey}_start", "") ?: ""

    fun setDayStart(ctx: Context, dayKey: String, value: String) {
        prefs(ctx).edit().putString("${KEY_DAY_PREFIX}${dayKey}_start", value).apply()
    }

    fun getDayEnd(ctx: Context, dayKey: String): String =
        prefs(ctx).getString("${KEY_DAY_PREFIX}${dayKey}_end", "") ?: ""

    fun setDayEnd(ctx: Context, dayKey: String, value: String) {
        prefs(ctx).edit().putString("${KEY_DAY_PREFIX}${dayKey}_end", value).apply()
    }

    // Get current day key
    fun getCurrentDayKey(): String {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "mon"
            Calendar.TUESDAY -> "tue"
            Calendar.WEDNESDAY -> "wed"
            Calendar.THURSDAY -> "thu"
            Calendar.FRIDAY -> "fri"
            Calendar.SATURDAY -> "sat"
            Calendar.SUNDAY -> "sun"
            else -> "mon"
        }
    }

    // Check if current time is within configured window for today
    fun isWithinSchedule(ctx: Context): Boolean {
        val dayKey = getCurrentDayKey()
        if (!isDayEnabled(ctx, dayKey)) return false

        val startStr = getDayStart(ctx, dayKey)
        val endStr = getDayEnd(ctx, dayKey)
        if (startStr.isBlank() || endStr.isBlank()) return false

        return try {
            val now = Calendar.getInstance()
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            val startParts = startStr.split(":")
            val endParts = endStr.split(":")
            if (startParts.size < 2 || endParts.size < 2) return false

            val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
            val endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()

            currentMinutes in startMinutes..endMinutes
        } catch (e: Exception) {
            false
        }
    }

    // Check if a specific hour is within configured window for today
    fun isHourWithinSchedule(ctx: Context, hour: Int, minute: Int = 0): Boolean {
        val dayKey = getCurrentDayKey()
        return isHourWithinScheduleForDay(ctx, dayKey, hour, minute)
    }
    
    // Check if a specific hour is within configured window for a specific day
    fun isHourWithinScheduleForDay(ctx: Context, dayKey: String, hour: Int, minute: Int = 0): Boolean {
        if (!isDayEnabled(ctx, dayKey)) return false

        val startStr = getDayStart(ctx, dayKey)
        val endStr = getDayEnd(ctx, dayKey)
        if (startStr.isBlank() || endStr.isBlank()) return false

        return try {
            val targetMinutes = hour * 60 + minute

            val startParts = startStr.split(":")
            val endParts = endStr.split(":")
            if (startParts.size < 2 || endParts.size < 2) return false

            val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
            val endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()

            targetMinutes in startMinutes..endMinutes
        } catch (e: Exception) {
            false
        }
    }

    private fun todayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
