package com.blacksingularities.openroutine

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.blacksingularities.openroutine.data.Repo
import com.blacksingularities.openroutine.engine.Engine
import com.blacksingularities.openroutine.engine.MonitorService
import com.blacksingularities.openroutine.engine.Scheduler
import com.blacksingularities.openroutine.model.Fmt
import com.blacksingularities.openroutine.notify.Notifier
import java.util.concurrent.ConcurrentHashMap

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Engine.init(this)
        Notifier.createChannels(this)
        val labels = ConcurrentHashMap<String, String>()
        Fmt.appLabel = { pkg ->
            labels.getOrPut(pkg) {
                runCatching {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
                }.getOrDefault(pkg)
            }
        }
        syncBackground(this)
    }

    companion object {
        @Volatile
        var inForeground = false

        /** Po każdej zmianie rutyn: alarmy i usługa nasłuchu zgodne z bieżącymi ustawieniami. */
        fun syncBackground(ctx: Context) {
            runCatching { Scheduler.sync(ctx) }
            runCatching { MonitorService.sync(ctx) }
        }
    }
}
