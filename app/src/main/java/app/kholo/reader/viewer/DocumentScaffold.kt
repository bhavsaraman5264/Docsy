package app.kholo.reader.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.kholo.reader.R
import app.kholo.reader.ui.components.AppTopBar
import app.kholo.reader.ui.components.FindBar

/** Search khula ho to bar deta hai. Back pehle search band karta hai, document nahi. */
fun <H> findBarOf(find: FindController<H>, state: FindState<H>): (@Composable () -> Unit)? {
    if (!state.open) return null
    return {
        BackHandler(onBack = find::close)
        FindBar(state.query, state.current, state.hits.size, state.searching, find::query, find::step, find::close)
    }
}

/** `findBar` mile to search chalu hai, aur woh top bar ki jagah dikhta hai. */
@Composable
fun DocumentScaffold(
    title: String,
    actions: ReaderActions,
    barActions: @Composable RowScope.() -> Unit = {},
    findBar: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            if (findBar != null) findBar() else DocumentTopBar(title, actions, barActions)
        },
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

@Composable
private fun DocumentTopBar(title: String, actions: ReaderActions, barActions: @Composable RowScope.() -> Unit) {
    AppTopBar(
        title = stringResource(R.string.app_name),
        subtitle = title,
        onBack = actions.onBack,
    ) {
        barActions()
        var open by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { open = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more))
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
                item(R.string.action_share, actions.onShare)
                item(R.string.action_open_elsewhere, actions.onOpenElsewhere)
                item(R.string.action_report, actions.onReport)
            }
        }
    }
}
