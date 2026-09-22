package li.songe.gkd.ui

import kotlinx.coroutines.flow.update
import li.songe.gkd.store.AiConfig
import li.songe.gkd.store.AiEndpointMode
import li.songe.gkd.store.AiHeader
import li.songe.gkd.store.AiModel
import li.songe.gkd.store.newAiProviderId
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.AiProtocolOption
import li.songe.gkd.util.findOption
import java.net.URI
import java.util.UUID

/** 列表页角标用的协议简述。 */
val AiConfig.protocolLabel: String
    get() = AiProtocolOption.objects.findOption(protocol).label +
            if (isAnthropic) "" else " · ${AiEndpointMode.labels[endpointMode] ?: endpointMode}"

/** 编辑中的一行自定义请求头。 */
data class AiHeaderDraft(
    val key: String = UUID.randomUUID().toString(),
    val name: String = "",
    val value: String = "",
)

/**
 * 服务商编辑草稿：数字以文本保存，避免输入中途（如 "0."）被丢弃；
 * 模型列表不进草稿，模型页直接写回 store。
 */
data class AiProviderDraft(
    val id: String = "",
    val name: String = "",
    val protocol: String = "openai",
    val endpointMode: String = AiEndpointMode.CHAT,
    val apiUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val anthropicVersion: String = AiConfig.DEFAULT_ANTHROPIC_VERSION,
    val systemPrompt: String = "",
    val enabled: Boolean = true,
    val temperature: String = "0",
    val topP: String = "1",
    val maxTokens: String = "4096",
    val headers: List<AiHeaderDraft> = emptyList(),
) {
    val isAnthropic get() = protocol == "anthropic"

    val paramSummary: String
        get() = "Temperature $temperature · Top P $topP · Max Tokens $maxTokens"

    /** 返回 null 表示合法；否则是给用户的中文提示。 */
    fun validationError(): String? {
        if (name.isBlank()) return "名称不能为空"
        val uri = runCatching { URI(apiUrl.trim()) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            return "API 地址必须是合法的 http/https 地址"
        }
        headers.forEach { header ->
            val name = header.name.trim()
            if (name.isNotEmpty() && header.value.isBlank()) return "请求头 $name 缺少值"
            if (name.isEmpty() && header.value.isNotEmpty()) return "请求头缺少名称"
        }
        val temp = temperature.toFloatOrNull() ?: return "Temperature 需为 0~2 的数字"
        val sampling = topP.toFloatOrNull() ?: return "Top P 需为 0~1 的数字"
        val tokens = maxTokens.toIntOrNull() ?: return "Max Tokens 需为整数"
        if (temp !in 0f..2f || sampling !in 0f..1f || tokens !in 1..MAX_TOKENS_LIMIT) {
            return "生成参数超出范围：Temperature 0~2、Top P 0~1、Max Tokens 1~$MAX_TOKENS_LIMIT"
        }
        return null
    }

    /** 以 [base] 为底写回草稿：保留 id 与模型列表，只有配置字段被覆盖。 */
    fun toProvider(base: AiConfig): AiConfig {
        val kept = base.headers.filter { draftHeader ->
            headers.none { it.name.trim() == draftHeader.name }
        }
        return base.copy(
            name = name.trim(),
            protocol = protocol,
            endpointMode = if (isAnthropic) AiEndpointMode.CHAT else endpointMode,
            apiUrl = apiUrl.trim().trimEnd('/'),
            apiKey = apiKey.trim(),
            model = model.trim(),
            anthropicVersion = anthropicVersion.trim().ifBlank { AiConfig.DEFAULT_ANTHROPIC_VERSION },
            systemPrompt = systemPrompt.trim(),
            enabled = enabled,
            temperature = temperature.toFloat(),
            topP = topP.toFloat(),
            maxTokens = maxTokens.toInt(),
            headers = kept + headers
                .filter { it.name.isNotBlank() }
                .map { AiHeader(it.name.trim(), it.value.trim()) },
        )
    }

    companion object {
        const val MAX_TOKENS_LIMIT = 128000

        fun of(config: AiConfig) = AiProviderDraft(
            id = config.id,
            name = config.name,
            protocol = config.protocol,
            endpointMode = config.endpointMode,
            apiUrl = config.apiUrl,
            apiKey = config.apiKey,
            model = config.model,
            anthropicVersion = config.anthropicVersion,
            systemPrompt = config.systemPrompt,
            enabled = config.enabled,
            temperature = trimFloat(config.temperature),
            topP = trimFloat(config.topP),
            maxTokens = config.maxTokens.toString(),
            headers = config.headers.map { AiHeaderDraft(name = it.name, value = it.value) },
        )

        fun new(protocol: String) = AiProviderDraft(
            protocol = protocol,
            name = if (protocol == "anthropic") "Anthropic" else "OpenAI 兼容",
            apiUrl = if (protocol == "anthropic") "https://api.anthropic.com" else "",
        )

        private fun trimFloat(value: Float) =
            if (value % 1f == 0f) value.toInt().toString() else value.toString()
    }
}

/** 服务商列表的唯一写入口。 */
object AiProviders {
    fun add(config: AiConfig): String {
        val id = config.id.ifBlank { newAiProviderId() }
        storeFlow.update { store ->
            val provider = config.copy(id = id)
            store.copy(
                aiProviders = store.aiProviders + provider,
                aiActiveProviderId = store.aiActiveProviderId.ifBlank { id },
            )
        }
        return id
    }

    fun save(config: AiConfig) {
        storeFlow.update { store ->
            store.copy(
                aiProviders = store.aiProviders.map { if (it.id == config.id) config else it },
                aiActiveProviderId = store.aiActiveProviderId.ifBlank {
                    config.id.takeIf { config.enabled }.orEmpty()
                },
            )
        }
    }

    fun remove(id: String) {
        storeFlow.update { store ->
            val rest = store.aiProviders.filterNot { it.id == id }
            store.copy(
                aiProviders = rest,
                aiActiveProviderId = store.aiActiveProviderId.takeIf { active ->
                    rest.any { it.id == active && it.enabled }
                } ?: rest.firstOrNull { it.enabled }?.id.orEmpty(),
            )
        }
    }

    fun setActive(id: String) {
        storeFlow.update { it.copy(aiActiveProviderId = id) }
    }

    fun mutate(id: String, transform: (AiConfig) -> AiConfig) {
        storeFlow.update { store ->
            store.copy(
                aiProviders = store.aiProviders.map { if (it.id == id) transform(it) else it },
            )
        }
    }
}

/** 远端拉回来的模型按 id 合并，手动添加的模型不被覆盖掉。 */
fun mergeAiModels(current: List<AiModel>, remote: List<AiModel>): Pair<List<AiModel>, Int> {
    var added = 0
    val merged = current.toMutableList()
    remote.forEach { model ->
        val index = merged.indexOfFirst { it.modelId == model.modelId }
        if (index >= 0) {
            if (merged[index].displayName == merged[index].modelId) {
                merged[index] = model
            }
        } else {
            merged.add(model)
            added++
        }
    }
    return merged to added
}
