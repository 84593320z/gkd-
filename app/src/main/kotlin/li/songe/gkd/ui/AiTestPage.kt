package li.songe.gkd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AppPageScaffold
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PreferenceGroup
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.asMutableState
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.util.AiProtocolOption
import li.songe.gkd.util.AiRuleGenerator
import li.songe.gkd.util.findOption
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Serializable
data object AiTestPageRoute : NavKey

@Composable
fun AiTestPage() {
    val mainVm = LocalMainViewModel.current
    val scope = rememberCoroutineScope()
    val store by storeFlow.collectAsState()
    val vm = viewModel<AiConfigVm>()
    LaunchedEffect(Unit) { vm.load(store.aiConfig) }

    var draft by vm.draftFlow.asMutableState()
    var testing by vm.testingFlow.asMutableState()
    var testResult by vm.testResultFlow.asMutableState()

    val protocolOption = AiProtocolOption.objects.findOption(draft.protocol)

    AppPageScaffold(
        title = "连接测试",
        navigationIcon = {
            PerfIconButton(
                imageVector = PerfIcon.ArrowBack,
                onClick = throttle { mainVm.popPage() },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            PreferenceGroup(title = "待测配置") {
                SettingItem(
                    title = "协议",
                    subtitle = protocolOption.label,
                    imageVector = null,
                )
                SettingItem(
                    title = "API 地址",
                    subtitle = draft.apiUrl.ifBlank { "未填写" },
                    imageVector = null,
                )
                SettingItem(
                    title = "API Key",
                    subtitle = if (draft.apiKey.isBlank()) "未填写" else "已填写（${draft.apiKey.length} 位）",
                    imageVector = null,
                )
                SettingItem(
                    title = "模型",
                    subtitle = draft.model.ifBlank { "未填写" },
                    imageVector = null,
                )
            }

            PreferenceGroup(title = "执行") {
                TextButton(
                    text = if (testing) "测试中…" else "开始测试",
                    enabled = !testing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = throttle {
                        val config = draft.toConfig(store.aiConfig)
                        if (config == null) {
                            toast("请先修正生成参数")
                            return@throttle
                        }
                        if (config.apiUrl.isBlank() || config.apiKey.isBlank() || config.model.isBlank()) {
                            toast("请先填写完整配置")
                            return@throttle
                        }
                        testing = true
                        testResult = null
                        scope.launchTry {
                            AiRuleGenerator.testConnection(config)
                                .onSuccess {
                                    testResult = "连接成功"
                                    toast("连接测试成功")
                                }.onFailure { e ->
                                    testResult = "连接失败：${e.message}"
                                    toast("连接测试失败：${e.message}")
                                }
                            testing = false
                        }
                    },
                )
            }

            val result = testResult
            if (result != null) {
                PreferenceGroup(title = "结果") {
                    Text(
                        text = result,
                        style = MiuixTheme.textStyles.body2,
                        color = if (result == "连接成功") {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
