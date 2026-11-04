package app.kholo.reader

import android.app.Application
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.log.AppLog
import app.kholo.reader.core.log.LogLevel
import app.kholo.reader.core.log.RingBufferTree
import app.kholo.reader.debug.DebugHooksImpl

private const val REPORT_EVENT_CAPACITY = 200

class KholoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Bug report ke liye safe events. `d` isme nahi jata.
        val reportBuffer = RingBufferTree(REPORT_EVENT_CAPACITY, LogLevel.I)
        val hooks = DebugHooksImpl()
        AppLog.plant(reportBuffer, hooks.logTree)
        hooks.onAppCreate(this)
        container = AppContainer(logBuffer = reportBuffer)
    }
}
