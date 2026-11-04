package app.kholo.reader.debug

import android.app.Application
import app.kholo.reader.core.DebugHooks
import app.kholo.reader.core.log.LogTree

class DebugHooksImpl : DebugHooks {
    override val logTree: LogTree = ReleaseLogTree()

    override fun onAppCreate(app: Application) = Unit
}
