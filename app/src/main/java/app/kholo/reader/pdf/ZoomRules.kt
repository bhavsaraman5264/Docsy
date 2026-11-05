package app.kholo.reader.pdf

private const val LIBRARY_MIN_ZOOM = 0.5f
private const val LIBRARY_MAX_ZOOM = 25f
private const val MAX_ZOOM_OVER_FIT = 8f
private const val FIT_EPSILON = 0.01f
private const val SNAP_TO_FIT_RANGE = 0.08f

/** "PDF zoom ke niyam" ka pure logic. Isme Android UI code nahi hai. */
object ZoomRules {
    /** Woh zoom jis par [pageWidth] points ka page [viewWidth] pixels me theek baithta hai. */
    fun fitZoom(viewWidth: Int, pageWidth: Int): Float {
        if (viewWidth <= 0 || pageWidth <= 0) return 1f
        return (viewWidth.toFloat() / pageWidth).coerceIn(LIBRARY_MIN_ZOOM, LIBRARY_MAX_ZOOM)
    }

    /** Sabse chauda page poora dikhe, isse zyada zoom out nahi. Isse khaali kinare nahi aate. */
    fun minZoom(viewWidth: Int, widestPageWidth: Int): Float = fitZoom(viewWidth, widestPageWidth)

    fun maxZoom(minZoom: Float): Float = (minZoom * MAX_ZOOM_OVER_FIT).coerceAtMost(LIBRARY_MAX_ZOOM)

    fun isAtFit(zoom: Float, fit: Float): Boolean = kotlin.math.abs(zoom - fit) <= FIT_EPSILON * fit

    /** Pinch ke baad page fit ke itna paas ho to thoda tedha chhodne ke bajay seedha fit karo. */
    fun shouldSnapToFit(zoom: Float, fit: Float): Boolean =
        !isAtFit(zoom, fit) && kotlin.math.abs(zoom - fit) <= SNAP_TO_FIT_RANGE * fit

    /** Screen ke point ke neeche page ka kaunsa point hai (library ki coordinate formula). */
    fun contentAt(viewCoord: Float, scroll: Int, zoom: Float, inset: Int): Float = (viewCoord + scroll - inset) / zoom

    /** Woh scroll jisse page ka [content] point screen ke [viewCoord] par aa jaye. */
    fun scrollFor(content: Float, zoom: Float, viewCoord: Float, inset: Int): Float = content * zoom - viewCoord + inset

    /**
     * Rotate ya window resize ke baad naya zoom. Jo user fit par tha wo fit par hi rahe,
     * aur jisne zoom kiya tha uska page par dikhne wala size badi screen ke hisaab se bana rahe.
     */
    fun zoomAfterResize(
        oldZoom: Float,
        oldViewWidth: Int,
        newViewWidth: Int,
        pageWidth: Int,
        widestPageWidth: Int,
    ): Float {
        val oldFit = fitZoom(oldViewWidth, pageWidth)
        val newFit = fitZoom(newViewWidth, pageWidth)
        val wanted = if (isAtFit(oldZoom, oldFit)) newFit else oldZoom * newFit / oldFit
        val min = minZoom(newViewWidth, widestPageWidth)
        return wanted.coerceIn(min, maxZoom(min))
    }
}
