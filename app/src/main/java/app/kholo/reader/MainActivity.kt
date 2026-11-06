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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kholo.reader.core.AppContainer
import app.kholo.reader.home.HomeScreen
import app.kholo.reader.home.HomeViewModel
import app.kholo.reader.report.ReportScreen
import app.kholo.reader.settings.LanguageStore
import app.kholo.reader.settings.SettingsScreen
import app.kholo.reader.tools.ToolsActivity
import app.kholo.reader.ui.components.IntroPage
import app.kholo.reader.ui.components.SwipeBack
import app.kholo.reader.ui.theme.AppTheme
import kotlinx.coroutines.delay

private const val PDF_WARMUP_DELAY_MS = 500L
private const val ROUTE_FADE_MS = 180
private const val INTRO_MS = 900L
private const val INTRO_FADE_MS = 250
private const val ROUTE_HOME = "home"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_REPORT = "report"
private const val EXTRA_REPORT_CATEGORY = "report_category"
internal val OPEN_TYPES = arrayOf(
    "application/pdf",
    "text/plain",
    "text/csv",
    "text/comma-separated-values",
    "application/csv",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel.sheet.macroEnabled.12",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/msword",
    "application/vnd.ms-excel",
    "application/vnd.ms-powerpoint",
)

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as KholoApp).container

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // PDF engine alag process me chalta hai. Screen dikhne ke baad warm karo, taaki app ka start dhima na ho.
        window.decorView.postDelayed(container::warmPdfEngine, PDF_WARMUP_DELAY_MS)
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
                // Sirf app khulne par. Rotate ya Settings se lautne par dobara nahi.
                var intro by rememberSaveable { mutableStateOf(startCategory == null) }
                LaunchedEffect(Unit) {
                    delay(INTRO_MS)
                    intro = false
                }
                Box(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = route,
                        transitionSpec = { fadeIn(tween(ROUTE_FADE_MS)) togetherWith fadeOut(tween(ROUTE_FADE_MS)) },
                        label = "route",
                    ) { shown ->
                        when (shown) {
                            ROUTE_SETTINGS -> SwipeBack(onBack = { route = ROUTE_HOME }) {
                                SettingsScreen(
                                    settings = container.settings,
                                    onBack = { route = ROUTE_HOME },
                                )
                            }

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
                                    onTool = { startActivity(ToolsActivity.intent(this@MainActivity, it)) },
                                    onCrashReport = {
                                        category = "crash"
                                        route = ROUTE_REPORT
                                    },
                                )
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = intro,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(INTRO_FADE_MS)),
                    ) {
                        IntroPage()
                    }
                }
            }
        }
    }

    private fun openPicked(uri: Uri) {
        container.uriReader.keepAccess(uri)
        open(uri)
    }

    /**
     * Viewer isi app ka hai, isliye URI grant ki zaroorat nahi. Grant flag lagane par expire hua
     * WhatsApp URI yahin crash karta tha. Ab viewer khud saaf message dikhata hai.
     */
    private fun open(uri: Uri) {
        startActivity(Intent(Intent.ACTION_VIEW, uri, this, ViewerActivity::class.java))
    }

    private fun share(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(contentResolver.getType(uri) ?: "*/*")
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
