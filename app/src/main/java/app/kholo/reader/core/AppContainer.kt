package app.kholo.reader.core

import android.content.ContentResolver
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.pdf.PdfSandboxHandle
import androidx.pdf.SandboxedPdfLoader
import app.kholo.reader.core.file.TempFiles
import app.kholo.reader.core.file.UriReader
import app.kholo.reader.core.log.RingBufferTree
import app.kholo.reader.pdf.PdfOpener
import app.kholo.reader.pdf.ReadingStateStore
import app.kholo.reader.recents.RecentsRepository
import app.kholo.reader.report.BugReportBuilder
import app.kholo.reader.report.CrashRecorder
import app.kholo.reader.settings.SettingsRepository
import app.kholo.reader.tools.PdfTools
import java.io.File

private val Context.appStore: DataStore<Preferences> by preferencesDataStore(name = "kholo")

/** Manual DI: app ke saare shared objects yahin bante hain. Hilt ka startup kharcha nahi. */
class AppContainer(
    context: Context,
    val logBuffer: RingBufferTree,
    val crashRecorder: CrashRecorder,
    val dispatchers: AppDispatchers = AppDispatchers(),
) {
    private val appContext = context.applicationContext
    private val store = appContext.appStore
    private var pdfEngine: PdfSandboxHandle? = null
    val uriReader = UriReader(context)
    val tempFiles = TempFiles(context)
    val pdfTools = PdfTools(context, File(context.applicationContext.cacheDir, "open"))
    val contentResolver: ContentResolver = context.applicationContext.contentResolver
    val recents = RecentsRepository(store)
    val settings = SettingsRepository(store)
    val readingState = ReadingStateStore(store)
    val pdfOpener = PdfOpener(context)
    val bugReports = BugReportBuilder(context, logBuffer)

    /**
     * Engine poore app ke liye ek baar chalu hota hai. Har screen ke band hone par connection band karne se
     * agla PDF spinner par atak jata tha.
     */
    fun warmPdfEngine() {
        if (pdfEngine == null) pdfEngine = SandboxedPdfLoader.startInitialization(appContext)
    }
}
