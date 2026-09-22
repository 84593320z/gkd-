package li.songe.gkd.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import io.ktor.client.request.HttpRequestBuilder
import li.songe.gkd.a11y.A11yContext
import li.songe.gkd.a11y.A11yRuleEngine
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.data.ActionPerformer
import li.songe.gkd.data.GkdAction
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.info2nodeList
import li.songe.gkd.store.AiConfig
import li.songe.gkd.store.AiEndpointMode
import li.songe.gkd.store.AiModel
import li.songe.gkd.store.storeFlow
import li.songe.selector.MatchOption
import li.songe.selector.Selector
import java.util.concurrent.TimeUnit

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float = 0f,
    val top_p: Float = 1f,
    val max_tokens: Int = 4096,
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: String,
)

@Serializable
data class AnthropicRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    val temperature: Float = 0f,
    val top_p: Float = 1f,
    val max_tokens: Int = 4096,
    val system: String? = null,
)

/** Responses 端点不接受 top_p，只带 temperature 与 max_output_tokens。 */
@Serializable
data class ResponsesInputMessage(
    val role: String = "user",
    val content: String,
)

@Serializable
data class ResponsesRequest(
    val model: String,
    val input: List<ResponsesInputMessage>,
    val instructions: String? = null,
    val temperature: Float = 0f,
    val max_output_tokens: Int = 4096,
)

object AiRuleGenerator {

    val isGenerating = MutableStateFlow(false)
    val pendingCount = MutableStateFlow(0)

    private val taskQueue = Channel<Long>(Channel.UNLIMITED)

    init {
        appScope.launchTry {
            for (snapshotId in taskQueue) {
                processRule(snapshotId)
            }
        }
    }

    private var cachedPrompt: String? = null

    private suspend fun loadPrompt(): String = withContext(Dispatchers.IO) {
        cachedPrompt?.let { return@withContext it }
        val text = app.assets.open("gkd-rule-generator-prompt.md").bufferedReader().readText()
        cachedPrompt = text
        text
    }

    fun fixApiUrl(url: String, protocol: String): String {
        var fixed = url.trim().trimEnd('/')
        // If URL already contains a full path, don't modify
        if (fixed.contains("/chat/completions") || fixed.contains("/messages")) {
            return fixed
        }
        // If URL already ends with /v1, keep it
        if (fixed.endsWith("/v1")) {
            return fixed
        }
        // If URL already contains /v1 somewhere, don't add it again
        if (fixed.contains("/v1/")) {
            return fixed
        }
        return "$fixed/v1"
    }

    private fun buildApiEndpoint(config: AiConfig): String {
        val baseUrl = fixApiUrl(config.apiUrl, config.protocol)
        return when {
            config.isAnthropic -> "$baseUrl/messages"
            config.endpointMode == AiEndpointMode.RESPONSES -> "$baseUrl/responses"
            else -> "$baseUrl/chat/completions"
        }
    }

    /** 自定义头先写入，认证头随后覆盖，避免用户误填 Authorization 把 Key 顶掉。 */
    private fun HttpRequestBuilder.applyAiHeaders(config: AiConfig) {
        config.headers.forEach { entry ->
            val name = entry.name.trim()
            if (name.isNotEmpty()) header(name, entry.value.trim())
        }
        when {
            config.isAnthropic -> {
                header("x-api-key", config.apiKey)
                header(
                    "anthropic-version",
                    config.anthropicVersion.ifBlank { AiConfig.DEFAULT_ANTHROPIC_VERSION },
                )
            }
            else -> header("Authorization", "Bearer ${config.apiKey}")
        }
    }

