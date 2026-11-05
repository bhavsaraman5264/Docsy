package app.kholo.reader.home

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

/** Tabs turant neeche hain, isliye bar chhota. Touch target phir bhi 48dp rehta hai. */
private val HOME_BAR_HEIGHT = 48.dp

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPickFile: () -> Unit,
    onOpen: (RecentFile) -> Unit,
    onShare: (RecentFile) -> Unit,
    onSettings: () -> Unit,
    onReport: () -> Unit,
    onCrashReport: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val window = rememberWindowInfo()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            HomeDrawer(
                tab = state.tab,
                onTab = viewModel::selectTab,
                onSettings = onSettings,
                onReport = onReport,
                close = { scope.launch { drawer.close() } },
            )
        },
    ) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = stringResource(R.string.app_name),
                    onMenu = { scope.launch { drawer.open() } },
                    height = HOME_BAR_HEIGHT,
                ) {
                    if (window.isShortHeight) {
                        IconButton(onClick = onPickFile) {
                            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.action_open_file))
                        }
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
private fun HomeDrawer(
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
    onSettings: () -> Unit,
    onReport: () -> Unit,
    close: () -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(Spacing.xl),
            style = MaterialTheme.typography.titleLarge,
        )
        val item: @Composable (Int, Boolean, () -> Unit) -> Unit = { label, selected, action ->
            NavigationDrawerItem(
                label = { Text(stringResource(label)) },
                selected = selected,
                onClick = {
                    close()
                    action()
                },
                modifier = Modifier.padding(horizontal = Spacing.m),
            )
        }
        item(R.string.tab_recent, tab == HomeTab.Recent) { onTab(HomeTab.Recent) }
        item(R.string.tab_favorites, tab == HomeTab.Favorites) { onTab(HomeTab.Favorites) }
        HorizontalDivider(Modifier.padding(vertical = Spacing.s))
        item(R.string.action_settings, false, onSettings)
        item(R.string.action_report, false, onReport)
    }
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
