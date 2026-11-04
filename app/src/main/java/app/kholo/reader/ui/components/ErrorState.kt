package app.kholo.reader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.kholo.reader.core.AppError
import app.kholo.reader.ui.theme.Spacing

@Composable
fun ErrorState(error: AppError, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(error.messageRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        actions()
    }
}
