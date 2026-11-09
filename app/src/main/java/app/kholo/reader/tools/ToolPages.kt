package app.kholo.reader.tools

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kholo.reader.R
import app.kholo.reader.ui.theme.Spacing

private val PAGE_CELL = 100.dp
private val PAGE_LINE = 1.dp
private val SELECTED_LINE = 2.dp
private val ACTION_ICON = 24.dp
private const val QUARTER_LEFT = -90f
private const val QUARTER_RIGHT = 90f
private const val DISABLED_ALPHA = 0.38f

/** Har page par chaar button ki jagah nahi hoti. Page chuno, neeche ke button usi par chalte hain. */
@Composable
internal fun PageGrid(viewModel: ToolsViewModel, state: ToolUiState, modifier: Modifier) {
    val grid = rememberLazyGridState()
    val selectedIndex = state.pageOrder.indexOf(state.selectedPage)
    // Khiskaya hua page screen ke bahar na chala jaye.
    LaunchedEffect(selectedIndex) {
        val shown = grid.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex }
        if (selectedIndex >= 0 && !shown) grid.animateScrollToItem(selectedIndex)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(PAGE_CELL),
        modifier = modifier.fillMaxWidth(),
        state = grid,
        contentPadding = PaddingValues(start = Spacing.screenPadding, end = Spacing.screenPadding, bottom = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        itemsIndexed(state.pageOrder, key = { _, page -> page }) { _, page ->
            PageCell(
                viewModel = viewModel,
                page = page,
                turn = state.pageTurns[page] ?: 0,
                selected = page == state.selectedPage,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun PageCell(viewModel: ToolsViewModel, page: Int, turn: Int, selected: Boolean, modifier: Modifier) {
    val shape = MaterialTheme.shapes.medium
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceBright)
            .border(
                width = if (selected) SELECTED_LINE else PAGE_LINE,
                color = if (selected) colors.primary else colors.outlineVariant,
                shape = shape,
            )
            .clickable(role = Role.RadioButton) { viewModel.selectPage(page) }
            .padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageThumb(viewModel, page, turn)
        Text(
            text = stringResource(R.string.tool_page, page + 1),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Dabba chaukor hai, taaki ghumane par lamba page bahar na nikle. */
@Composable
private fun PageThumb(viewModel: ToolsViewModel, page: Int, turn: Int) {
    val bitmap by produceState<Bitmap?>(viewModel.cachedPage(page), page) {
        if (value == null) value = viewModel.pageBitmap(page)
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
        image?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize().rotate(turn.toFloat())) }
    }
}

@Composable
internal fun PageActions(viewModel: ToolsViewModel, state: ToolUiState) {
    val page = state.selectedPage
    val index = state.pageOrder.indexOf(page)
    Row(Modifier.fillMaxWidth()) {
        PageAction(
            icon = R.drawable.ic_keyboard_arrow_up,
            label = R.string.action_move_back,
            turn = QUARTER_LEFT,
            enabled = index > 0,
        ) { viewModel.movePage(index, -1) }
        PageAction(
            icon = R.drawable.ic_keyboard_arrow_up,
            label = R.string.action_move_ahead,
            turn = QUARTER_RIGHT,
            enabled = index in 0 until state.pageOrder.lastIndex,
        ) { viewModel.movePage(index, 1) }
        PageAction(R.drawable.ic_rotate_right, R.string.action_rotate, enabled = page != null) {
            page?.let(viewModel::rotatePage)
        }
        PageAction(R.drawable.ic_close, R.string.action_remove, enabled = index >= 0) { viewModel.removePage(index) }
    }
}

@Composable
private fun RowScope.PageAction(icon: Int, label: Int, turn: Float = 0f, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ACTION_ICON).rotate(turn))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
