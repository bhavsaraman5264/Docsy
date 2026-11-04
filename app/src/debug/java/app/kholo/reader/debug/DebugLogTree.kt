package app.kholo.reader.debug

import android.util.Log
import app.kholo.reader.core.log.LogEvent
import app.kholo.reader.core.log.LogLevel
import app.kholo.reader.core.log.LogTree
import app.kholo.reader.core.log.RingBufferTree

private const val DEBUG_EVENT_CAPACITY = 500

/** Logcat me likhta hai, aur aakhri 500 logs Log screen ke liye memory me rakhta hai. */
class DebugLogTree : LogTree {
    val buffer = RingBufferTree(DEBUG_EVENT_CAPACITY)

    override fun log(event: LogEvent) {
        buffer.log(event)
        val tag = "Kholo.${event.tag}"
        when (event.level) {
            LogLevel.D -> Log.d(tag, event.message)
            LogLevel.I -> Log.i(tag, event.message)
            LogLevel.W -> Log.w(tag, event.message)
            LogLevel.E -> Log.e(tag, event.message)
        }
    }
}
