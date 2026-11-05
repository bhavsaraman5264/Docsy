package app.kholo.reader.unlock

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import app.kholo.reader.R
import app.kholo.reader.ui.components.AppTopBar
import app.kholo.reader.ui.components.ChoiceChips
import app.kholo.reader.ui.theme.Spacing

private const val DOB_DIGITS = 8
private const val YEAR_DIGITS = 4
private const val PAN_LENGTH = 10
private const val MOBILE_DIGITS = 10

/** Details aur password sirf is screen ki memory me hain. Screenshot aur recent apps preview band hain. */
@Composable
fun UnlockScreen(
    name: String,
    failedAttempt: Boolean,
    detailsFailed: Boolean,
    onPassword: (String) -> Unit,
    onDetails: (DocKind, UnlockDetails) -> Unit,
    onBack: () -> Unit,
) {
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var kind by remember { mutableStateOf(DocClassifier.guess(name)) }
    var details by remember { mutableStateOf(UnlockDetails()) }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.app_name), subtitle = name, onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.password_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.password_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChoiceChips(
                options = DocKind.entries,
                selected = kind,
                label = { stringResource(kindLabel(it)) },
                onSelect = { kind = it },
            )
            if (kind != DocKind.Other) {
                DetailFields(kind, details) { details = it }
                if (kind == DocKind.Bank) {
                    Text(
                        stringResource(R.string.unlock_bank_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (detailsFailed) {
                    Text(
                        stringResource(R.string.unlock_details_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val ready = PasswordRules.candidates(kind, details).isNotEmpty()
                Button(
                    onClick = { onDetails(kind, details) },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                ) { Text(stringResource(R.string.action_open)) }
                HorizontalDivider(Modifier.padding(vertical = Spacing.s))
            }
            ManualPassword(failedAttempt, onPassword)
        }
    }
}

private fun kindLabel(kind: DocKind): Int = when (kind) {
    DocKind.Aadhaar -> R.string.kind_aadhaar
    DocKind.IncomeTax -> R.string.kind_income_tax
    DocKind.Form16 -> R.string.kind_form16
    DocKind.Form26As -> R.string.kind_26as
    DocKind.Bank -> R.string.kind_bank
    DocKind.Other -> R.string.kind_other
}

@Composable
private fun DetailFields(kind: DocKind, details: UnlockDetails, onChange: (UnlockDetails) -> Unit) {
    val needsName = kind == DocKind.Aadhaar || kind == DocKind.Bank
    val needsPan = kind == DocKind.IncomeTax || kind == DocKind.Form16
    if (needsName) {
        Field(R.string.unlock_name, details.name, KeyboardType.Text, KeyboardCapitalization.Characters) {
            onChange(details.copy(name = it))
        }
    }
    if (needsPan) {
        Field(R.string.unlock_pan, details.pan, KeyboardType.Text, KeyboardCapitalization.Characters) {
            onChange(details.copy(pan = it.take(PAN_LENGTH)))
        }
    }
    if (kind == DocKind.Aadhaar) {
        Field(R.string.unlock_year, details.birthYear, KeyboardType.Number) {
            onChange(details.copy(birthYear = it.filter(Char::isDigit).take(YEAR_DIGITS)))
        }
    } else {
        Field(R.string.unlock_dob, details.birthDate, KeyboardType.Number) {
            onChange(details.copy(birthDate = it.filter(Char::isDigit).take(DOB_DIGITS)))
        }
    }
    if (kind == DocKind.Bank) {
        Field(R.string.unlock_mobile, details.mobile, KeyboardType.Number) {
            onChange(details.copy(mobile = it.filter(Char::isDigit).take(MOBILE_DIGITS)))
        }
    }
}

@Composable
private fun Field(
    label: Int,
    value: String,
    type: KeyboardType,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type, capitalization = capitalization),
    )
}

@Composable
private fun ManualPassword(failedAttempt: Boolean, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val submit = { if (password.isNotEmpty()) onSubmit(password) }
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.password_label)) },
        singleLine = true,
        isError = failedAttempt,
        supportingText = { if (failedAttempt) Text(stringResource(R.string.error_wrong_password)) },
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(stringResource(if (visible) R.string.action_hide else R.string.action_show))
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
    )
    Button(
        onClick = submit,
        enabled = password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) { Text(stringResource(R.string.action_open_with_password)) }
}
