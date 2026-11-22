package app.kholo.reader.ui.adaptive

import android.app.Activity
import android.os.Build
import android.view.Display

/**
 * Kai Xiaomi aur doosre phone anjaan app ko 60Hz par chalate hain, chahe screen 90 ya 120Hz ki ho.
 * Isi resolution ka sabse tez mode maangne se scroll, swipe aur zoom turant zyada smooth dikhte hain.
 * Resolution nahi badalta, isliye screen jhapakti nahi. App band hote hi phone apni setting par laut aata hai.
 */
fun Activity.preferHighRefreshRate() {
    val display = currentDisplay() ?: return
    val now = display.mode
    val best = display.supportedModes
        .filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
        .maxByOrNull { it.refreshRate }
    // Screen pehle se tez ho tab bhi maangte hain: nayi window ki maang khaali ho to phone 60 par laut sakta hai.
    if (best != null) {
        // Rate maangna Google ka sujhaya tarika hai. Mode id un phones ke liye, jaise Xiaomi,
        // jo sirf rate ki maang ko nazarandaz kar dete hain.
        window.attributes = window.attributes.also {
            it.preferredRefreshRate = best.refreshRate
            it.preferredDisplayModeId = best.modeId
        }
    }
}

private fun Activity.currentDisplay(): Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    display
} else {
    @Suppress("DEPRECATION")
    windowManager.defaultDisplay
}
