package app.kholo.reader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.kholo.reader.R
import app.kholo.reader.core.AppError
import app.kholo.reader.ui.theme.Spacing

@Composable
fun ErrorState(error: AppError, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    EmptyState(
        title = stringResource(error.messageRes),
        modifier = modifier,
        icon = R.drawable.ic_error,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { actions() }
    }
}
