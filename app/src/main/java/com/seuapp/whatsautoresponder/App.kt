package com.seuapp.whatsautoresponder

import android.app.Application
import com.seuapp.whatsautoresponder.util.CrashHandler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
    }
}
