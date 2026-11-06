package app.kholo.reader.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

private const val MIN_FONT_SP = 12f
private const val MAX_FONT_SP = 36f
private const val TWO_FINGERS = 2

/**
 * Read mode ka zoom: do ungliyon se text ka size badalta hai. Text line me dobara fit hota hai,
 * isliye page tedha nahi hota aur side me scroll nahi karna padta. Ek ungli ka scroll waisa hi rehta hai.
 */
fun Modifier.pinchFontSize(current: () -> Int, onChange: (Int) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var exact = current().toFloat()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= TWO_FINGERS) {
                exact = (exact * event.calculateZoom()).coerceIn(MIN_FONT_SP, MAX_FONT_SP)
                // Poora text sirf tab dobara banta hai jab size sach me badle.
                val size = exact.roundToInt()
                if (size != current()) onChange(size)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
