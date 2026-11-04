package app.kholo.reader.debug

import app.kholo.reader.core.log.LogEvent
import app.kholo.reader.core.log.LogLevel
import app.kholo.reader.core.log.LogTree

/** Release me logcat par kuch nahi jata. Bug report ke events KholoApp ka ring buffer rakhta hai. */
class ReleaseLogTree : LogTree {
    override fun isLoggable(level: LogLevel): Boolean = false

    override fun log(event: LogEvent) = Unit
}
