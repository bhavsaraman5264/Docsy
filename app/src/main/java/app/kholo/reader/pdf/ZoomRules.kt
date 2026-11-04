package app.kholo.reader.pdf

private const val LIBRARY_MIN_ZOOM = 0.5f
private const val LIBRARY_MAX_ZOOM = 25f
private const val MAX_ZOOM_OVER_FIT = 8f
private const val FIT_EPSILON = 0.01f

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
