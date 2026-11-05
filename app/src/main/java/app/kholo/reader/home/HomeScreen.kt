package app.kholo.reader.home

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import app.kholo.reader.ui.theme.FavoriteStar
import app.kholo.reader.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Tabs turant neeche hain, isliye bar chhota. Touch target phir bhi 48dp rehta hai. */
private val HOME_BAR_HEIGHT = 48.dp

/** Aakhri file `Open file` button ke neeche na chhupe. */
private val LIST_BOTTOM_PADDING = 96.dp

private const val JUST_NOW_MS = 60_000L

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
    val hasFiles = state.files.isNotEmpty()
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            HomeDrawer(
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
                    if (window.isShortHeight && hasFiles) {
                        IconButton(onClick = onPickFile) {
                            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.action_open_file))
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!window.isShortHeight && hasFiles) {
                    PrimaryAction(stringResource(R.string.action_open_file), onPickFile)
                }
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
                if (hasFiles) {
                    FileGrid(state.files, window.widthClass, onOpen, onShare, viewModel)
                } else {
                    EmptyHome(state.tab, onPickFile)
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
private fun FileGrid(
    files: List<RecentFile>,
    widthClass: WidthClass,
    onOpen: (RecentFile) -> Unit,
    onShare: (RecentFile) -> Unit,
    viewModel: HomeViewModel,
) {
    val columns = when (widthClass) {
        WidthClass.Compact -> 1
        WidthClass.Medium -> 2
        WidthClass.Expanded -> 3
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LIST_BOTTOM_PADDING),
    ) {
        items(files, key = { it.uri }) { file ->
            RecentRow(file, onOpen, onShare, viewModel)
        }
    }
}

@Composable
private fun HomeDrawer(onSettings: () -> Unit, onReport: () -> Unit, close: () -> Unit) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.l),
            style = MaterialTheme.typography.titleLarge,
        )
        val item: @Composable (Int, Int, () -> Unit) -> Unit = { icon, label, action ->
            DrawerRow(icon, label) {
                close()
                action()
            }
        }
        item(R.drawable.ic_settings, R.string.action_settings, onSettings)
        item(R.drawable.ic_feedback, R.string.action_report, onReport)
    }
}

@Composable
private fun DrawerRow(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(painterResource(icon), contentDescription = null) },
        label = { Text(stringResource(label)) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = Spacing.m),
    )
}

@Composable
private fun EmptyHome(tab: HomeTab, onPickFile: () -> Unit) {
    if (tab == HomeTab.Favorites) {
        EmptyState(
            title = stringResource(R.string.favorites_empty),
            subtitle = stringResource(R.string.favorites_empty_hint),
            icon = R.drawable.ic_star,
        )
    } else {
        EmptyState(
            title = stringResource(R.string.home_empty_title),
            subtitle = stringResource(R.string.home_empty_hint),
        ) {
            Button(onClick = onPickFile, shape = MaterialTheme.shapes.small) {
                Text(stringResource(R.string.action_open_file))
            }
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
    FileRow(
        title = file.name,
        subtitle = fileSubtitle(file),
        typeLabel = file.typeLabel,
        onClick = { onOpen(file) },
    ) {
        IconButton(onClick = { viewModel.toggleFavorite(file.uri) }) {
            Icon(
                painterResource(if (file.favorite) R.drawable.ic_star else R.drawable.ic_star_border),
                contentDescription = stringResource(
                    if (file.favorite) R.string.action_unfavorite else R.string.action_favorite,
                ),
                tint = if (file.favorite) {
                    FavoriteStar
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        var open by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { open = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
                        viewModel.remove(file)
                    },
                )
            }
        }
    }
}

@Composable
private fun fileSubtitle(file: RecentFile): String {
    val context = LocalContext.current
    val age = System.currentTimeMillis() - file.openedAt
    val time = if (age < JUST_NOW_MS) {
        stringResource(R.string.time_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(file.openedAt).toString()
    }
    return if (file.size > 0) "${Formatter.formatShortFileSize(context, file.size)} · $time" else time
}
