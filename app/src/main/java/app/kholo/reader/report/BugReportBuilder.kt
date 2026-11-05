package app.kholo.reader.report

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import app.kholo.reader.BuildConfig
import app.kholo.reader.core.log.RingBufferTree
import app.kholo.reader.pdf.PdfCapabilities

private const val BYTES_IN_MB = 1024L * 1024L

/** Sirf safe jaankari: file ka naam, URI, password aur document ka text yahan kabhi nahi aata. */
class BugReportBuilder(context: Context, private val logBuffer: RingBufferTree) {
    private val appContext = context.applicationContext

    fun build(category: String, note: String, crash: String?): String = buildString {
        appendLine("Kholo report: $category")
        if (note.isNotBlank()) appendLine("Note: $note")
        appendLine()
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("RAM: ${totalRamMb()} MB")
        appendLine("Free storage: ${Environment.getDataDirectory().usableSpace / BYTES_IN_MB} MB")
        appendLine("Full PDF features: ${PdfCapabilities.current().fullFeatures}")
        appendLine()
        appendLine("Events:")
        logBuffer.snapshot().forEach { appendLine("${it.timeMillis} ${it.level} ${it.tag} ${it.message}") }
        if (crash != null) {
            appendLine()
            appendLine("Crash:")
            append(crash)
        }
    }

    private fun totalRamMb(): Long {
        val manager = appContext.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.totalMem / BYTES_IN_MB
    }
}
