package app.kholo.reader.home

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kholo.reader.R
import app.kholo.reader.recents.RecentFile
import app.kholo.reader.ui.adaptive.WidthClass
import app.kholo.reader.ui.adaptive.rememberWindowInfo
import app.kholo.reader.ui.components.AppTopBar
import app.kholo.reader.ui.components.EmptyState
import app.kholo.reader.ui.components.FileRow
import app.kholo.reader.ui.components.PrimaryAction
import app.kholo.reader.ui.theme.Spacing

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPickFile: () -> Unit,
    onPickWhatsApp: () -> Unit,
    onOpen: (RecentFile) -> Unit,
    onShare: (RecentFile) -> Unit,
    onSettings: () -> Unit,
    onCrashReport: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val window = rememberWindowInfo()
    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.app_name)) {
                if (window.isShortHeight) {
                    IconButton(onClick = onPickFile) {
                        Icon(painterResource(R.drawable.ic_add), stringResource(R.string.action_open_file))
                    }
                }
                IconButton(onClick = onSettings) {
                    Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.action_settings))
                }
            }
        },
        floatingActionButton = {
            if (!window.isShortHeight) PrimaryAction(stringResource(R.string.action_open_file), onPickFile)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == HomeTab.Recent,
                    onClick = { viewModel.selectTab(HomeTab.Recent) },
                    text = { Text(stringResource(R.string.tab_recent)) },
                )
                Tab(
                    selected = state.tab == HomeTab.Favorites,
                    onClick = { viewModel.selectTab(HomeTab.Favorites) },
                    text = { Text(stringResource(R.string.tab_favorites)) },
                )
            }
            WhatsAppRow(onPickWhatsApp)
            if (state.files.isEmpty()) {
                EmptyHome(state.tab, onPickFile)
            } else {
                val columns = when (window.widthClass) {
                    WidthClass.Compact -> 1
                    WidthClass.Medium -> 2
                    WidthClass.Expanded -> 3
                }
                LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize()) {
                    items(state.files, key = { it.uri }) { file ->
                        RecentRow(file, onOpen, onShare, viewModel)
                    }
                }
            }
        }
    }
    if (state.pendingCrash) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCrash,
            title = { Text(stringResource(R.string.crash_title)) },
            text = { Text(stringResource(R.string.crash_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.acknowledgeCrash()
                        onCrashReport()
                    },
                ) { Text(stringResource(R.string.action_report)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCrash) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun WhatsAppRow(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.whatsapp_files),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.m),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EmptyHome(tab: HomeTab, onPickFile: () -> Unit) {
    val message = if (tab == HomeTab.Favorites) R.string.favorites_empty else R.string.home_empty_title
    EmptyState(message = stringResource(message)) {
        if (tab == HomeTab.Recent) {
            Button(onClick = onPickFile) { Text(stringResource(R.string.action_open_file)) }
        }
    }
}

@Composable
private fun RecentRow(
    file: RecentFile,
    onOpen: (RecentFile) -> Unit,
    onShare: (RecentFile) -> Unit,
    viewModel: HomeViewModel,
) {
    val context = LocalContext.current
    val time = DateUtils.getRelativeTimeSpanString(file.openedAt)
    val sizeText = Formatter.formatShortFileSize(context, file.size)
    val subtitle = if (file.size > 0) "$sizeText - $time" else time.toString()
    FileRow(
        title = file.name,
        subtitle = subtitle,
        typeLabel = file.typeLabel,
        onClick = { onOpen(file) },
    ) {
        var open by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { open = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                val favorite = if (file.favorite) R.string.action_unfavorite else R.string.action_favorite
                DropdownMenuItem(
                    text = { Text(stringResource(favorite)) },
                    onClick = {
                        open = false
                        viewModel.toggleFavorite(file.uri)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_share)) },
                    onClick = {
                        open = false
                        onShare(file)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_remove_recent)) },
                    onClick = {
                        open = false
                        viewModel.remove(file.uri)
                    },
                )
            }
        }
    }
}
