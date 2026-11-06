package app.kholo.reader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Itna khinchne par hi page band ho, taaki halke se chhune par na nikal jaye. */
private val BACK_DISTANCE = 96.dp

/** Page ungli ke saath chalta hai. Kam khinchne par wapas apni jagah aa jata hai. */
@Composable
fun SwipeBack(onBack: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val back by rememberUpdatedState(onBack)
    val distance = with(LocalDensity.current) { BACK_DISTANCE.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(distance) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(offset.value) >= distance) back() else scope.launch { offset.animateTo(0f) }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + amount) }
                }
            }
            .graphicsLayer { translationX = offset.value },
    ) { content() }
}
