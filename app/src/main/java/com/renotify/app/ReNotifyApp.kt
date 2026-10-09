package com.renotify.app

import android.app.Application
import com.renotify.app.data.Retention
import com.renotify.app.repost.Reposter
import com.renotify.app.rules.RulesEngine
import com.renotify.app.service.ListenerWatchdogWorker
import com.renotify.app.telemetry.Telemetry

class ReNotifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reposter.ensureChannel(this)
        RulesEngine.init(this)
        ListenerWatchdogWorker.schedule(this)
        Retention.schedule(this)
        Retention.pruneAsync(this)
        Telemetry.init(this)
    }
}
