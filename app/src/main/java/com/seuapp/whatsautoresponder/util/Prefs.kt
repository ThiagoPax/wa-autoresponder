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

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun toggleEnabled(ctx: Context): Boolean {
        val newVal = !isEnabled(ctx)
        setEnabled(ctx, newVal)
        return newVal
    }

    fun clearLog(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY_LOG).apply()
    }

    fun readLog(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LOG, "") ?: ""

    fun appendLog(ctx: Context, line: String) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val ts = sdf.format(Date())
        val entry = "[$ts] $line"
        val cur = readLog(ctx)
        val newLog = if (TextUtils.isEmpty(cur)) entry else "$cur\n$entry"
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_LOG, newLog).apply()
    }
}
