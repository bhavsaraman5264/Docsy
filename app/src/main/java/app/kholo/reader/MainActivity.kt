package app.kholo.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.pdf.PdfSandboxHandle
import androidx.pdf.SandboxedPdfLoader
import app.kholo.reader.core.AppContainer
import app.kholo.reader.home.HomeScreen
import app.kholo.reader.home.HomeViewModel
import app.kholo.reader.report.ReportScreen
import app.kholo.reader.settings.LanguageStore
import app.kholo.reader.settings.SettingsScreen
import app.kholo.reader.ui.theme.AppTheme

private const val ROUTE_HOME = "home"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_REPORT = "report"
private const val EXTRA_REPORT_CATEGORY = "report_category"
private val OPEN_TYPES = arrayOf("application/pdf")

class MainActivity : ComponentActivity() {
    private var sandbox: PdfSandboxHandle? = null
    private val container: AppContainer get() = (application as KholoApp).container

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // PDF engine alag process me chalta hai. Pehle se warm rahe to file tap karte hi khulti hai.
        window.decorView.post { sandbox = SandboxedPdfLoader.startInitialization(this) }
        val startCategory = intent.getStringExtra(EXTRA_REPORT_CATEGORY)
        setContent {
            AppTheme(container.settings) {
                val firstRoute = if (startCategory != null) ROUTE_REPORT else ROUTE_HOME
                var route by rememberSaveable { mutableStateOf(firstRoute) }
                var category by rememberSaveable { mutableStateOf(startCategory ?: "other") }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let(::openPicked)
                }
                BackHandler(enabled = route != ROUTE_HOME) {
                    if (startCategory != null) finish() else route = ROUTE_HOME
                }
                when (route) {
                    ROUTE_SETTINGS -> SettingsScreen(
                        settings = container.settings,
                        onBack = { route = ROUTE_HOME },
                    )

                    ROUTE_REPORT -> ReportScreen(
                        container = container,
                        category = category,
                        onBack = { if (startCategory != null) finish() else route = ROUTE_HOME },
                        onSend = ::sendReport,
                    )

                    else -> {
                        val homeViewModel: HomeViewModel = viewModel(
                            factory = viewModelFactory { initializer { HomeViewModel(container) } },
                        )
                        HomeScreen(
                            viewModel = homeViewModel,
                            onPickFile = { picker.launch(OPEN_TYPES) },
                            onOpen = { open(Uri.parse(it.uri)) },
                            onShare = { share(Uri.parse(it.uri)) },
                            onSettings = { route = ROUTE_SETTINGS },
                            onReport = {
                                category = "other"
                                route = ROUTE_REPORT
                            },
                            onCrashReport = {
                                category = "crash"
                                route = ROUTE_REPORT
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        sandbox?.close()
        super.onDestroy()
    }

    private fun openPicked(uri: Uri) {
        container.uriReader.keepAccess(uri)
        open(uri)
    }

    private fun open(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri, this, ViewerActivity::class.java)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }

    private fun share(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(Intent.createChooser(send, null))
    }

    private fun sendReport(report: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Kholo report")
            .putExtra(Intent.EXTRA_TEXT, report)
        launch(Intent.createChooser(send, null))
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.error_file_gone, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun reportIntent(context: Context, category: String): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_REPORT_CATEGORY, category)
    }
}
