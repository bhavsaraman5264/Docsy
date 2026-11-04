package app.kholo.reader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import app.kholo.reader.R
import app.kholo.reader.ui.theme.KholoTheme
import app.kholo.reader.ui.theme.Spacing

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_description),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action()
    }
}

@Preview(widthDp = 320)
@Composable
private fun EmptyStatePreview() {
    KholoTheme { EmptyState(message = "Koi file kholo, wo yahan dikhegi") }
}
