package app.kholo.reader.home

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kholo.reader.R
import app.kholo.reader.recents.RecentFile
import app.kholo.reader.tools.ToolGrid
import app.kholo.reader.tools.ToolKind
import app.kholo.reader.ui.adaptive.rememberWindowInfo
import app.kholo.reader.ui.components.AppTopBar
import app.kholo.reader.ui.components.EmptyState
import app.kholo.reader.ui.components.FileRow
import app.kholo.reader.ui.components.PrimaryAction
import app.kholo.reader.ui.theme.FavoriteStar
import app.kholo.reader.ui.theme.LocalBarColors
import app.kholo.reader.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Tabs turant neeche hain, isliye bar chhota. Touch target phir bhi 48dp rehta hai. */
private val HOME_BAR_HEIGHT = 48.dp

/** Aakhri file `Open file` button ke neeche na chhupe. */
private val LIST_BOTTOM_PADDING = 96.dp

/** Screen ke left se beech tak kahin se shuru hua swipe menu kholta hai. Bilkul right wala nahi. */
private const val DRAWER_START_SHARE = 0.7f

/** Itna khinchne par menu khule, taaki halki si ungli se na khul jaye. */
private val DRAWER_PULL = 48.dp

/** Itni tez chhoti flick bhi menu khole, chahe [DRAWER_PULL] tak na pahunchi ho. Dp prati second. */
private val DRAWER_FLICK = 600.dp

/** Page ka itna hissa khinchne par tab badle. Default aadha hai, jo bhaari lagta hai. */
private const val TAB_SWITCH_THRESHOLD = 0.3f

private const val JUST_NOW_MS = 60_000L
private const val FILE_ROW = "file"

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPickFile: () -> Unit,
    onOpen: (RecentFile) -> Unit,
    onShare: (RecentFile) -> Unit,
    onSettings: () -> Unit,
    onReport: () -> Unit,
    onTool: (ToolKind) -> Unit,
    onCrashReport: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val window = rememberWindowInfo()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = viewModel.lastPage) { HomeTab.entries.size }
    val recentList = rememberLazyListState()
    val favoriteList = rememberLazyListState()
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { viewModel.lastPage = it } }
    val pullPx = with(LocalDensity.current) { DRAWER_PULL.toPx() }
    val flickPx = with(LocalDensity.current) { DRAWER_FLICK.toPx() }
    val drawerPull = remember(drawer, pullPx, flickPx) {
        DrawerPull(pullPx, flickPx) { scope.launch { drawer.open() } }
    }
    // Sirf button ke andar padha jata hai, taaki swipe ke beech poori screen dobara na bane.
    val hasFiles by remember(state.recents, state.favorites) {
        derivedStateOf {
            when (HomeTab.entries[pager.currentPage]) {
                HomeTab.Recent -> state.recents.isNotEmpty()
                HomeTab.Favorites -> state.favorites.isNotEmpty()
                HomeTab.Tools -> false
            }
        }
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        // Side swipe tabs ke liye hai. Band drawer ko [DrawerPull] pehle tab se kholta hai.
        gesturesEnabled = drawer.isOpen,
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
                HomeTabs(pager) { scope.launch { pager.animateScrollToPage(it.ordinal) } }
                HorizontalPager(
                    state = pager,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(drawerPull) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                drawerPull.begin(down.position.x <= size.width * DRAWER_START_SHARE)
                            }
                        }
                        .nestedScroll(drawerPull),
                    // Teeno tabs pehle se bane rehte hain, isliye swipe ke beech kuch naya nahi banta.
                    beyondViewportPageCount = HomeTab.entries.size - 1,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pager,
                        snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        snapPositionalThreshold = TAB_SWITCH_THRESHOLD,
                    ),
                ) { page ->
                    when (val shown = HomeTab.entries[page]) {
                        HomeTab.Tools -> ToolGrid(onTool)
                        HomeTab.Recent -> FilePage(shown, state.recents, recentList, onPickFile) {
                            RecentRow(it, onOpen, onShare, viewModel)
                        }
                        HomeTab.Favorites -> FilePage(shown, state.favorites, favoriteList, onPickFile) {
                            RecentRow(it, onOpen, onShare, viewModel)
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

/** Sirf screen par dikhne wali rows banti hain, isliye hazaron files par bhi scroll halka rehta hai. */
@Composable
private fun FilePage(
    tab: HomeTab,
    files: List<RecentFile>,
    listState: LazyListState,
    onPickFile: () -> Unit,
    row: @Composable (RecentFile) -> Unit,
) {
    if (files.isEmpty()) {
        EmptyHome(tab, onPickFile)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = LIST_BOTTOM_PADDING),
        ) {
            items(files, key = { it.uri }, contentType = { FILE_ROW }) { row(it) }
        }
    }
}

/** Pehle tab par pager aur peeche nahi ja sakta. Kinare se shuru hua bacha hua right swipe menu kholta hai. */
private class DrawerPull(
    private val threshold: Float,
    private val flick: Float,
    private val open: () -> Unit,
) : NestedScrollConnection {
    private var pulled = 0f
    private var canOpen = false

    fun begin(inZone: Boolean) {
        canOpen = inZone
        pulled = 0f
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && canOpen) {
            pulled = if (available.x > 0f) pulled + available.x else 0f
            if (pulled >= threshold) openOnce()
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // Pager jo khinchav nahi le paya wahi pulled hai, isliye tab badalne wali flick menu nahi kholti.
        if (canOpen && pulled > 0f && available.x >= flick) openOnce()
        pulled = 0f
        return Velocity.Zero
    }

    /** Ek swipe mein ek hi baar, warna chalti animation dobara shuru hokar jhatka deti hai. */
    private fun openOnce() {
        canOpen = false
        open()
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
    val justNow = stringResource(R.string.time_just_now)
    // Date format mehnga hai. Scroll ke har frame me dobara nahi banana.
    return remember(file.openedAt, file.size, justNow) {
        val age = System.currentTimeMillis() - file.openedAt
        val time = if (age < JUST_NOW_MS) {
            justNow
        } else {
            DateUtils.getRelativeTimeSpanString(file.openedAt).toString()
        }
        if (file.size > 0) "${Formatter.formatShortFileSize(context, file.size)} · $time" else time
    }
}

@Composable
private fun HomeTabs(pager: PagerState, onSelect: (HomeTab) -> Unit) {
    val bar = LocalBarColors.current
    val selected = HomeTab.entries[pager.currentPage]
    PrimaryTabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = bar.container,
        contentColor = bar.content,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selected.ordinal, matchContentSize = true),
                width = Dp.Unspecified,
                color = bar.content,
            )
        },
        divider = {},
    ) {
        HomeTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = { Text(stringResource(tab.label()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                selectedContentColor = bar.content,
                unselectedContentColor = bar.quiet,
            )
        }
    }
}

private fun HomeTab.label(): Int = when (this) {
    HomeTab.Recent -> R.string.tab_recent
    HomeTab.Favorites -> R.string.tab_favorites
    HomeTab.Tools -> R.string.tab_tools
}
