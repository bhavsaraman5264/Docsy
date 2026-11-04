package app.kholo.reader.core.log

/**
 * App ka logger. Message lambda me hai, isliye log band ho to string banti hi nahi.
 *
 * Niyam: password, user ke details, file ka naam, poora URI, document ka text ya cell data
 * yahan kabhi nahi aayega, debug build me bhi nahi. Iske bajay `doc#3`, MIME, size aur time likho.
 *
 * Yeh function inline nahi hain: R8 ka `assumenosideeffects` rule `d` ke call ko release se hatata hai.
 */
object AppLog {
    @Volatile
    private var trees: List<LogTree> = emptyList()

    fun plant(vararg newTrees: LogTree) {
        trees = newTrees.toList()
    }

    fun d(tag: LogTag, message: () -> String) = write(LogLevel.D, tag, message)

    fun i(tag: LogTag, message: () -> String) = write(LogLevel.I, tag, message)

    fun w(tag: LogTag, message: () -> String) = write(LogLevel.W, tag, message)

    fun e(tag: LogTag, message: () -> String) = write(LogLevel.E, tag, message)

    private fun write(level: LogLevel, tag: LogTag, message: () -> String) {
        val active = trees.filter { it.isLoggable(level) }
        if (active.isEmpty()) return
        val event = LogEvent(System.currentTimeMillis(), level, tag, message())
        active.forEach { it.log(event) }
    }
}
