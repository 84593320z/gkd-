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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.ui.component.AppPageScaffold
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PreferenceGroup
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.util.throttle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Serializable
data object AiHelpPageRoute : NavKey

private val HelpParagraphs = listOf(
    "开启流程" to listOf(
        "1. 在「高级设置 - 快照」打开「AI 规则」开关；若还没有可用的服务商，会自动跳到「AI 服务商」页让你新建。",
        "2. 在服务商详情里填好名称、Base URL、API Key，切到「模型」页签拉取或添加模型，并把其中一个设为当前模型。",
        "3. 配好多个服务商时，用列表页的勾选框切换当前生效的那个，快照会按当前服务商生成规则并写入本地订阅。",
        "4. 广告弹窗出现时按快照快捷键保存快照，生成结果可在「快照记录」里查看，规则会出现在本地订阅的对应应用分组下。",
    ),
    "加强模式" to listOf(
        "双击快照按钮进入加强模式：生成规则后立即执行选择器，若界面没有变化则让模型自动重写，直到命中为止。",
        "加强模式会多次调用接口，Token 消耗高于普通模式，建议只在复杂弹窗上使用。",
    ),
    "模型选择" to listOf(
        "优先选择支持视觉输入的轻量模型（如各家 flash / mini / haiku 档位），响应快且成本低。",
        "Temperature 与 Top P 建议保持默认低值，可减少选择器漂移；Max Tokens 建议 4096 以上。",
    ),
    "隐私提示" to listOf(
        "快照图片会发送到你所配置的接口地址，请先确认该地址属于自己或可信服务。",
        "日志与分享链接中的 API Key 会被自动脱敏，但仍建议不要公开完整日志。",
    ),
)

@Composable
fun AiHelpPage() {
    val mainVm = LocalMainViewModel.current

    AppPageScaffold(
        title = "使用说明",
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
            HelpParagraphs.forEach { (section, lines) ->
                PreferenceGroup(title = section) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = section,
                            style = MiuixTheme.textStyles.main,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        lines.forEach { line ->
                            Text(
                                text = line,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
