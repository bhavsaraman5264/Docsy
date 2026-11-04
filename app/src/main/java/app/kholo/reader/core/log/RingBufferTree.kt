package app.kholo.reader.core.log

/** Aakhri [capacity] events memory me. Disk par kuch nahi jata. */
class RingBufferTree(
    private val capacity: Int,
    private val minLevel: LogLevel = LogLevel.D,
) : LogTree {
    private val events = ArrayDeque<LogEvent>(capacity)

    override fun isLoggable(level: LogLevel): Boolean = level >= minLevel

    @Synchronized
    override fun log(event: LogEvent) {
        if (event.level < minLevel) return
        if (events.size == capacity) events.removeFirst()
        events.addLast(event)
    }

    @Synchronized
    fun snapshot(): List<LogEvent> = events.toList()
}
