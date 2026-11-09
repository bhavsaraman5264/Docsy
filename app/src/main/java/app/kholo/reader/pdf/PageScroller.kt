package app.kholo.reader.pdf

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.kholo.reader.R
import app.kholo.reader.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val MIN_SCROLLER_PAGES = 5

private val BUTTON_SIZE = 48.dp
private val BOTTOM_CONTROLS_SPACE = 72.dp
private const val JUMP_INTERVAL_MS = 50L

/**
 * Tez drag me har beech wale page ka render na lage, isliye jump [interval] me ek baar. Par jo page reh gaya
 * use [pending] yaad rakhta hai, taaki ungli rukte hi document usi page par aa jaye, chhodne ka intezaar na ho.
 */
internal class JumpThrottle(private val interval: Long) {
    private var jumped = -1
    private var jumpedAt = Long.MIN_VALUE / 2
    var pending: Int? = null
        private set

    fun reset() {
        jumped = -1
        jumpedAt = Long.MIN_VALUE / 2
        pending = null
    }

    /** Abhi jaana hai to page, warna null aur page [pending] me. */
    fun offer(target: Int, now: Long): Int? = when {
        target == jumped -> {
            pending = null
            null
        }
        now - jumpedAt >= interval -> {
            jumped = target
            jumpedAt = now
            pending = null
            target
        }
        else -> {
            pending = target
            null
        }
    }

    fun waitFrom(now: Long): Long = (jumpedAt + interval - now).coerceAtLeast(0L)

    fun flush(now: Long): Int? = pending?.let { offer(it, now) }
}

/**
 * Daayein kinare ka button: kheencho to pura document ungli ke saath chalta hai, dabao to "page par jao".
 * Drag ki jagah sirf layout me padhi jati hai, isliye har touch par button dobara nahi banta. Bubble tabhi
 * badalta hai jab page number badle. Aakhri page hamesha jata hai.
 */
@Composable
fun PageScroller(
    page: Int,
    pageCount: Int,
    topInset: Dp,
    onDragStart: () -> Unit,
    onJump: (Int) -> Unit,
    onDragEnd: (Int) -> Unit,
    onClick: () -> Unit,
) {
    val buttonPx = with(LocalDensity.current) { BUTTON_SIZE.toPx() }
    var dragY by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val latestJump by rememberUpdatedState(onJump)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(top = topInset + Spacing.s, end = Spacing.xs, bottom = BOTTOM_CONTROLS_SPACE),
    ) {
        val lastPage = pageCount - 1
        val travel = (constraints.maxHeight - buttonPx).coerceAtLeast(1f)
        val restingY = page.toFloat() / lastPage * travel
        val latestRestingY by rememberUpdatedState(restingY)
        val pageAt = remember(travel, lastPage) { { y: Float -> (y / travel * lastPage).roundToInt() } }
        val bubblePage by remember(pageAt) { derivedStateOf { dragY?.let { pageAt(it) + 1 } } }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (dragY ?: latestRestingY).roundToInt()) },
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bubblePage?.let { PageBubble(it, pageCount) }
            Surface(
                onClick = onClick,
                modifier = Modifier
                    .size(BUTTON_SIZE)
                    .pointerInput(travel, lastPage) {
                        val throttle = JumpThrottle(JUMP_INTERVAL_MS)
                        var flushing: Job? = null
                        val finish = {
                            flushing?.cancel()
                            throttle.reset()
                            val target = pageAt(dragY ?: latestRestingY)
                            dragY = null
                            onDragEnd(target)
                        }
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragY = latestRestingY
                                throttle.reset()
                                onDragStart()
                            },
                            onDragEnd = finish,
                            onDragCancel = finish,
                        ) { change, amount ->
                            change.consume()
                            val moved = ((dragY ?: latestRestingY) + amount).coerceIn(0f, travel)
                            dragY = moved
                            throttle.offer(pageAt(moved), change.uptimeMillis)?.let(latestJump)
                            if (throttle.pending != null && flushing?.isActive != true) {
                                flushing = scope.launch {
                                    delay(throttle.waitFrom(SystemClock.uptimeMillis()))
                                    throttle.flush(SystemClock.uptimeMillis())?.let(latestJump)
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
                    contentDescription = stringResource(R.string.page_scroller),
                    modifier = Modifier.padding(Spacing.m),
                )
            }
        }
    }
}

@Composable
private fun PageBubble(page: Int, pageCount: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = Spacing.xs,
    ) {
        Text(
            text = stringResource(R.string.page_indicator, page, pageCount),
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
