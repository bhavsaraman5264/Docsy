package app.kholo.reader.report

import android.content.Context
import java.io.File
import java.io.IOException

private const val FILE_NAME = "last_crash.txt"
private const val MAX_FRAMES = 40

/**
 * Crash ki chhoti report private storage me likhta hai, phir default handler chalata hai, taaki
 * Android vitals ko bhi crash mile. Exception ka message nahi likhte, usme URI ya file naam ho sakta hai.
 */
class CrashRecorder(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                file.writeText(safeTrace(error))
            } catch (_: IOException) {
                // Crash ke beech report na likhi jaye to bhi crash ko aage jane do.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(): String? = try {
        if (file.exists()) file.readText() else null
    } catch (_: IOException) {
        null
    }

    fun clear() {
        file.delete()
    }

    companion object {
        fun safeTrace(error: Throwable): String = buildString {
            var current: Throwable? = error
            while (current != null) {
                appendLine(current.javaClass.name)
                current.stackTrace.take(MAX_FRAMES).forEach { appendLine("  at $it") }
                current = current.cause?.takeIf { it !== current }
            }
        }
    }
}