    private fun createHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(0, TimeUnit.SECONDS) // no timeout for AI response
                    writeTimeout(30, TimeUnit.SECONDS)
                }
            }
        }
    }

    private fun buildRequestBody(config: AiConfig, content: String, maxTokensOverride: Int? = null): String {
        val maxTokens = maxTokensOverride ?: config.maxTokens
        val systemPrompt = config.systemPrompt.trim().takeIf { it.isNotEmpty() }
        return when {
            config.isAnthropic -> json.encodeToString(
                AnthropicRequest(
                    model = config.model,
                    messages = listOf(AnthropicMessage("user", content)),
                    temperature = config.temperature,
                    top_p = config.topP,
                    max_tokens = maxTokens,
                    system = systemPrompt,
                )
            )
            config.endpointMode == AiEndpointMode.RESPONSES -> json.encodeToString(
                ResponsesRequest(
                    model = config.model,
                    input = listOf(ResponsesInputMessage(content = content)),
                    instructions = systemPrompt,
                    temperature = config.temperature,
                    max_output_tokens = maxTokens,
                )
            )
            else -> json.encodeToString(
                ChatRequest(
                    model = config.model,
                    messages = listOfNotNull(
                        systemPrompt?.let { ChatMessage("system", it) },
                        ChatMessage("user", content),
                    ),
                    temperature = config.temperature,
                    top_p = config.topP,
                    max_tokens = maxTokens,
                )
            )
        }
    }

    /** 只做一次 max_tokens=1 的最小对话，返回错误摘要；无错误时返回 "ok"。 */
    suspend fun testConnection(config: AiConfig): Result<String> = runCatching {
        val httpClient = createHttpClient()
        try {
            val response = httpClient.post(buildApiEndpoint(config)) {
                applyAiHeaders(config)
                contentType(ContentType.Application.Json)
                setBody(buildRequestBody(config, "hi", maxTokensOverride = 1))
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw Exception("HTTP ${response.status.value} ${errorHint(body)}".trim())
            }
            errorHint(body).takeIf { it.isNotEmpty() }?.let { throw Exception(it) }
            "ok"
        } finally {
            httpClient.close()
        }
    }

    private fun errorHint(body: String): String {
        val error = runCatching { json.parseToJsonElement(body).jsonObject["error"] }.getOrNull()
            ?: return ""
        val message = runCatching { error.jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
            ?: runCatching { error.jsonPrimitive.content }.getOrNull()
            ?: return ""
        return message.trim().take(200)
    }

    /** 拉取远端模型：各家字段名不统一，能解析出上下文长度与推理能力就一并记录。 */
    suspend fun fetchModels(config: AiConfig): Result<List<AiModel>> = runCatching {
        val baseUrl = fixApiUrl(config.apiUrl, config.protocol)
        val httpClient = createHttpClient()
        try {
            val response = httpClient.get("$baseUrl/models") {
                applyAiHeaders(config)
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw Exception("HTTP ${response.status.value} ${errorHint(body)}".trim())
            }
            val data = json.parseToJsonElement(body).jsonObject["data"]
                ?: throw Exception("接口未返回 data 字段")
            data.jsonArray.mapNotNull { element ->
                runCatching {
                    val item = element.jsonObject
                    val id = item["id"]?.jsonPrimitive?.content ?: return@runCatching null
                    val label = (item["display_name"] ?: item["name"])?.jsonPrimitive?.content
                    val capabilities = item["capabilities"].objectOrNull()
                    val reasoning = item["supports_reasoning"].boolOrNullValue()
                        ?: item["supported_parameters"].jsonArrayOrNull
                            ?.any { it.jsonPrimitive.content == "reasoning_effort" }
                        ?: capabilities?.get("supports_reasoning").boolOrNullValue()
                    AiModel(
                        modelId = id,
                        displayName = label?.ifBlank { null } ?: id,
                        contextWindow = item.intOf("context_length")
                            ?: item["context_window"].intOrNullValue()
                            ?: item["context_window"].objectOrNull()?.intOf("max_input_tokens")
                            ?: capabilities?.intOf("max_input_tokens")
                            ?: 0,
                        reasoning = reasoning,
                    )
                }.getOrNull()
            }
        } finally {
            httpClient.close()
        }
    }

    suspend fun generateRule(snapshotId: Long) {
        val config = storeFlow.value.activeAiProvider()
        if (config == null || !config.usable) {
            toast("请先在 AI 设置中选择并配置服务商")
            return
        }
        pendingCount.value++
        taskQueue.send(snapshotId)
        if (isGenerating.value) {
            toast("AI 任务已加入队列（排队中：${pendingCount.value}）")
        }
    }

    private suspend fun processRule(snapshotId: Long) {
        isGenerating.value = true
        try {
            val config = storeFlow.value.activeAiProvider() ?: error("请先配置 AI 服务商")
            toast("AI 正在生成规则...", forced = true)
            val prompt = loadPrompt()
            val snapshotJson = withContext(Dispatchers.IO) {
                SnapshotExt.snapshotFile(snapshotId).readText()
            }
            val userContent = "$prompt\n$snapshotJson"
            LogUtils.d("AI generateRule: prompt length=${prompt.length}, snapshot length=${snapshotJson.length}, total=${userContent.length}")

            val result = callAiApi(config, userContent)
            LogUtils.d("AI extracted content (first 2000 chars): ${result.take(2000)}")
            val ruleText = extractContent(result)
            LogUtils.d("AI after extractContent (first 2000 chars): ${ruleText.take(2000)}")

            val currentRule = parseAndValidateRule(ruleText)
            if (currentRule == null) {
                toast("AI 生成的规则 JSON 不合法，重试中...")
                val retryResult = callAiApi(config, userContent)
                val retryText = extractContent(retryResult)
                val retryParsed = parseAndValidateRule(retryText)
                if (retryParsed == null) {
                    toast("AI 生成规则失败：JSON 解析错误")
                    return
                }
                insertRuleToSubscription(retryParsed)
            } else {
                insertRuleToSubscription(currentRule)
            }
            toast("AI 规则生成成功并已添加到本地规则")
        } catch (e: Exception) {
            toast("AI 生成规则失败：${e.message}")
            LogUtils.d("AI rule generation failed", e)
        } finally {
            pendingCount.value--
            isGenerating.value = pendingCount.value > 0
        }
    }

    /**
     * 加强模式：双击快照按钮触发
     * 1. 捕获快照 → AI 生成规则
     * 2. 执行规则点击 → 等待界面变化 → 再次捕获节点树
     * 3. 对比两次节点树判断规则是否生效
     * 4. 未生效则将失败规则加入 prompt，让 AI 重新生成
     */
    suspend fun enhancedGenerate() {
        val config = storeFlow.value.activeAiProvider()
        if (!storeFlow.value.aiEnable) {
            toast("请先启用 AI 规则")
            return
        }
        if (config == null || !config.usable) {
            toast("请先在 AI 设置中选择并配置服务商")
            return
        }
        if (isGenerating.value) {
            toast("AI 正在生成中，请稍后再试")
            return
        }
        isGenerating.value = true
        try {
            // Step 1: 捕获快照
            //toast("加强模式：正在捕获快照...", forced = true)
            val snapshot = SnapshotExt.captureSnapshot(forAiRule = false)
            val snapshotJson = withContext(Dispatchers.IO) {
                SnapshotExt.snapshotFile(snapshot.id).readText()
            }

            // Step 2: AI 生成规则
            toast("加强模式：AI 正在生成规则...\n 请勿离开当前界面", forced = true)
            val prompt = loadPrompt()
            val userContent = "$prompt\n$snapshotJson"
            val result = callAiApi(config, userContent)
            val ruleText = extractContent(result)
            var currentRule = parseAndValidateRule(ruleText) ?: run {
                toast("AI 生成规则失败：JSON 解析错误")
                return
            }

            // Step 3: 尝试执行规则并验证
            var lastRuleText = ruleText
            val maxRetries = 2
            for (attempt in 0 until maxRetries) {
                val actions = extractActions(currentRule)
                if (actions.isEmpty()) {
                    toast("加强模式：未找到有效选择器")
                    insertRuleToSubscription(currentRule)
                    return
                }

                // 获取当前节点树，尝试匹配并执行
                val engine = A11yRuleEngine.instance
                if (engine == null) {
                    toast("无障碍服务不可用")
                    insertRuleToSubscription(currentRule)
                    return
                }

                val rootNode = engine.safeActiveWindow
                if (rootNode == null) {
                    toast("无法获取当前界面节点")
                    insertRuleToSubscription(currentRule)
                    return
                }

                // 记录执行前的节点树特征
                val beforeNodes = info2nodeList(rootNode)
                val beforeSignature = snapshotSignature(beforeNodes)

                // 尝试用生成规则匹配并执行动作
                var actionExecuted = false
                val a11yContext = A11yContext(engine, interruptable = false)
                for (action in actions) {
                    val selector = Selector.parseOrNull(action.selector) ?: continue
                    val targetNode = a11yContext.querySelfOrSelector(
                        rootNode, selector, MatchOption(fastQuery = action.fastQuery)
                    )
                    if (targetNode != null) {
                        val actionResult = ActionPerformer
                            .getAction(action.action ?: ActionPerformer.Click.action)
                            .perform(targetNode, action)
                        if (actionResult.result) {
                            actionExecuted = true
                            LogUtils.d("加强模式：执行动作成功, action=${action.action}, selector=${action.selector}")
                            break
                        }
                    }
                }

                if (!actionExecuted) {
                    LogUtils.d("加强模式：选择器未匹配到任何节点，attempt=$attempt")
                    if (attempt == maxRetries - 1) {
                        toast("加强模式：规则未能匹配节点，已保存供手动调整")
                        insertRuleToSubscription(currentRule)
                        return
                    }
                    // 未匹配到，重新生成
                    toast("加强模式：规则未匹配，正在重新生成...")
                    val retryContent = buildRetryPrompt(prompt, snapshotJson, lastRuleText, "选择器未匹配到任何节点")
                    val retryResult = callAiApi(config, retryContent)
                    val retryRuleText = extractContent(retryResult)
                    currentRule = parseAndValidateRule(retryRuleText) ?: run {
                        toast("加强模式：重试失败")
                        return
                    }
                    lastRuleText = retryRuleText
                    continue
                }

                // Step 4: 等待界面变化
                delay(1500)

                // 再次捕获节点树（不保存）
                val afterRootNode = engine.safeActiveWindow
                if (afterRootNode == null) {
                    // 界面消失，规则很可能生效了（弹窗关闭）
                    toast("加强模式：规则验证成功（界面已变化）")
                    insertRuleToSubscription(currentRule)
                    return
                }
                val afterNodes = info2nodeList(afterRootNode)
                val afterSignature = snapshotSignature(afterNodes)

                // Step 5: 对比判断
                if (beforeSignature != afterSignature) {
                    // 界面发生变化，规则生效
                    toast("加强模式：规则验证成功！")
                    insertRuleToSubscription(currentRule)
                    return
                }

                // 界面未变化，规则可能未生效
                if (attempt < maxRetries - 1) {
                    toast("加强模式：规则未生效，正在重新生成...")
                    val retryContent = buildRetryPrompt(prompt, snapshotJson, lastRuleText, "点击执行后界面未发生变化，规则可能不正确")
                    val retryResult = callAiApi(config, retryContent)
                    val retryRuleText = extractContent(retryResult)
                    currentRule = parseAndValidateRule(retryRuleText) ?: run {
                        toast("加强模式：重试解析失败，保存当前规则")
                        return
                    }
                    lastRuleText = retryRuleText
                } else {
                    toast("加强模式：多次尝试后规则仍未生效，已保存最新版本")
                    insertRuleToSubscription(currentRule)
                }
            }
        } catch (e: Exception) {
            toast("加强模式失败：${e.message}")
            LogUtils.d("Enhanced generate failed", e)
        } finally {
            isGenerating.value = false
        }
    }

    private fun extractActions(subs: RawSubscription): List<GkdAction> {
        return subs.apps.flatMap { app ->
            app.groups.flatMap { group ->
                group.rules.flatMap { rule ->
                    rule.matches.orEmpty().map { selector ->
                        GkdAction(
                            selector = selector,
                            fastQuery = rule.fastQuery ?: group.fastQuery ?: false,
                            action = rule.action,
                            position = rule.position,
                            swipeArg = rule.swipeArg,
                        )
                    }
                }
            }
        }
    }

    private fun snapshotSignature(nodes: List<li.songe.gkd.data.NodeInfo>): String {
        // 用节点数量 + 可见节点的 text/desc 拼接作为简单签名
        val visibleTexts = nodes.mapNotNull { node ->
            val text = node.attr.text ?: node.attr.desc
            if (node.attr.visibleToUser && text != null) text else null
        }.sorted()
        return "${nodes.size}:${visibleTexts.hashCode()}"
    }

    private fun buildRetryPrompt(
        originalPrompt: String,
        snapshotJson: String,
        previousRule: String,
        failureReason: String,
    ): String {
        return """$originalPrompt

$snapshotJson

---
注意：上一次生成的规则未能生效。
失败原因：$failureReason
上次生成的规则（请勿再使用相同的选择器）：
$previousRule

请分析失败原因，重新选择更准确的目标节点和选择器。"""
    }
    private suspend fun callAiApi(config: AiConfig, userContent: String): String {
        val endpoint = buildApiEndpoint(config)
        val requestBody = buildRequestBody(config, userContent)
        LogUtils.d("AI Request endpoint: $endpoint")
        LogUtils.d("AI Request body (first 2000 chars): ${requestBody.take(2000)}")
        val httpClient = createHttpClient()
        try {
            val response = httpClient.post(endpoint) {
                applyAiHeaders(config)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val body = response.bodyAsText()
            LogUtils.d("AI Response (first 3000 chars): ${body.take(3000)}")
            if (!response.status.isSuccess()) {
                throw Exception("HTTP ${response.status.value} ${errorHint(body)}".trim())
            }
            val jsonElement = json.parseToJsonElement(body)

            return when {
                config.isAnthropic -> {
                    val contentArr = jsonElement.jsonObject["content"]
                        ?: throw Exception(errorHint(body).ifBlank { "Anthropic 响应缺少 content" })
                    contentArr.jsonArray.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                        ?: throw Exception("No text in Anthropic content")
                }
                config.endpointMode == AiEndpointMode.RESPONSES -> {
                    // Responses 的正文在 output[] 的 message 项里，逐段拼 output_text
                    val text = jsonElement.jsonObject["output"].jsonArrayOrNull
                        ?.flatMap { item ->
                            val msg = item.objectOrNull() ?: return@flatMap emptyList()
                            msg["content"].jsonArrayOrNull.orEmpty().mapNotNull { part ->
                                val obj = part.objectOrNull() ?: return@mapNotNull null
                                obj["output_text"]?.jsonPrimitive?.content
                                    ?: obj["text"]?.jsonPrimitive?.content
                                        .takeIf { obj["type"]?.jsonPrimitive?.content == "text" }
                            }
                        }?.joinToString("\n").orEmpty()
                    text.ifBlank { errorHint(body) }.takeIf { it.isNotBlank() }
                        ?: throw Exception("Responses 响应中没有文本内容")
                }
                else -> {
                    val choices = jsonElement.jsonObject["choices"]
                        ?: throw Exception("No choices in OpenAI response")
                    choices.jsonArray.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
                        ?: throw Exception("No content in OpenAI response")
                }
            }
        } finally {
            httpClient.close()
        }
    }

    private fun extractContent(text: String): String {
        var content = text.trim()
        // Try to extract JSON from markdown code block anywhere in the text
        val codeBlockRegex = Regex("```(?:json5?)?\\s*\\n?([\\s\\S]*?)\\n?```")
        val match = codeBlockRegex.find(content)
        if (match != null) {
            content = match.groupValues[1].trim()
        } else {
            // Remove markdown code block markers at start/end
            if (content.startsWith("```json5") || content.startsWith("```json") || content.startsWith("```")) {
                content = content.removePrefix("```json5").removePrefix("```json").removePrefix("```")
            }
            if (content.endsWith("```")) {
                content = content.removeSuffix("```")
            }
            content = content.trim()
        }
        // If content doesn't start with '{', try to find the first '{' and last '}'
        if (!content.startsWith("{")) {
            val firstBrace = content.indexOf('{')
            val lastBrace = content.lastIndexOf('}')
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                content = content.substring(firstBrace, lastBrace + 1)
            }
        }
        return content
    }

    private fun parseAndValidateRule(text: String): RawSubscription? {
        // First try parsing as full subscription
        try {
            return RawSubscription.parse(text, json5 = true)
        } catch (_: Exception) {}
        try {
            return RawSubscription.parse(text, json5 = false)
        } catch (_: Exception) {}

        // AI typically outputs an App-level JSON (id=packageName, groups=[...])
        // Wrap it into a subscription structure
        try {
            val jsonElement = json.parseToJsonElement(text)
            val app = RawSubscription.parseApp(jsonElement.jsonObject)
            LogUtils.d("AI currentRule as RawApp: id=${app.id}, groups=${app.groups.size}")
            // Build a minimal subscription containing this app
            return RawSubscription(
                id = -1L,
                name = "AI Generated",
                version = 1,
                apps = listOf(app),
            )
        } catch (e: Exception) {
            LogUtils.d("AI parse as app failed: ${e.message}")
            LogUtils.d("AI failed text (first 500 chars): ${text.take(500)}")
            return null
        }
    }

    private fun insertRuleToSubscription(newSubs: RawSubscription) {
        val currentSubs = subsMapFlow.value[LOCAL_SUBS_ID] ?: return
        val newApp = newSubs.apps.firstOrNull() ?: return
        val existingApp = currentSubs.apps.find { it.id == newApp.id }

        // Reassign group keys to avoid conflicts with existing groups
        val existingKeys = existingApp?.groups?.map { it.key }?.toSet() ?: emptySet()
        var nextKey = (existingKeys.maxOrNull() ?: -1) + 1
        val fixedGroups = newApp.groups.map { group ->
            val newKey = nextKey++
            group.copy(key = newKey)
        }
        val fixedApp = newApp.copy(groups = fixedGroups)

        val updatedSubs = if (existingApp != null) {
            val updatedApp = existingApp.copy(
                groups = existingApp.groups + fixedGroups
            )
            currentSubs.copy(
                apps = currentSubs.apps.map { if (it.id == updatedApp.id) updatedApp else it }
            )
        } else {
            currentSubs.copy(
                apps = (currentSubs.apps + fixedApp).filterIfNotAll { it.groups.isNotEmpty() }
            )
        }
        updateSubscription(updatedSubs)
    }
}

private val JsonElement?.jsonArrayOrNull: JsonArray? get() = this as? JsonArray

private fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.intOrNullValue(): Int? = (this as? JsonPrimitive)?.intOrNull

private fun JsonElement?.boolOrNullValue(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.intOf(key: String): Int? = this[key].intOrNullValue()
