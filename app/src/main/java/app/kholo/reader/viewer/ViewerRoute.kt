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
import app.kholo.reader.docx.DocxScreen
import app.kholo.reader.open.DocumentType
import app.kholo.reader.pdf.PdfScreen
import app.kholo.reader.table.GridScreen
import app.kholo.reader.text.TextScreen
import app.kholo.reader.ui.theme.LocalReaderBackground
import app.kholo.reader.unlock.UnlockScreen

@Composable
fun ViewerRoute(
    uri: Uri,
    container: AppContainer,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenElsewhere: () -> Unit,
    onPickAgain: () -> Unit,
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

        is ViewerUiState.NeedsPassword -> UnlockScreen(
            name = current.name,
            failedAttempt = current.failedAttempt,
            detailsFailed = current.detailsFailed,
            onPassword = viewModel::submitPassword,
            onDetails = viewModel::unlockWith,
            onBack = onBack,
        )

        is ViewerUiState.Failed -> OpenFailedScreen(
            error = current.error,
            name = current.name,
            onBack = onBack,
            onOpenElsewhere = onOpenElsewhere,
            onPickAgain = onPickAgain,
            onReport = { onReport("open") },
        )

        is ViewerUiState.Other -> {
            val actions = ReaderActions(
                onBack = onBack,
                onShare = onShare,
                onOpenElsewhere = onOpenElsewhere,
                onReport = { onReport("open") },
                failed = { error ->
                    OpenFailedScreen(error, current.name, onBack, onOpenElsewhere, onPickAgain) { onReport("open") }
                },
            )
            when (current.type) {
                DocumentType.Csv, DocumentType.Xlsx, DocumentType.Xls ->
                    GridScreen(
                        uri = current.uri,
                        type = current.type,
                        name = current.name,
                        size = current.size,
                        container = container,
                        actions = actions,
                        content = current.content,
                    )
                DocumentType.Docx, DocumentType.Pptx, DocumentType.Doc, DocumentType.Ppt ->
                    DocxScreen(
                        uri = current.uri,
                        type = current.type,
                        name = current.name,
                        size = current.size,
                        container = container,
                        actions = actions,
                        content = current.content,
                    )
                else -> TextScreen(current.uri, current.name, container, actions)
            }
        }

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
