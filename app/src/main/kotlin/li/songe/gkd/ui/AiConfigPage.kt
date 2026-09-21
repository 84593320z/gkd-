package li.songe.gkd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.update
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AppPageScaffold
import li.songe.gkd.ui.component.LabeledField
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PreferenceGroup
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextSearchListDialog
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.asMutableState
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.util.AiProtocolOption
import li.songe.gkd.util.AiRuleGenerator
import li.songe.gkd.util.findOption
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Serializable
data object AiConfigPageRoute : NavKey

@Composable
fun AiConfigPage() {
    val mainVm = LocalMainViewModel.current
    val scope = rememberCoroutineScope()
    val store by storeFlow.collectAsState()
    val vm = viewModel<AiConfigVm>()
    LaunchedEffect(Unit) { vm.load(store.aiConfig) }

    var draft by vm.draftFlow.asMutableState()
    var modelList by vm.modelListFlow.asMutableState()
    var fetchingModel by vm.fetchingModelFlow.asMutableState()
    var showModelDlg by vm.showModelDlgFlow.asMutableState()
    var showProtocolDlg by vm.showProtocolDlgFlow.asMutableState()

    val protocolOption = AiProtocolOption.objects.findOption(draft.protocol)

    if (showProtocolDlg) {
        TextSearchListDialog(
            onDismiss = { showProtocolDlg = false },
            title = "协议",
            selectedText = protocolOption.label,
            textList = AiProtocolOption.objects.map { option ->
                option.label to { vm.update { it.copy(protocol = option.value) } }
            },
        )
    }

    if (showModelDlg && modelList.isNotEmpty()) {
        TextSearchListDialog(
            onDismiss = { showModelDlg = false },
            title = "选择模型",
            selectedText = draft.model,
            searchPlaceholder = "搜索模型",
            textList = modelList.map { name ->
                name to { vm.update { it.copy(model = name) } }
            },
        )
    }

    AppPageScaffold(
        title = "AI 规则设置",
        navigationIcon = {
            PerfIconButton(
                imageVector = PerfIcon.ArrowBack,
                onClick = {
                    vm.finishEditing()
                    mainVm.popPage()
                },
            )
        },
        actions = {
            TextButton(
                text = "保存",
                modifier = Modifier.padding(end = 8.dp),
                onClick = throttle {
                    val config = draft.toConfig(store.aiConfig)
                    if (config == null) {
                        toast("生成参数超出范围：Temperature 0~2、Top P 0~1、Max Tokens 1~${AiConfigDraft.MAX_TOKENS_LIMIT}")
                        return@throttle
                    }
                    storeFlow.update { it.copy(aiConfig = config) }
                    vm.finishEditing()
                    toast("AI 配置已保存")
                    mainVm.popPage()
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
            PreferenceGroup(title = "接口") {
                PickerRow(
                    title = "协议",
                    value = protocolOption.label,
                    onClick = { showProtocolDlg = true },
                )
                LabeledField(
                    label = "API 地址",
                    value = draft.apiUrl,
                    onValueChange = { v -> vm.update { it.copy(apiUrl = v) } },
                    placeholder = protocolOption.placeholder,
                )
                LabeledField(
                    label = "API Key",
                    value = draft.apiKey,
                    onValueChange = { v -> vm.update { it.copy(apiKey = v) } },
                    placeholder = "请输入 API Key",
                )
            }

            PreferenceGroup(title = "模型") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "模型",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = draft.model,
                            onValueChange = { v -> vm.update { it.copy(model = v) } },
                            modifier = Modifier.weight(1f),
                            label = "模型名称（推荐 flash 模型）",
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            text = if (fetchingModel) "获取中" else "获取",
                            enabled = !fetchingModel,
                            minWidth = 40.dp,
                            onClick = throttle {
                                val config = draft.toConfig(store.aiConfig)
                                if (config == null) {
                                    toast("请先修正生成参数")
                                    return@throttle
                                }
                                if (config.apiUrl.isBlank() || config.apiKey.isBlank()) {
                                    toast("请先填写 API 地址和 Key")
                                    return@throttle
                                }
                                fetchingModel = true
                                scope.launchTry {
                                    AiRuleGenerator.fetchModelList(config)
                                        .onSuccess { list ->
                                            modelList = list
                                            if (list.isEmpty()) {
                                                toast("该接口未返回模型列表")
                                            } else {
                                                showModelDlg = true
                                                if (draft.model.isBlank()) {
                                                    vm.update { it.copy(model = list.first()) }
                                                }
                                                toast("获取到 ${list.size} 个模型")
                                            }
                                        }.onFailure { e ->
                                            toast("获取模型列表失败：${e.message}")
                                        }
                                    fetchingModel = false
                                }
                            },
                        )
                        if (modelList.isNotEmpty()) {
                            TextButton(
                                text = "选择",
                                minWidth = 40.dp,
                                onClick = throttle { showModelDlg = true },
                            )
                        }
                    }
                }
            }

            PreferenceGroup(title = "生成与连接") {
                SettingItem(
                    title = "生成参数",
                    subtitle = draft.paramSummary,
                    onClick = { mainVm.navigatePage(AiParamsPageRoute) },
                )
                SettingItem(
                    title = "连接测试",
                    subtitle = "校验 API 地址、Key 与模型是否可用",
                    onClick = { mainVm.navigatePage(AiTestPageRoute) },
                )
                SettingItem(
                    title = "使用说明",
                    subtitle = "快照生成规则的流程与加强模式",
                    onClick = { mainVm.navigatePage(AiHelpPageRoute) },
                )
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        onClick = throttle(fn = onClick),
        endActions = {
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        },
    )
}
