package app.kholo.reader.core

import android.app.Application
import app.kholo.reader.core.log.LogTree

/** Debug tools ka darwaza. Release build me iski implementation khaali hoti hai. */
interface DebugHooks {
    val logTree: LogTree

    fun onAppCreate(app: Application)
}
