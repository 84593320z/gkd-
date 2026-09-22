package li.songe.gkd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PreferenceGroup
import li.songe.gkd.util.throttle
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 分组小标题 + 圆角卡片：AI 设置相关页面统一用这个壳。 */
@Composable
fun AiSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 2.dp),
        )
        PreferenceGroup(content = content)
    }
}

@Composable
fun AiRowDivider(hasLeading: Boolean = true) {
    HorizontalDivider(
        modifier = Modifier.padding(start = if (hasLeading) 16.dp else 0.dp),
        thickness = 1.dp,
    )
}

/** 行首圆角图标底座，对齐 MIUIX 设置的分组图标。 */
@Composable
fun AiRowIcon(
    imageVector: ImageVector,
    tint: Color = MiuixTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .padding(end = 14.dp)
            .size(34.dp)
            .background(
                MiuixTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        PerfIcon(
            imageVector = imageVector,
            modifier = Modifier.size(19.dp),
            tint = if (enabled) tint else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 能力 / 状态小胶囊。 */
@Composable
fun AiTagChip(
    text: String,
    emphasized: Boolean = false,
) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = if (emphasized) {
            MiuixTheme.colorScheme.onPrimaryContainer
        } else {
            MiuixTheme.colorScheme.onSecondaryContainer
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(
                if (emphasized) {
                    MiuixTheme.colorScheme.primaryContainer
                } else {
                    MiuixTheme.colorScheme.secondaryContainer
                },
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun AiHint(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = if (error) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** 右侧显示当前值的单选行，点击后由调用方弹出选择框。 */
@Composable
fun AiPickerRow(
    title: String,
    value: String,
    summary: String? = null,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        onClick = throttle(fn = onClick),
        endActions = {
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** 128000 -> 128K，1048576 -> 1M；0 表示未记录。 */
fun formatTokens(count: Int): String = when {
    count <= 0 -> "未知"
    count % 1_000_000 == 0 -> "${count / 1_000_000}M"
    count >= 1000 -> "${count / 1000}K"
    else -> count.toString()
}
