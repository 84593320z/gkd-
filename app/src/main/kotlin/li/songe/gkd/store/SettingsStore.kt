package li.songe.gkd.store

import kotlinx.serialization.Serializable
import li.songe.gkd.META
import li.songe.gkd.notif.ActionTipNotif
import li.songe.gkd.util.ActionTipStyleOption
import li.songe.gkd.util.AppGroupOption
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.AutomatorModeOption
import li.songe.gkd.util.findOption
import li.songe.gkd.util.RuleSortOption
import li.songe.gkd.util.UpdateChannelOption
import li.songe.gkd.util.UpdateTimeOption

/** 服务商下的一个模型条目：modelId 是发给接口的名字，其余字段只用于界面与选模型参考。 */
@Serializable
data class AiModel(
    val modelId: String,
    val displayName: String = modelId,
    /** 上下文长度，0 表示接口未给出且用户未填 */
    val contextWindow: Int = 0,
    /** 是否支持思考/推理，null 表示未知 */
    val reasoning: Boolean? = null,
)

@Serializable
data class AiHeader(
    val name: String,
    val value: String,
)

/** OpenAI 兼容接口的两种端点：/chat/completions 与 /responses */
object AiEndpointMode {
    const val CHAT = "chat"
    const val RESPONSES = "responses"

    val labels = mapOf(
        CHAT to "Chat Completions API",
        RESPONSES to "Responses API",
    )
}

/** 一个服务商连接：接口协议 + 地址 + 密钥 + 模型池，[SettingsStore.aiProviders] 可存多份 */
@Serializable
data class AiConfig(
    val id: String = "",
    val name: String = "",
    val protocol: String = "openai",
    val endpointMode: String = AiEndpointMode.CHAT,
    val apiUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val models: List<AiModel> = emptyList(),
    val headers: List<AiHeader> = emptyList(),
    val anthropicVersion: String = DEFAULT_ANTHROPIC_VERSION,
    /** 留空则使用内置的 gkd-rule-generator-prompt.md */
    val systemPrompt: String = "",
    val enabled: Boolean = true,
    val temperature: Float = 0f,
    val topP: Float = 1f,
    val maxTokens: Int = 4096,
) {
    val isAnthropic get() = protocol == "anthropic"

    val usable: Boolean
        get() = apiUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    fun currentModel(): AiModel? = models.firstOrNull { it.modelId == model }

    companion object {
        const val DEFAULT_ANTHROPIC_VERSION = "2023-06-01"
    }
}

