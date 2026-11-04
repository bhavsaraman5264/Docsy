package app.kholo.reader.ui.components

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import app.kholo.reader.R

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
        text = { Text(label) },
    )
}
