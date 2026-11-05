package app.kholo.reader.pdf

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.kholo.reader.R
import app.kholo.reader.ui.theme.Spacing

@Composable
fun PdfSearchBar(state: PdfSearchState, onQuery: (String) -> Unit, onStep: (Int) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
            }
            TextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).focusRequester(focus),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.action_search)) },
            )
            if (state.hits.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.search_count, state.current + 1, state.hits.size),
                    modifier = Modifier.padding(horizontal = Spacing.s),
                    style = MaterialTheme.typography.labelMedium,
                )
            } else if (state.query.isNotBlank() && !state.searching) {
                Text(
                    text = stringResource(R.string.search_none),
                    modifier = Modifier.padding(horizontal = Spacing.s),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            IconButton(onClick = { onStep(-1) }, enabled = state.hits.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_keyboard_arrow_up), stringResource(R.string.action_previous))
            }
            IconButton(onClick = { onStep(1) }, enabled = state.hits.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_keyboard_arrow_down), stringResource(R.string.action_next))
            }
        }
    }
}