@Serializable
data class SettingsStore(
    val enableAutomator: Boolean = false,
    val automatorMode: Int = AutomatorModeOption.A11yMode.value,
    val enableShizuku: Boolean = false,
    val enableMatch: Boolean = true,
    val enableStatusService: Boolean = false,
    val excludeFromRecents: Boolean = false,
    val captureScreenshot: Boolean = false,
    val screenshotTargetAppId: String = "",
    val screenshotEventSelector: String = "",
    val httpServerPort: Int = 8888,
    val updateSubsInterval: Long = UpdateTimeOption.Everyday.value,
    val captureVolumeChange: Boolean = false,
    val toastWhenClick: Boolean = true,
    val actionToast: String = META.appName,
    val autoClearMemorySubs: Boolean = false,
    val hideSnapshotStatusBar: Boolean = false,
    val enableDarkTheme: Boolean? = null,
    val enableDynamicColor: Boolean = false,
    /** MIUIX：顶栏/底栏模糊（需 RuntimeShader） */
    val enableMiuixBlur: Boolean = true,
    /** MIUIX：悬浮底栏（类 Apple / FloatingNavigationBar） */
    val useFloatingNavBar: Boolean = true,
    /** MIUIX：悬浮底栏液态玻璃高光（依赖模糊） */
    val enableLiquidGlass: Boolean = true,
    /** 系统预测式返回手势（Android 13+，切换后需重建 Activity） */
    val enablePredictiveBack: Boolean = false,
    val showSaveSnapshotToast: Boolean = true,
    /** @deprecated 由 [actionTipStyle] 接管；读取时见 resolve */
    val useSystemToast: Boolean = false,
    /** 触发提示样式，见 [li.songe.gkd.util.ActionTipStyleOption] */
    val actionTipStyle: Int = 0,
    /** 实时通知自动消失时间（秒），见 [li.songe.gkd.notif.ActionTipNotif] 范围 */
    val actionTipLiveDurationSec: Int = ActionTipNotif.DEFAULT_DURATION_SEC,
    val useCustomNotifText: Boolean = false,
    val customNotifTitle: String = META.appName,
    val customNotifText: String = $$"${i}全局/${k}应用/${u}规则/${n}触发",
    val updateChannel: Int = if (META.isBeta) UpdateChannelOption.Beta.value else UpdateChannelOption.Stable.value,
    val appSort: Int = AppSortOption.ByUsedTime.value,
    val showBlockApp: Boolean = true,
    val appRuleSort: Int = RuleSortOption.ByDefault.value,
    val subsAppSort: Int = AppSortOption.ByUsedTime.value,
    val subsCategorySort: Int = AppSortOption.ByUsedTime.value,
    val subsAppShowUninstall: Boolean = false,
    val subsAppGroupType: Int = AppGroupOption.UserGroup.value or AppGroupOption.SystemGroup.value,
    val subsCategoryGroupType: Int = AppGroupOption.UserGroup.value or AppGroupOption.SystemGroup.value,
    val subsAppShowBlock: Boolean = false,
    val subsCategoryShowBlock: Boolean = false,
    val subsExcludeSort: Int = AppSortOption.ByUsedTime.value,
    val subsExcludeShowBlockApp: Boolean = true,
    val subsExcludeShowInnerDisabledApp: Boolean = true,
    val subsPowerWarn: Boolean = true,
    val enableBlockA11yAppList: Boolean = false,
    val blockA11yAppListFollowMatch: Boolean = true,
    val a11yAppSort: Int = AppSortOption.ByUsedTime.value,
    val a11yScopeAppSort: Int = AppSortOption.ByUsedTime.value,
    val appGroupType: Int = (1 shl AppGroupOption.normalObjects.size) - 1,
    val a11yAppGroupType: Int = appGroupType,
    val a11yScopeAppGroupType: Int = appGroupType,
    val subsExcludeAppGroupType: Int = appGroupType,
    val showDisabledRule: Boolean = true,
    val aiEnable: Boolean = false,
    /** @deprecated 旧版单份配置，首次加载时被 [migrateAiProviders] 展开进 [aiProviders] */
    val aiConfig: AiConfig? = null,
    val aiProviders: List<AiConfig> = emptyList(),
    /** 界面中选定的服务商；为空时回落到第一个已启用的 */
    val aiActiveProviderId: String = "",
) {
    val useA11y get() = automatorMode == AutomatorModeOption.A11yMode.value
    val useAutomation get() = automatorMode == AutomatorModeOption.AutomationMode.value

    /** 兼容旧版 [useSystemToast]：未改过 [actionTipStyle] 时沿用 Toast 开关 */
    fun resolveActionTipStyle(): ActionTipStyleOption {
        if (actionTipStyle != ActionTipStyleOption.Overlay.value) {
            return ActionTipStyleOption.objects.findOption(actionTipStyle)
        }
        return if (useSystemToast) {
            ActionTipStyleOption.SystemToast
        } else {
            ActionTipStyleOption.Overlay
        }
    }

    fun resolveActionTipLiveDurationSec(): Int =
        actionTipLiveDurationSec.coerceIn(
            ActionTipNotif.MIN_DURATION_SEC,
            ActionTipNotif.MAX_DURATION_SEC,
        )

    val actionTipLiveDurationMs: Long
        get() = resolveActionTipLiveDurationSec() * 1000L

    /** 实际用于生成的服务商：先认界面上选中的，再回落到第一个启用的 */
    fun activeAiProvider(): AiConfig? =
        aiProviders.firstOrNull { it.id == aiActiveProviderId && it.enabled }
            ?: aiProviders.firstOrNull { it.enabled }
}

fun newAiProviderId(): String = java.util.UUID.randomUUID().toString()

/** 旧版只有一份 aiConfig：首次加载时展开成一个服务商，之后不再读取旧字段。 */
fun SettingsStore.migrateAiProviders(): SettingsStore {
    if (aiProviders.isNotEmpty()) return this
    val legacy = aiConfig ?: return this
    if (legacy.apiUrl.isBlank() && legacy.apiKey.isBlank() && legacy.model.isBlank()) {
        return copy(aiConfig = null)
    }
    val provider = legacy.copy(
        id = newAiProviderId(),
        name = legacy.name.ifBlank { legacy.model.ifBlank { "默认服务商" } },
        model = legacy.model.trim(),
        models = legacy.model.trim().takeIf { it.isNotEmpty() }?.let { listOf(AiModel(it)) }.orEmpty(),
    )
    return copy(
        aiConfig = null,
        aiProviders = listOf(provider),
        aiActiveProviderId = provider.id,
    )
}