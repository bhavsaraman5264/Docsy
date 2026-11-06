package app.kholo.reader.pdf

import androidx.compose.runtime.Composable
import app.kholo.reader.ui.components.FindBar

@Composable
fun PdfSearchBar(state: PdfSearchState, onQuery: (String) -> Unit, onStep: (Int) -> Unit, onClose: () -> Unit) {
    FindBar(state.query, state.current, state.hits.size, state.searching, onQuery, onStep, onClose)
}
