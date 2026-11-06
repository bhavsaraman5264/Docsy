package app.kholo.reader.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kholo.reader.R
import app.kholo.reader.ui.theme.LocalBarColors

private const val MAX_BAR_SCALE = 1.6f

/** Phone ki badi font setting par bar bhi badhta hai, warna title kat jata. */
@Composable
fun scaledBarHeight(height: Dp): Dp = height * LocalDensity.current.fontScale.coerceIn(1f, MAX_BAR_SCALE)

/** Coffee rang ka bar, dono theme me. Status bar ke peeche bhi yahi rang jata hai. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    subtitle: String? = null,
    height: Dp = 56.dp,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val bar = LocalBarColors.current
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (subtitle == null) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = bar.quiet,
                    )
                }
            }
        },
        navigationIcon = {
            if (onMenu != null) {
                IconButton(onClick = onMenu) {
                    Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.action_menu))
                }
            }
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        },
        actions = actions,
        expandedHeight = scaledBarHeight(height),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = bar.container,
            scrolledContainerColor = bar.container,
            navigationIconContentColor = bar.content,
            titleContentColor = bar.content,
            actionIconContentColor = bar.content,
        ),
    )
}

/** Top bar ka bhara hua button: cream par coffee text, taaki dono theme me bar se alag dikhe. */
@Composable
fun BarTextButton(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val bar = LocalBarColors.current
    TextButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.textButtonColors(containerColor = bar.content, contentColor = bar.container),
        content = content,
    )
}
