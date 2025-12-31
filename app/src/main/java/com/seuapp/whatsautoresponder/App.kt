package com.seuapp.whatsautoresponder

import android.app.Application
import com.seuapp.whatsautoresponder.util.LogBus

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        LogBus.init(this)
    }
}
