package li.songe.gkd.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import li.songe.gkd.store.AiConfig
import li.songe.gkd.store.AiEndpointMode
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AppPageScaffold
import li.songe.gkd.ui.component.LabeledField
import li.songe.gkd.ui.component.PerfCustomIconButton
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PreferenceGroup
import li.songe.gkd.ui.component.TextSearchListDialog
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.util.AiProtocolOption
import li.songe.gkd.util.AiRuleGenerator
import li.songe.gkd.util.findOption
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AiProviderDetailPage(route: AiProviderDetailRoute) {
    val mainVm = LocalMainViewModel.current
    val scope = rememberCoroutineScope()
    val store by storeFlow.collectAsState()
    var createdId by remember { mutableStateOf<String?>(null) }

    val providerId = route.id ?: createdId
    val provider = providerId?.let { id -> store.aiProviders.firstOrNull { it.id == id } }

    if (providerId != null && provider == null) {
        MissingProviderPage(onBack = { mainVm.popPage() })
        return
    }

    val isNew = provider == null
    var tab by remember { mutableIntStateOf(0) }
    var draft by remember(providerId) {
        mutableStateOf(provider?.let(AiProviderDraft::of) ?: AiProviderDraft.new(route.protocol))
    }

    AppPageScaffold(
        title = if (isNew) "新建服务商" else draft.name.ifBlank { "服务商" },
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
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            if (!isNew) {
                TabRow(
                    tabs = listOf("配置", "模型"),
                    selectedTabIndex = tab,
                    onTabSelected = { tab = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                if (isNew || tab == 0) {
                    AiProviderConfigTab(
                        provider = provider ?: AiConfig(),
                        draft = draft,
                        isNew = isNew,
                        scope = scope,
                        onDraftChange = { draft = it },
                        onCreated = { createdId = it },
                        onRemoved = { mainVm.popPage() },
                    )
                } else {
                    AiProviderModelsTab(provider)
                }
            }
        }
    }
}

@Composable
private fun MissingProviderPage(onBack: () -> Unit) {
    AppPageScaffold(
        title = "AI 服务商",
        navigationIcon = {
            PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = throttle(fn = onBack))
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "该服务商已被移除",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(text = "返回", onClick = throttle(fn = onBack))
        }
    }
}

