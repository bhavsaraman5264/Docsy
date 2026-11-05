package app.kholo.reader.pdf

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.kholo.reader.R
import app.kholo.reader.ui.components.AppTopBar
import app.kholo.reader.ui.theme.Spacing

/** Password sirf memory me rehta hai. Screenshot aur recent apps preview band hain. */
@Composable
fun PasswordScreen(name: String, failedAttempt: Boolean, onSubmit: (String) -> Unit, onBack: () -> Unit) {
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var password by remember { mutableStateOf("") }
    Scaffold(topBar = { AppTopBar(title = name, onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(stringResource(R.string.password_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.password_label)) },
                singleLine = true,
                isError = failedAttempt,
                supportingText = {
                    if (failedAttempt) Text(stringResource(R.string.error_wrong_password))
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Button(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.action_open))
            }
        }
    }
}
