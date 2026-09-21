package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import li.songe.gkd.store.AiConfig

/** AI 设置编辑草稿：数字以文本保存，避免输入中途（如 "0."）被丢弃。 */
data class AiConfigDraft(
    val protocol: String = "openai",
    val apiUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: String = "0",
    val topP: String = "1",
    val maxTokens: String = "4096",
) {
    val paramSummary: String
        get() = "Temperature $temperature · Top P $topP · Max Tokens $maxTokens"

    fun toConfig(base: AiConfig): AiConfig? {
        val temp = temperature.toFloatOrNull() ?: return null
        val sampling = topP.toFloatOrNull() ?: return null
        val tokens = maxTokens.toIntOrNull() ?: return null
        if (temp !in 0f..2f || sampling !in 0f..1f || tokens !in 1..MAX_TOKENS_LIMIT) return null
        return base.copy(
            protocol = protocol,
            apiUrl = apiUrl.trim().trimEnd('/'),
            apiKey = apiKey.trim(),
            model = model.trim(),
            temperature = temp,
            topP = sampling,
            maxTokens = tokens,
        )
    }

    companion object {
        const val MAX_TOKENS_LIMIT = 128000

        fun of(config: AiConfig) = AiConfigDraft(
            protocol = config.protocol,
            apiUrl = config.apiUrl,
            apiKey = config.apiKey,
            model = config.model,
            temperature = trimFloat(config.temperature),
            topP = trimFloat(config.topP),
            maxTokens = config.maxTokens.toString(),
        )

        private fun trimFloat(value: Float) =
            if (value % 1f == 0f) value.toInt().toString() else value.toString()
    }
}

/** 二级页与三级页共用同一份草稿：进入二级页时载入，保存或返回时结束编辑。 */
class AiConfigVm : ViewModel() {
    val draftFlow = MutableStateFlow(AiConfigDraft())
    val editingFlow = MutableStateFlow(false)
    val modelListFlow = MutableStateFlow<List<String>>(emptyList())
    val fetchingModelFlow = MutableStateFlow(false)
    val showModelDlgFlow = MutableStateFlow(false)
    val showProtocolDlgFlow = MutableStateFlow(false)
    val testingFlow = MutableStateFlow(false)
    val testResultFlow = MutableStateFlow<String?>(null)

    fun load(saved: AiConfig) {
        if (editingFlow.value) return
        draftFlow.value = AiConfigDraft.of(saved)
        editingFlow.value = true
    }

    fun update(transform: (AiConfigDraft) -> AiConfigDraft) {
        draftFlow.update(transform)
    }

    fun resetParams() = update {
        it.copy(temperature = "0", topP = "1", maxTokens = "4096")
    }

    fun finishEditing() {
        editingFlow.value = false
    }
}