@Composable
private fun AiProviderConfigTab(
    provider: AiConfig,
    draft: AiProviderDraft,
    isNew: Boolean,
    scope: CoroutineScope,
    onDraftChange: (AiProviderDraft) -> Unit,
    onCreated: (String) -> Unit,
    onRemoved: () -> Unit,
) {
    val mainVm = LocalMainViewModel.current
    var apiKeyVisible by remember { mutableStateOf(false) }
    var headersExpanded by remember { mutableStateOf(false) }
    var showEndpointDlg by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val protocolOption = AiProtocolOption.objects.findOption(draft.protocol)
    val baseUrl = draft.apiUrl.ifBlank { protocolOption.placeholder }

    fun buildDraftProvider(): AiConfig = draft.toProvider(provider)

    fun update(transform: (AiProviderDraft) -> AiProviderDraft) {
        onDraftChange(transform(draft))
        // 任何一次改动都让上一次测试结果失效
        testResult = null
    }

    if (showEndpointDlg) {
        TextSearchListDialog(
            onDismiss = { showEndpointDlg = false },
            title = "端点模式",
            selectedText = AiEndpointMode.labels[draft.endpointMode],
            textList = listOf(AiEndpointMode.CHAT, AiEndpointMode.RESPONSES).map { mode ->
                AiEndpointMode.labels[mode]!! to { update { it.copy(endpointMode = mode) } }
            },
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "connection") {
            AiSection(title = "连接配置") {
                Column(modifier = Modifier.padding(16.dp)) {
                    FieldLabel("名称")
                    TextField(
                        value = draft.name,
                        onValueChange = { v -> update { it.copy(name = v) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = "例如 DeepSeek 官方",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FieldLabel("Base URL")
                    TextField(
                        value = draft.apiUrl,
                        onValueChange = { v -> update { it.copy(apiUrl = v) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = protocolOption.placeholder,
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FieldLabel("API Key")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = draft.apiKey,
                            onValueChange = { v -> update { it.copy(apiKey = v) } },
                            modifier = Modifier.weight(1f),
                            label = "请输入 API Key",
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (apiKeyVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        PerfCustomIconButton(
                            size = 36.dp,
                            iconSize = 19.dp,
                            onClickLabel = if (apiKeyVisible) "隐藏 API Key" else "显示 API Key",
                            onClick = { apiKeyVisible = !apiKeyVisible },
                            imageVector = if (apiKeyVisible) MiuixIcons.Hide else MiuixIcons.Show,
                            contentDescription = "显示/隐藏 API Key",
                        )
                    }
                    if (draft.isAnthropic) {
                        Spacer(modifier = Modifier.height(12.dp))
                        FieldLabel("anthropic-version")
                        TextField(
                            value = draft.anthropicVersion,
                            onValueChange = { v -> update { it.copy(anthropicVersion = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = AiConfig.DEFAULT_ANTHROPIC_VERSION,
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                        )
                    }
                }
                if (!draft.isAnthropic) {
                    AiRowDivider(hasLeading = false)
                    AiPickerRow(
                        title = "端点模式",
                        value = AiEndpointMode.labels[draft.endpointMode] ?: draft.endpointMode,
                        summary = if (draft.endpointMode == AiEndpointMode.RESPONSES) {
                            "调用 /responses，只带 temperature 与 max_output_tokens"
                        } else {
                            "调用 /chat/completions，支持 top_p 与 system 消息"
                        },
                        onClick = { showEndpointDlg = true },
                    )
                }
                AiRowDivider(hasLeading = false)
                BasicComponent(
                    title = if (testing) "测试连接中…" else "测试连接",
                    summary = testResult ?: "读取 $baseUrl/models，并把返回的模型合并进模型列表",
                    enabled = !testing,
                    onClick = throttle {
                        val error = draft.validationError()
                        if (error != null) {
                            testResult = "校验未通过：$error"
                            return@throttle
                        }
                        testing = true
                        testResult = null
                        val config = buildDraftProvider()
                        scope.launchTry {
                            AiRuleGenerator.fetchModels(config)
                                .onSuccess { remote ->
                                    val (merged, added) = mergeAiModels(config.models, remote)
                                    if (!isNew) {
                                        AiProviders.mutate(provider.id) { it.copy(models = merged) }
                                    }
                                    testResult = if (remote.isEmpty()) {
                                        "接口未返回模型列表"
                                    } else {
                                        "连接成功：远端 ${remote.size} 个，新增 $added"
                                    }
                                }.onFailure { e ->
                                    testResult = "连接失败：${e.message}"
                                }
                            testing = false
                        }
                    },
                )
            }
        }

        item(key = "headers") {
            AiSection(title = "自定义请求头") {
                val rotation by animateFloatAsState(if (headersExpanded) 180f else 0f)
                BasicComponent(
                    title = if (draft.headers.isEmpty()) "未设置" else "已设置 ${draft.headers.size} 项",
                    summary = "会先于认证头写入，因此 Authorization / x-api-key 仍以 API Key 为准。",
                    onClick = throttle { headersExpanded = !headersExpanded },
                    endActions = {
                        PerfIcon(
                            imageVector = MiuixIcons.ExpandMore,
                            modifier = Modifier.rotate(rotation),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    },
                )
                if (headersExpanded) {
                    draft.headers.forEach { header ->
                        AiRowDivider(hasLeading = false)
                        HeaderRow(
                            header = header,
                            onNameChange = { name ->
                                update {
                                    it.copy(
                                        headers = it.headers.map { h ->
                                            if (h.key == header.key) h.copy(name = name) else h
                                        },
                                    )
                                }
                            },
                            onValueChange = { value ->
                                update {
                                    it.copy(
                                        headers = it.headers.map { h ->
                                            if (h.key == header.key) h.copy(value = value) else h
                                        },
                                    )
                                }
                            },
                            onRemove = {
                                update { it.copy(headers = it.headers.filterNot { h -> h.key == header.key }) }
                            },
                        )
                    }
                    AiRowDivider(hasLeading = false)
                    BasicComponent(
                        title = "添加请求头",
                        startAction = { AiRowIcon(imageVector = MiuixIcons.Add) },
                        endActions = { PerfIcon(imageVector = MiuixIcons.AddCircle) },
                        onClick = throttle { update { it.copy(headers = it.headers + AiHeaderDraft()) } },
                    )
                }
            }
        }

        item(key = "preferences") {
            AiSection(title = "偏好与提示词") {
                TextSwitch(
                    title = "启用此服务商",
                    subtitle = "停用后不参与快照自动生成",
                    checked = draft.enabled,
                    onCheckedChange = { v -> update { it.copy(enabled = v) } },
                )
                AiRowDivider(hasLeading = false)
                Column(modifier = Modifier.padding(16.dp)) {
                    FieldLabel("系统提示词")
                    TextField(
                        value = draft.systemPrompt,
                        onValueChange = { v -> update { it.copy(systemPrompt = v) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        label = "追加在内置提示词之前",
                        useLabelAsPlaceholder = true,
                    )
                    AiHint(
                        text = "留空则只使用内置的 gkd-rule-generator-prompt.md。",
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        item(key = "params") {
            AiSection(title = "生成参数") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LabeledField(
                        label = "Temperature",
                        value = draft.temperature,
                        onValueChange = { v -> update { it.copy(temperature = v) } },
                        placeholder = "0 ~ 2，越大输出越随机",
                        keyboardType = KeyboardType.Decimal,
                    )
                    LabeledField(
                        label = "Top P",
                        value = draft.topP,
                        onValueChange = { v -> update { it.copy(topP = v) } },
                        placeholder = "0 ~ 1，通常与 Temperature 二选一",
                        keyboardType = KeyboardType.Decimal,
                    )
                    LabeledField(
                        label = "Max Tokens",
                        value = draft.maxTokens,
                        onValueChange = { v -> update { it.copy(maxTokens = v) } },
                        placeholder = "1 ~ ${AiProviderDraft.MAX_TOKENS_LIMIT}，规则 JSON 建议 4096",
                        keyboardType = KeyboardType.Number,
                    )
                    AiHint(
                        text = "快照规则输出是结构化 JSON，Temperature 建议保持 0，可减少选择器漂移。",
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }

        item(key = "actions") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TextButton(
                    text = when {
                        testing -> "处理中…"
                        isNew -> "创建服务商"
                        else -> "保存配置"
                    },
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = throttle {
                        val error = draft.validationError()
                        if (error != null) {
                            status = "保存失败：$error"
                            return@throttle
                        }
                        val config = buildDraftProvider()
                        if (isNew) {
                            onCreated(AiProviders.add(config))
                            status = "已创建，切到「模型」页签拉取模型"
                            toast("已创建服务商 ${config.name}")
                        } else {
                            AiProviders.save(config)
                            status = if (config.enabled) {
                                "已保存"
                            } else {
                                "已保存，但该服务商处于停用状态"
                            }
                            toast("AI 配置已保存")
                        }
                    },
                )
                status?.let { message ->
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.footnote2,
                        color = if (message.startsWith("保存失败")) {
                            MiuixTheme.colorScheme.error
                        } else {
                            MiuixTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        if (!isNew) {
            item(key = "danger") {
                PreferenceGroup {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !testing) {
                                scope.launchTry {
                                    mainVm.dialogFlow.waitResult(
                                        title = "移除服务商",
                                        text = "确定移除「${provider.name.ifBlank { "未命名" }}」？它的模型列表会一并删除。",
                                        confirmText = "移除",
                                        error = true,
                                    )
                                    AiProviders.remove(provider.id)
                                    toast("已移除 ${provider.name.ifBlank { "未命名" }}")
                                    onRemoved()
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "移除服务商",
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        item(key = "bottom") {
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun HeaderRow(
    header: AiHeaderDraft,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var visible by remember(header.key) { mutableStateOf(false) }
    Row(
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = header.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = "名称，如 X-User-Agent",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            TextField(
                value = header.value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = "值",
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (visible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            )
        }
        PerfCustomIconButton(
            size = 36.dp,
            iconSize = 19.dp,
            onClickLabel = "显示或隐藏取值",
            onClick = { visible = !visible },
            imageVector = if (visible) MiuixIcons.Hide else MiuixIcons.Show,
            contentDescription = "显示/隐藏取值",
        )
        PerfCustomIconButton(
            size = 36.dp,
            iconSize = 19.dp,
            onClickLabel = "删除该请求头",
            onClick = throttle(fn = onRemove),
            imageVector = MiuixIcons.Delete,
            contentDescription = "删除",
            tint = MiuixTheme.colorScheme.error,
        )
    }
}

/** 卡片内的字段小标题，和 [LabeledField] 的排版保持一致。 */
@Composable
fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
