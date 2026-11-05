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

/** PDF sandbox process me khulta hai, isliye kharab file app ko crash nahi karti. */
class PdfOpener(context: Context) {
    private val loader = SandboxedPdfLoader(context.applicationContext)

    suspend fun open(uri: Uri, password: String?): DocResult<PdfDocument> = try {
        DocResult.Success(loader.openDocument(uri, password))
    } catch (e: CancellationException) {
        throw e
    } catch (_: PdfPasswordException) {
        DocResult.Failure(AppError.WrongPassword)
    } catch (_: FileNotFoundException) {
        DocResult.Failure(AppError.FileGone)
    } catch (_: SecurityException) {
        DocResult.Failure(AppError.FileGone)
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
