package app.kholo.reader.pdf

import android.graphics.RectF

data class SearchHit(val page: Int, val bounds: List<RectF>)

data class PdfSearchState(
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    val current: Int = -1,
    val searching: Boolean = false,
)

data class PdfUiState(
    val controlsVisible: Boolean = true,
    val page: Int = 0,
    val pageCount: Int = 0,
    val atFit: Boolean = true,
    val night: Boolean = false,
    val rotation: Int = 0,
    val search: PdfSearchState? = null,
    val showPageJump: Boolean = false,
    val scrollerVisible: Boolean = false,
    val scrubbing: Boolean = false,
)
