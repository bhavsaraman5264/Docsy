package app.kholo.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kholo.reader.core.AppContainer
import app.kholo.reader.home.HomeScreen
import app.kholo.reader.home.HomeViewModel
import app.kholo.reader.report.ReportScreen
import app.kholo.reader.settings.SettingsScreen
import app.kholo.reader.ui.theme.AppTheme

private const val ROUTE_HOME = "home"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_REPORT = "report"
private const val EXTRA_REPORT_CATEGORY = "report_category"
private val OPEN_TYPES = arrayOf("application/pdf")
private val WHATSAPP_DOCUMENTS: Uri = Uri.parse(
    "content://com.android.externalstorage.documents/document/" +
        "primary%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2FWhatsApp%20Documents",
)

/** File picker jo chahe to WhatsApp Documents folder par khule. */
private class OpenAt : ActivityResultContracts.OpenDocument() {
    var initial: Uri? = null

    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).also { intent ->
            initial?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
}

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as KholoApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startCategory = intent.getStringExtra(EXTRA_REPORT_CATEGORY)
        setContent {
            AppTheme(container.settings) {
                val firstRoute = if (startCategory != null) ROUTE_REPORT else ROUTE_HOME
                var route by rememberSaveable { mutableStateOf(firstRoute) }
                var category by rememberSaveable { mutableStateOf(startCategory ?: "other") }
                val contract = remember { OpenAt() }
                val picker = rememberLauncherForActivityResult(contract) { uri -> uri?.let(::openPicked) }
                BackHandler(enabled = route != ROUTE_HOME) {
                    if (startCategory != null) finish() else route = ROUTE_HOME
                }
                when (route) {
                    ROUTE_SETTINGS -> SettingsScreen(
                        settings = container.settings,
                        onBack = { route = ROUTE_HOME },
                        onReport = {
                            category = "other"
                            route = ROUTE_REPORT
                        },
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
                            onPickFile = {
                                contract.initial = null
                                picker.launch(OPEN_TYPES)
                            },
                            onPickWhatsApp = {
                                contract.initial = WHATSAPP_DOCUMENTS
                                picker.launch(OPEN_TYPES)
                            },
                            onOpen = { open(Uri.parse(it.uri)) },
                            onShare = { share(Uri.parse(it.uri)) },
                            onSettings = { route = ROUTE_SETTINGS },
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
