package app.kholo.reader.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.kholo.reader.R
import app.kholo.reader.core.AppError
import app.kholo.reader.ui.components.AppTopBar
import app.kholo.reader.ui.components.ErrorState
import app.kholo.reader.ui.theme.Spacing

@Composable
fun OpenFailedScreen(
    error: AppError,
    name: String?,
    onBack: () -> Unit,
    onOpenElsewhere: () -> Unit,
    onReport: () -> Unit,
) {
    Scaffold(topBar = {
        AppTopBar(title = stringResource(R.string.app_name), subtitle = name, onBack = onBack)
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ErrorState(error) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (error != AppError.FileGone) {
                        Button(onClick = onOpenElsewhere, shape = MaterialTheme.shapes.small) {
                            Text(stringResource(R.string.action_open_elsewhere))
                        }
                    }
                    OutlinedButton(onClick = onReport, shape = MaterialTheme.shapes.small) {
                        Text(stringResource(R.string.action_report))
                    }
                }
            }
        }
    }
}
