package com.seuapp.whatsautoresponder.util

import android.content.Context
import android.text.TextUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Prefs {
    private const val FILE = "wa_auto_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LOG = "log"
    private const val KEY_DAY_PREFIX = "day_"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun toggleEnabled(ctx: Context): Boolean {
        val newVal = !isEnabled(ctx)
        setEnabled(ctx, newVal)
        return newVal
    }

    fun clearLog(ctx: Context) {
        prefs(ctx).edit().remove(KEY_LOG).apply()
    }

    fun readLog(ctx: Context): String =
        prefs(ctx).getString(KEY_LOG, "") ?: ""

    fun appendLog(ctx: Context, line: String) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val ts = sdf.format(Date())
        val entry = "[$ts] $line"
        val cur = readLog(ctx)
        val newLog = if (TextUtils.isEmpty(cur)) entry else "$cur\n$entry"
        prefs(ctx).edit().putString(KEY_LOG, newLog).apply()
    }

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
}
