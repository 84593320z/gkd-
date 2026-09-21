package li.songe.gkd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AppPageScaffold
import li.songe.gkd.ui.component.LabeledField
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PreferenceGroup
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.asMutableState
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Serializable
data object AiParamsPageRoute : NavKey

@Composable
fun AiParamsPage() {
    val mainVm = LocalMainViewModel.current
    val store by storeFlow.collectAsState()
    val vm = viewModel<AiConfigVm>()
    LaunchedEffect(Unit) { vm.load(store.aiConfig) }

    var draft by vm.draftFlow.asMutableState()

    AppPageScaffold(
        title = "生成参数",
        navigationIcon = {
            PerfIconButton(
                imageVector = PerfIcon.ArrowBack,
                onClick = throttle { mainVm.popPage() },
            )
        },
        actions = {
            TextButton(
                text = "恢复默认",
                modifier = Modifier.padding(end = 8.dp),
                onClick = throttle {
                    vm.resetParams()
                    toast("已恢复默认生成参数")
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            PreferenceGroup(title = "采样") {
                LabeledField(
                    label = "Temperature",
                    value = draft.temperature,
                    onValueChange = { v -> vm.update { it.copy(temperature = v) } },
                    placeholder = "0 ~ 2，越大输出越随机",
                    keyboardType = KeyboardType.Decimal,
                )
                LabeledField(
                    label = "Top P",
                    value = draft.topP,
                    onValueChange = { v -> vm.update { it.copy(topP = v) } },
                    placeholder = "0 ~ 1，通常与 Temperature 二选一",
                    keyboardType = KeyboardType.Decimal,
                )
                LabeledField(
                    label = "Max Tokens",
                    value = draft.maxTokens,
                    onValueChange = { v -> vm.update { it.copy(maxTokens = v) } },
                    placeholder = "1 ~ ${AiConfigDraft.MAX_TOKENS_LIMIT}，规则 JSON 建议 4096",
                    keyboardType = KeyboardType.Number,
                )
            }

            PreferenceGroup(title = "提示") {
                Text(
                    text = "快照规则输出是一段结构化 JSON，Temperature 建议保持 0，" +
                            "较低取值可减少选择器漂移。修改后回到上一页点「保存」才会写入配置。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
