package app.kholo.reader.core.log

interface LogTree {
    fun log(event: LogEvent)

    /** Kya is level ka message banana bhi chahiye? False par lambda chalta hi nahi. */
    fun isLoggable(level: LogLevel): Boolean = true
}
