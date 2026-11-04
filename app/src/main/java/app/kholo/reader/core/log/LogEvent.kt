package app.kholo.reader.core.log

enum class LogTag { OPEN, PDF, ZOOM, UNLOCK, TABLE, DOCX, CACHE, RECENTS, PERF }

enum class LogLevel { D, I, W, E }

data class LogEvent(
    val timeMillis: Long,
    val level: LogLevel,
    val tag: LogTag,
    val message: String,
)
