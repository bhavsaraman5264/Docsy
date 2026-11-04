package app.kholo.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.kholo.reader.core.log.AppLog
import app.kholo.reader.core.log.LogTag
import app.kholo.reader.ui.components.AppTopBar
import app.kholo.reader.ui.components.EmptyState
import app.kholo.reader.ui.theme.KholoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLog.d(LogTag.OPEN) { "home created" }
        AppLog.i(LogTag.PERF) { "main activity created" }
        setContent {
            KholoTheme {
                Scaffold(topBar = { AppTopBar(title = stringResource(R.string.app_name)) }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        EmptyState(message = stringResource(R.string.home_empty_title))
                    }
                }
            }
        }
    }
}
