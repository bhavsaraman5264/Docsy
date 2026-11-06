package app.kholo.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import app.kholo.reader.R
import app.kholo.reader.ui.theme.LocalBarColors
import app.kholo.reader.ui.theme.Spacing

/** Dark theme me bhi padha jaye, isliye highlight par text hamesha kaala hai. */
val FindHitColor = Color(0xFFFFE082)
val FindCurrentColor = Color(0xFFFFA726)

/** PDF, Word, Excel aur TXT ka ek hi search bar. */
@Composable
fun FindBar(
    query: String,
    current: Int,
    total: Int,
    searching: Boolean,
    onQuery: (String) -> Unit,
    onStep: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Spacer(
            Modifier.fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(LocalBarColors.current.container),
        )
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
            }
            TextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).focusRequester(focus),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.action_search)) },
            )
            if (total > 0) {
                Text(
                    text = stringResource(R.string.search_count, current + 1, total),
                    modifier = Modifier.padding(horizontal = Spacing.s),
                    style = MaterialTheme.typography.labelMedium,
                )
            } else if (query.isNotBlank() && !searching) {
                Text(
                    text = stringResource(R.string.search_none),
                    modifier = Modifier.padding(horizontal = Spacing.s),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            IconButton(onClick = { onStep(-1) }, enabled = total > 0) {
                Icon(painterResource(R.drawable.ic_keyboard_arrow_up), stringResource(R.string.action_previous))
            }
            IconButton(onClick = { onStep(1) }, enabled = total > 0) {
                Icon(painterResource(R.drawable.ic_keyboard_arrow_down), stringResource(R.string.action_next))
            }
        }
    }
}

/** Top bar ka search button. */
@Composable
fun FindAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(R.drawable.ic_search), stringResource(R.string.action_search))
    }
}

/** Text ke andar milan ki jagah, `start` se `end` tak. */
class FindRange(val start: Int, val end: Int, val current: Boolean)

/** Range text se bahar ho (jaise rich aur plain text me fark) to use chhod do, crash nahi. */
fun AnnotatedString.highlighted(ranges: List<FindRange>): AnnotatedString {
    if (ranges.isEmpty()) return this
    val builder = AnnotatedString.Builder(this)
    ranges.filter { it.start >= 0 && it.end <= length && it.start < it.end }.forEach { range ->
        val background = if (range.current) FindCurrentColor else FindHitColor
        builder.addStyle(SpanStyle(color = Color.Black, background = background), range.start, range.end)
    }
    return builder.toAnnotatedString()
}
