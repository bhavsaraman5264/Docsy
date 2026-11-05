package app.kholo.reader.core

import android.content.ContentResolver
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.kholo.reader.core.file.TempFiles
import app.kholo.reader.core.file.UriReader
import app.kholo.reader.core.log.RingBufferTree
import app.kholo.reader.pdf.PdfOpener
import app.kholo.reader.pdf.ReadingStateStore
import app.kholo.reader.recents.RecentsRepository
import app.kholo.reader.report.BugReportBuilder
import app.kholo.reader.report.CrashRecorder
import app.kholo.reader.settings.SettingsRepository

private val Context.appStore: DataStore<Preferences> by preferencesDataStore(name = "kholo")

/** Manual DI: app ke saare shared objects yahin bante hain. Hilt ka startup kharcha nahi. */
class AppContainer(
    context: Context,
    val logBuffer: RingBufferTree,
    val crashRecorder: CrashRecorder,
    val dispatchers: AppDispatchers = AppDispatchers(),
) {
    private val store = context.applicationContext.appStore
    val uriReader = UriReader(context)
    val tempFiles = TempFiles(context)
    val contentResolver: ContentResolver = context.applicationContext.contentResolver
    val recents = RecentsRepository(store)
    val settings = SettingsRepository(store)
    val readingState = ReadingStateStore(store)
    val pdfOpener = PdfOpener(context)
    val bugReports = BugReportBuilder(context, logBuffer)
}
