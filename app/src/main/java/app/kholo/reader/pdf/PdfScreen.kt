package app.kholo.reader.pdf

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.pdf.Highlight
import androidx.pdf.PdfPoint
import androidx.pdf.PdfRect
import app.kholo.reader.R
import app.kholo.reader.core.AppContainer
import app.kholo.reader.ui.components.AppTopBar
import app.kholo.reader.ui.theme.KholoTheme
import app.kholo.reader.ui.theme.LocalReaderBackground
import app.kholo.reader.ui.theme.ReaderBackgroundDark
import app.kholo.reader.ui.theme.Spacing
import app.kholo.reader.viewer.OpenDocument

private val TOP_BAR_HEIGHT = 56.dp
private const val HIGHLIGHT_COLOR = 0x80FFEB3B.toInt()
private const val CURRENT_HIGHLIGHT_COLOR = 0x80FF9800.toInt()

@Composable
fun PdfScreen(
    doc: OpenDocument,
    container: AppContainer,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenElsewhere: () -> Unit,
    onReport: () -> Unit,
) {
    val viewModel: PdfViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PdfViewModel(doc.document, doc.uri, doc.reading, container) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fullFeatures = remember { PdfCapabilities.current().fullFeatures }
    val host = remember {
        PdfViewHost(context, viewModel::toggleControls).also { layout ->
            layout.setNight(doc.reading.night)
            layout.setPageRotation(doc.reading.rotation)
        }
    }
    val zoom = remember {
        ZoomController(
            view = host.pdfView,
            document = doc.document,
            startPage = doc.reading.page.coerceIn(0, doc.document.pageCount - 1),
            onPage = viewModel::onPageChanged,
            onFit = viewModel::onFitChanged,
            onUserScroll = viewModel::hideControls,
        ).also { controller ->
            controller.attach(scope)
            host.pdfView.pdfDocument = doc.document
        }
    }
    val density = LocalDensity.current
    LaunchedEffect(density) { host.setTopInset(with(density) { TOP_BAR_HEIGHT.roundToPx() }) }
    LaunchedEffect(state.night) { host.setNight(state.night) }
    LaunchedEffect(state.rotation) { host.setPageRotation(state.rotation) }
    SearchEffect(state.search, host)
    BackHandler(enabled = state.search != null) { viewModel.closeSearch() }

    val background = if (state.night) ReaderBackgroundDark else LocalReaderBackground.current
    val window = LocalActivity.current?.window
    val rootView = LocalView.current
    LaunchedEffect(background, window) {
        // Night page ke upar status bar ke icons bhi halke hon.
        if (window != null) {
            val lightBars = background != ReaderBackgroundDark
            WindowCompat.getInsetsController(window, rootView).isAppearanceLightStatusBars = lightBars
        }
    }

    Box(Modifier.fillMaxSize().background(background)) {
        // PDF status bar, navigation bar aur notch ke neeche kabhi nahi jayega.
        AndroidView(
            factory = { host },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
        )
        ReaderControlsTheme(night = state.night) {
            AnimatedVisibility(state.controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                val search = state.search
                if (search != null) {
                    PdfSearchBar(
                        state = search,
                        onQuery = viewModel::search,
                        onStep = viewModel::nextHit,
                        onClose = viewModel::closeSearch,
                    )
                } else {
                    AppTopBar(title = stringResource(R.string.app_name), subtitle = doc.name, onBack = onBack) {
                        if (fullFeatures) {
                            IconButton(onClick = viewModel::openSearch) {
                                Icon(
                                    painterResource(R.drawable.ic_search),
                                    contentDescription = stringResource(R.string.action_search),
                                )
                            }
                        }
                        ReaderMenu(
                            night = state.night,
                            onRotate = viewModel::rotate,
                            onNight = viewModel::toggleNight,
                            onShare = onShare,
                            onOpenElsewhere = onOpenElsewhere,
                            onReport = onReport,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = state.controlsVisible || !state.atFit,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                BottomControls(
                    page = state.page,
                    pageCount = state.pageCount,
                    showPage = state.controlsVisible,
                    showFit = !state.atFit,
                    onPageClick = { viewModel.showPageJump(true) },
                    onFit = zoom::fit,
                )
            }
        }
    }
    if (state.showPageJump) {
        PageJumpDialog(
            pageCount = state.pageCount,
            onJump = { page ->
                host.pdfView.scrollToPage(page)
                viewModel.showPageJump(false)
            },
            onDismiss = { viewModel.showPageJump(false) },
        )
    }
}

/** Night me toolbar bhi dark, chahe app ki theme light ho. */
@Composable
private fun ReaderControlsTheme(night: Boolean, content: @Composable () -> Unit) {
    if (night) KholoTheme(darkTheme = true, content = content) else content()
}

@Composable
private fun SearchEffect(search: PdfSearchState?, host: PdfViewHost) {
    val hits = search?.hits.orEmpty()
    val current = search?.current ?: -1
    LaunchedEffect(hits, current) {
        val highlights = hits.flatMapIndexed { index, hit ->
            val color = if (index == current) CURRENT_HIGHLIGHT_COLOR else HIGHLIGHT_COLOR
            hit.bounds.map { Highlight(PdfRect(hit.page, it.left, it.top, it.right, it.bottom), color) }
        }
        host.pdfView.setHighlights(highlights)
    }
    // Sirf current badalne par scroll, naye results aane par padhne ki jagah na hile.
    LaunchedEffect(current) {
        val hit = hits.getOrNull(current) ?: return@LaunchedEffect
        val box = hit.bounds.firstOrNull() ?: return@LaunchedEffect
        host.pdfView.scrollToPosition(PdfPoint(hit.page, box.left, box.top))
    }
}

@Composable
private fun ReaderMenu(
    night: Boolean,
    onRotate: () -> Unit,
    onNight: () -> Unit,
    onShare: () -> Unit,
    onOpenElsewhere: () -> Unit,
    onReport: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val item: @Composable (Int, () -> Unit) -> Unit = { label, action ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
            item(R.string.action_rotate, onRotate)
            item(if (night) R.string.action_night_off else R.string.action_night_on, onNight)
            item(R.string.action_share, onShare)
            item(R.string.action_open_elsewhere, onOpenElsewhere)
            item(R.string.action_report, onReport)
        }
    }
}

@Composable
private fun BottomControls(
    page: Int,
    pageCount: Int,
    showPage: Boolean,
    showFit: Boolean,
    onPageClick: () -> Unit,
    onFit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.l),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPage) {
            Surface(
                onClick = onPageClick,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = Spacing.xs,
            ) {
                Text(
                    text = stringResource(R.string.page_indicator, page + 1, pageCount),
                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            Box(Modifier)
        }
        if (showFit) {
            Button(onClick = onFit, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.action_fit)) }
        }
    }
}

@Composable
private fun PageJumpDialog(pageCount: Int, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val page = text.toIntOrNull()?.takeIf { it in 1..pageCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_jump_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                singleLine = true,
                label = { Text(stringResource(R.string.page_jump_hint, pageCount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { page?.let { onJump(it - 1) } }, enabled = page != null) {
                Text(stringResource(R.string.action_go))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
