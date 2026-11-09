package app.kholo.reader.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kholo.reader.R
import app.kholo.reader.ui.adaptive.WidthClass
import app.kholo.reader.ui.adaptive.rememberWindowInfo
import app.kholo.reader.ui.theme.Spacing
import app.kholo.reader.ui.theme.ToolTone
import app.kholo.reader.ui.theme.fileTypeColor

private val BADGE_SIZE = 40.dp
private val BADGE_ICON_SIZE = 22.dp
private val FLOW_ARROW_SIZE = 14.dp
private val CARD_LINE = 1.dp
private const val BADGE_TINT = 0.14f
private const val DARK_LUMINANCE = 0.5f
private const val CHIP_TEXT_SP = 11f
private const val HINT_LINES = 3
private const val COMPACT_COLUMNS = 2
private const val MEDIUM_COLUMNS = 3
private const val EXPANDED_COLUMNS = 4
private const val TOOL_ROW = "tools"
private const val GROUP_TITLE = "title"

internal fun toolColumns(width: WidthClass): Int = when (width) {
    WidthClass.Compact -> COMPACT_COLUMNS
    WidthClass.Medium -> MEDIUM_COLUMNS
    WidthClass.Expanded -> EXPANDED_COLUMNS
}

internal fun toolRows(group: ToolGroup, columns: Int): List<List<ToolKind>> =
    ToolKind.entries.filter { it.group == group }.chunked(columns)

/**
 * Tools tab. Lazy list ki har row me cards hote hain, grid nahi: isse ek row ke cards hamesha barabar oonche
 * rehte hain, chahe Hindi ya badi font se ek card ka text lamba ho jaye.
 */
@Composable
fun ToolGrid(onTool: (ToolKind) -> Unit) {
    val columns = toolColumns(rememberWindowInfo().widthClass)
    val sections = remember(columns) { ToolGroup.entries.map { it to toolRows(it, columns) } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screenPadding,
            end = Spacing.screenPadding,
            top = Spacing.m,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item(key = "private") { PrivateNote() }
        sections.forEach { (group, rows) ->
            item(key = group.name, contentType = GROUP_TITLE) {
                Text(
                    text = stringResource(group.title),
                    modifier = Modifier.padding(top = Spacing.s),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            items(rows, key = { it.first().name }, contentType = { TOOL_ROW }) { row ->
                ToolCardRow(row, onTool)
            }
        }
    }
}

@Composable
private fun PrivateNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_lock),
            contentDescription = null,
            modifier = Modifier.size(FLOW_ARROW_SIZE + Spacing.xs),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.tools_private),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolCardRow(row: List<ToolKind>, onTool: (ToolKind) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        row.forEach { tool -> ToolCard(tool, Modifier.weight(1f).fillMaxHeight(), onTool) }
    }
}

@Composable
private fun ToolCard(tool: ToolKind, modifier: Modifier, onTool: (ToolKind) -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright)
            .border(CARD_LINE, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Button) { onTool(tool) }
            .padding(Spacing.m),
    ) {
        ToolBadge(tool)
        Text(
            text = stringResource(tool.title),
            modifier = Modifier.padding(top = Spacing.m),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(tool.hint),
            modifier = Modifier.padding(top = Spacing.xs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = HINT_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < DARK_LUMINANCE

@Composable
internal fun ToolTone.color(): Color = if (isDark()) dark else light

/** Sirf sajawat hai: tool ka naam card me likha hai, isliye screen reader ise chhod deta hai. */
@Composable
internal fun ToolBadge(tool: ToolKind, modifier: Modifier = Modifier) {
    val flow = tool.flow
    if (flow != null) {
        Row(
            modifier = modifier.clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeChip(flow.first)
            Icon(
                painterResource(R.drawable.ic_arrow_forward),
                contentDescription = null,
                modifier = Modifier.size(FLOW_ARROW_SIZE),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TypeChip(flow.second)
        }
    } else {
        val tone = tool.tone.color()
        Box(
            modifier = modifier
                .size(BADGE_SIZE)
                .background(tone.copy(alpha = BADGE_TINT), MaterialTheme.shapes.medium)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(tool.icon), null, Modifier.size(BADGE_ICON_SIZE), tint = tone)
        }
    }
}

/** Font setting badi ho to bhi yeh dabbe me hi rahe, isliye text phone ki font ke saath nahi badhta. */
@Composable
internal fun TypeChip(label: String, modifier: Modifier = Modifier) {
    val color = fileTypeColor(label, isDark())
    val fontScale = LocalDensity.current.fontScale
    Box(
        modifier = modifier
            .widthIn(min = BADGE_SIZE)
            .heightIn(min = BADGE_SIZE)
            .background(color.copy(alpha = BADGE_TINT), MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = (CHIP_TEXT_SP / fontScale).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
