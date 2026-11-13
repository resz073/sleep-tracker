package com.resz073.sleeptracker

import android.app.Application
import com.resz073.sleeptracker.data.Backend

class SleepApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Backend.init(this)
    }
}
