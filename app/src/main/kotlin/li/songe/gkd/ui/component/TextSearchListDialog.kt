package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import li.songe.gkd.util.throttle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 可搜索的列表选择弹窗：选中项用主色文字标记，不显示对勾。
 * [searchPlaceholder] 为空时退化为普通列表。
 */
@Composable
fun TextSearchListDialog(
    onDismiss: () -> Unit,
    textList: List<Pair<String, () -> Unit>>,
    title: String? = null,
    selectedText: String? = null,
    searchPlaceholder: String? = null,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(textList, query) {
        if (query.isEmpty()) textList
        else textList.filter { it.first.contains(query, ignoreCase = true) }
    }
    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column {
            if (searchPlaceholder != null) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    label = searchPlaceholder,
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }
            if (filtered.isEmpty()) {
                Text(
                    text = "无匹配结果",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    filtered.forEach { (text, onClickItem) ->
                        PerfDropdownMenuItem(
                            text = text,
                            titleColor = if (text == selectedText) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                Color.Unspecified
                            },
                            onClick = throttle {
                                onDismiss()
                                onClickItem()
                            },
                        )
                    }
                }
            }
        }
    }
}
