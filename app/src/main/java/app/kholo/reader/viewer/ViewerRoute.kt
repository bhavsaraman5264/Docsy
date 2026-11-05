package app.kholo.reader.viewer

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kholo.reader.core.AppContainer
import app.kholo.reader.pdf.PasswordScreen
import app.kholo.reader.pdf.PdfScreen
import app.kholo.reader.ui.theme.LocalReaderBackground

@Composable
fun ViewerRoute(
    uri: Uri,
    container: AppContainer,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenElsewhere: () -> Unit,
    onReport: (String) -> Unit,
) {
    val viewModel: ViewerViewModel = viewModel(
        factory = viewModelFactory { initializer { ViewerViewModel(uri, container) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        ViewerUiState.Loading -> Box(
            Modifier.fillMaxSize().background(LocalReaderBackground.current),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        is ViewerUiState.NeedsPassword -> PasswordScreen(
            name = current.name,
            failedAttempt = current.failedAttempt,
            onSubmit = viewModel::submitPassword,
            onBack = onBack,
        )

        is ViewerUiState.Failed -> OpenFailedScreen(
            error = current.error,
            name = current.name,
            onBack = onBack,
            onOpenElsewhere = onOpenElsewhere,
            onReport = { onReport("open") },
        )

        is ViewerUiState.Ready -> PdfScreen(
            doc = current.doc,
            container = container,
            onBack = onBack,
            onShare = onShare,
            onOpenElsewhere = onOpenElsewhere,
            onReport = { onReport("zoom") },
        )
    }
}
