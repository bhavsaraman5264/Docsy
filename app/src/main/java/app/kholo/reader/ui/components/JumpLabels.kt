package app.kholo.reader.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.kholo.reader.R

/** Kya poochhna hai: "page" ya "row". Hint me `%1$d` ki jagah kul ginti aati hai. */
class JumpLabels(@StringRes val title: Int, @StringRes val hint: Int)

val PageJump = JumpLabels(R.string.page_jump_title, R.string.page_jump_hint)
val RowJump = JumpLabels(R.string.row_jump_title, R.string.row_jump_hint)

/** Seedha kisi page ya row par. [onJump] ko 0 se shuru hone wala number milta hai. */
@Composable
fun JumpDialog(labels: JumpLabels, count: Int, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val number = text.toIntOrNull()?.takeIf { it in 1..count }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(labels.title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(MAX_DIGITS) },
                singleLine = true,
                label = { Text(stringResource(labels.hint, count)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { number?.let { onJump(it - 1) } }, enabled = number != null) {
                Text(stringResource(R.string.action_go))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Int me 10 ank tak aate hain; isse lamba number crash nahi, bas galat maana jaye. */
private const val MAX_DIGITS = 9
