package app.kholo.reader.pdf

import android.content.Context
import android.net.Uri
import android.os.RemoteException
import androidx.pdf.PdfDocument
import androidx.pdf.PdfPasswordException
import androidx.pdf.SandboxedPdfLoader
import app.kholo.reader.core.AppError
import app.kholo.reader.core.DocResult
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

private const val OPEN_TIMEOUT_MS = 20_000L

/** PDF sandbox process me khulta hai, isliye kharab file app ko crash nahi karti. */
class PdfOpener(context: Context) {
    private val appContext = context.applicationContext
    private var loader = SandboxedPdfLoader(appContext)

    /** Engine jawab na de to spinner hamesha nahi ghoomta: ek baar naye connection se koshish, phir saaf message. */
    private suspend fun openWithRetry(uri: Uri, password: String?): PdfDocument? =
        withTimeoutOrNull(OPEN_TIMEOUT_MS) { loader.openDocument(uri, password) } ?: run {
            loader = SandboxedPdfLoader(appContext)
            withTimeoutOrNull(OPEN_TIMEOUT_MS) { loader.openDocument(uri, password) }
        }

    suspend fun open(uri: Uri, password: String?): DocResult<PdfDocument> = try {
        openWithRetry(uri, password)?.let { DocResult.Success(it) } ?: DocResult.Failure(AppError.PdfBusy)
    } catch (e: CancellationException) {
        throw e
    } catch (_: PdfPasswordException) {
        DocResult.Failure(AppError.WrongPassword)
    } catch (_: FileNotFoundException) {
        DocResult.Failure(AppError.FileGone)
    } catch (_: SecurityException) {
        DocResult.Failure(AppError.NeedsAccess)
    } catch (_: IOException) {
        DocResult.Failure(AppError.Corrupt)
    } catch (_: IllegalStateException) {
        // M1 me mila: kharab file par library "Unknown loading status" deti hai.
        DocResult.Failure(AppError.Corrupt)
    } catch (_: IllegalArgumentException) {
        DocResult.Failure(AppError.Corrupt)
    } catch (_: RemoteException) {
        DocResult.Failure(AppError.Corrupt)
    }
}
