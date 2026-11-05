package app.kholo.reader

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.pdf.PdfSandboxHandle
import androidx.pdf.SandboxedPdfLoader
import app.kholo.reader.open.IntentParser
import app.kholo.reader.ui.theme.AppTheme
import app.kholo.reader.viewer.ViewerRoute

/** "Open with" aur Share ka seedha darwaza: koi splash ya Home beech me nahi. */
class ViewerActivity : ComponentActivity() {
    private var sandbox: PdfSandboxHandle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = IntentParser.parse(intent)
        if (request == null) {
            finish()
            return
        }
        sandbox = SandboxedPdfLoader.startInitialization(this)
        enableEdgeToEdge()
        val container = (application as KholoApp).container
        setContent {
            AppTheme(container.settings) {
                ViewerRoute(
                    uri = request.uri,
                    container = container,
                    onBack = ::finish,
                    onShare = { share(request.uri) },
                    onOpenElsewhere = { openElsewhere(request.uri) },
                    onReport = ::report,
                )
            }
        }
    }

    private fun share(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(contentResolver.getType(uri) ?: PDF_MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(Intent.createChooser(send, null))
    }

    private fun openElsewhere(uri: Uri) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, contentResolver.getType(uri) ?: PDF_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(view, null).putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(this, ViewerActivity::class.java)),
        )
        launch(chooser)
    }

    private fun report(category: String) {
        startActivity(MainActivity.reportIntent(this, category))
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        sandbox?.close()
        super.onDestroy()
    }

    private companion object {
        const val PDF_MIME = "application/pdf"
    }
}
