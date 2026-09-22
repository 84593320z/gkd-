package li.songe.gkd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import li.songe.gkd.store.AiConfig
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AppPageScaffold
import li.songe.gkd.ui.component.PerfCheckbox
import li.songe.gkd.ui.component.PerfCustomIconButton
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.AiProtocolOption
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Serializable
data object AiProvidersPageRoute : NavKey

/** id 为 null 表示新建，protocol 决定新建时的默认协议。 */
@Serializable
data class AiProviderDetailRoute(
    val id: String? = null,
    val protocol: String = "openai",
) : NavKey

@Composable
fun AiProvidersPage() {
    val mainVm = LocalMainViewModel.current
    val scope = rememberCoroutineScope()
    val store by storeFlow.collectAsState()
    var query by remember { mutableStateOf("") }

    val providers = store.aiProviders
    val activeId = store.activeAiProvider()?.id
    val filtered = remember(providers, query) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            providers
        } else {
            providers.filter {
                it.name.contains(keyword, true) ||
                        it.apiUrl.contains(keyword, true) ||
                        it.model.contains(keyword, true)
            }
        }
    }

    fun confirmDelete(provider: AiConfig) = scope.launch {
        mainVm.dialogFlow.waitResult(
            title = "移除服务商",
            text = "确定移除「${provider.name.ifBlank { "未命名" }}」？它的模型列表会一并删除。",
            confirmText = "移除",
            error = true,
        )
        AiProviders.remove(provider.id)
        toast("已移除 ${provider.name.ifBlank { "未命名" }}")
    }

    AppPageScaffold(
        title = "AI 服务商",
        navigationIcon = {
            PerfIconButton(
                imageVector = PerfIcon.ArrowBack,
                onClick = throttle { mainVm.popPage() },
            )
        },
    ) { contentPadding ->
        LazyColumn(modifier = Modifier.scaffoldPadding(contentPadding)) {
            item(key = "add") {
                AiSection(title = "添加服务商") {
                    AiProtocolOption.objects.forEachIndexed { index, option ->
                        if (index > 0) {
                            AiRowDivider(hasLeading = false)
                        }
                        BasicComponent(
                            title = option.newTitle,
                            summary = option.newSummary,
                            startAction = { AiRowIcon(imageVector = PerfIcon.Api) },
                            endActions = { PerfIcon(imageVector = MiuixIcons.AddCircle) },
                            onClick = throttle {
                                mainVm.navigatePage(AiProviderDetailRoute(protocol = option.value))
                            },
                        )
                    }
                }
            }

            item(key = "help") {
                AiSection(title = "关于") {
                    BasicComponent(
                        title = "使用说明",
                        summary = "快照生成规则的流程与加强模式",
                        startAction = { AiRowIcon(imageVector = PerfIcon.HelpOutline) },
                        onClick = throttle { mainVm.navigatePage(AiHelpPageRoute) },
                    )
                }
            }

            item(key = "list") {
                AiSection(title = "已配置 ${providers.size} 个") {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        label = "搜索名称 / 地址 / 模型",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                    if (filtered.isEmpty()) {
                        AiHint(
                            text = if (providers.isEmpty()) {
                                "还没有服务商，用上面的入口新建一个"
                            } else {
                                "没有匹配的服务商"
                            },
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        filtered.forEach { provider ->
                            AiRowDivider(hasLeading = false)
                            val isActive = provider.id == activeId
                            val activate = throttle { AiProviders.setActive(provider.id) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        onClick = throttle {
                                            mainVm.navigatePage(AiProviderDetailRoute(id = provider.id))
                                        },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = provider.name.ifBlank { "未命名" },
                                            style = MiuixTheme.textStyles.headline2,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = provider.apiUrl.ifBlank { "未填写地址" },
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    PerfCheckbox(
                                        checked = isActive,
                                        key = provider.id,
                                        onCheckedChange = { activate() },
                                    )
                                    PerfCustomIconButton(
                                        size = 36.dp,
                                        iconSize = 19.dp,
                                        onClickLabel = "移除服务商",
                                        onClick = throttle(fn = { confirmDelete(provider) }),
                                        imageVector = MiuixIcons.Delete,
                                        contentDescription = "移除",
                                        tint = MiuixTheme.colorScheme.error,
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    AiTagChip(text = provider.protocolLabel)
                                    AiTagChip(text = "模型 ${provider.models.size}")
                                    if (provider.model.isNotBlank()) {
                                        AiTagChip(text = provider.model)
                                    }
                                    if (!provider.enabled) {
                                        AiTagChip(text = "已停用")
                                    }
                                    if (isActive) {
                                        AiTagChip(text = "当前", emphasized = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
