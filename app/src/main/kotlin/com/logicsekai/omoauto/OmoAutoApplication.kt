// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto

import android.app.Application
import android.util.Log
import com.logicsekai.omoauto.common.OmoLog
import com.logicsekai.omoauto.data.update.UpdateManager
import com.logicsekai.omoauto.data.update.UpdateManagerOwner
import com.logicsekai.omoauto.streaming.ProjectionEngine
import com.logicsekai.omoauto.streaming.ProjectionEngineOwner

class OmoAutoApplication : Application(), ProjectionEngineOwner, UpdateManagerOwner {

    lateinit var container: AppContainer
        private set

    override val projectionEngine: ProjectionEngine
        get() = container.engine

    override val updateManager: UpdateManager
        get() = container.updates

    override fun onCreate() {
        super.onCreate()
        OmoLog.sink = OmoLog.Sink { level, tag, message, error ->
            when (level) {
                OmoLog.Level.DEBUG -> if (BuildConfig.DEBUG) Log.d(tag, message, error)
                OmoLog.Level.INFO -> Log.i(tag, message, error)
                OmoLog.Level.WARN -> Log.w(tag, message, error)
                OmoLog.Level.ERROR -> Log.e(tag, message, error)
            }
        }
        container = AppContainer(this)
        container.updates.start()
        OmoLog.i("OmoAuto", "OmoAuto ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) started")
    }
}
