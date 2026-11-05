package app.kholo.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kholo.reader.ui.theme.Spacing
import app.kholo.reader.ui.theme.fileTypeColor

private val BADGE_SIZE = 40.dp
private val TEXT_START = 68.dp
private const val BADGE_TINT = 0.12f
private const val DARK_LUMINANCE = 0.5f

/** Saadi list row. Patli line se alag hoti hai, card se nahi. */
@Composable
fun FileRow(
    title: String,
    subtitle: String,
    typeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < DARK_LUMINANCE
    val typeColor = fileTypeColor(typeLabel, dark)
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = Spacing.screenPadding, top = Spacing.m, bottom = Spacing.m, end = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(BADGE_SIZE)
                    .background(typeColor.copy(alpha = BADGE_TINT), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = typeLabel, color = typeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing()
        }
        HorizontalDivider(Modifier.padding(start = TEXT_START), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
