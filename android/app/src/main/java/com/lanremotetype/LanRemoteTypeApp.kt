package com.lanremotetype

import android.app.Application
import com.lanremotetype.util.SoundHelper

class LanRemoteTypeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SoundHelper.init(this)
    }
}
