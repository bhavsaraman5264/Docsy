package app.kholo.reader.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.kholo.reader.R
import app.kholo.reader.pdf.JumpThrottle
import app.kholo.reader.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BUTTON_SIZE = 48.dp
private const val JUMP_INTERVAL_MS = 50L
private const val HIDE_MS = 2_500L

/** Itni screens se chhoti list me button ka kaam nahi, haath se scroll kaafi hai. */
private const val MIN_SCREENS = 3

/**
 * PDF wala tez scroll button, Word, PowerPoint, Excel, CSV aur text ke liye. Scroll karte hi dikhta hai,
 * thodi der baad chhup jata hai. Kheencho to list ungli ke saath chalti hai, bubble me [label] dikhta hai.
 */
@Composable
fun ListScroller(
    list: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    // Page jaisi list (Word page view) me PDF wala niyam: itne page hon to button.
    minItems: Int? = null,
    // Diya ho to button dabane par PDF jaisa "page/row par jao" dialog.
    jump: JumpLabels? = null,
    label: @Composable (index: Int) -> String,
) {
    val long by remember(list, itemCount, minItems) {
        derivedStateOf {
            val visible = list.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            if (minItems != null) itemCount >= minItems else itemCount > visible * MIN_SCREENS
        }
    }
    if (!long) return
    var dragY by remember { mutableStateOf<Float?>(null) }
    var shown by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(list.isScrollInProgress, dragY == null) {
        if (list.isScrollInProgress || dragY != null) {
            shown = true
        } else {
            delay(HIDE_MS)
            shown = false
        }
    }
    AnimatedVisibility(visible = shown, modifier = modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
        Thumb(list, itemCount, dragY, { dragY = it }, label, onTap = jump?.let { { asking = true } })
    }
    if (asking && jump != null) {
        JumpDialog(
            labels = jump,
            count = itemCount,
            onJump = { index ->
                asking = false
                scope.launch { list.scrollToItem(index) }
            },
            onDismiss = { asking = false },
        )
    }
}

@Composable
private fun Thumb(
    list: LazyListState,
    itemCount: Int,
    dragY: Float?,
    onDragY: (Float?) -> Unit,
    label: @Composable (Int) -> String,
    onTap: (() -> Unit)?,
) {
    val buttonPx = with(LocalDensity.current) { BUTTON_SIZE.toPx() }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = Spacing.s, horizontal = Spacing.xs)) {
        val last = (itemCount - 1).coerceAtLeast(1)
        val travel = (constraints.maxHeight - buttonPx).coerceAtLeast(1f)
        val indexAt = remember(travel, last) { { y: Float -> (y / travel * last).roundToInt().coerceIn(0, last) } }
        // Jagah sirf layout me padhi jati hai, har scroll frame par button dobara nahi banta.
        val restingY = { list.firstVisibleItemIndex.toFloat() / last * travel }
        val bubbleIndex by remember(indexAt) { derivedStateOf { dragY?.let(indexAt) } }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (dragY ?: restingY()).coerceIn(0f, travel).roundToInt()) },
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bubbleIndex?.let { Bubble(label(it)) }
            Surface(
                onClick = { onTap?.invoke() },
                enabled = onTap != null,
                modifier = Modifier
                    .size(BUTTON_SIZE)
                    .pointerInput(travel, last) {
                        val throttle = JumpThrottle(JUMP_INTERVAL_MS)
                        var flushing: Job? = null
                        var y = 0f
                        val jump = { index: Int -> scope.launch { list.scrollToItem(index) } }
                        val finish = {
                            flushing?.cancel()
                            throttle.reset()
                            jump(indexAt(y))
                            onDragY(null)
                        }
                        detectVerticalDragGestures(
                            onDragStart = {
                                y = restingY()
                                onDragY(y)
                                throttle.reset()
                            },
                            onDragEnd = finish,
                            onDragCancel = finish,
                        ) { change, amount ->
                            change.consume()
                            y = (y + amount).coerceIn(0f, travel)
                            onDragY(y)
                            throttle.offer(indexAt(y), change.uptimeMillis)?.let(jump)
                            if (throttle.pending != null && flushing?.isActive != true) {
                                flushing = scope.launch {
                                    delay(throttle.waitFrom(SystemClock.uptimeMillis()))
                                    throttle.flush(SystemClock.uptimeMillis())?.let(jump)
                                }
                            }
                        }
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = Spacing.xs,
            ) {
                Icon(
                    painterResource(R.drawable.ic_unfold_more),
                    contentDescription = stringResource(R.string.list_scroller),
                    modifier = Modifier.padding(Spacing.m),
                )
            }
        }
    }
}

@Composable
private fun Bubble(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = Spacing.xs,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
