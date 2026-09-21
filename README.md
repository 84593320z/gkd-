# GKD-X 融合版

> ⚠️ `main` 只是建仓时自动生成的占位分支，仓库代码全部在 **`miuix-ai`** 分支上。
> 完整介绍（界面截图、改动列表、开源致谢、订阅与反馈）请看 **[miuix-ai/README.md](https://github.com/84593320z/gkd-/blob/miuix-ai/README.md)**。
> 想让它直接显示在仓库首页，把默认分支切过去：Settings → General → Default branch → `miuix-ai`。

基于 [GKD](https://github.com/gkd-kit/gkd) 的 Android 自定义屏幕点击应用分支，界面全面适配 [compose-miuix-ui](https://github.com/compose-miuix-ui/miuix)。

通过自定义规则，在指定界面满足条件（如屏幕存在特定文字）时，点击节点、位置或执行其他操作 —— 常用来跳过开屏广告和弹窗。

## 下载与安装

- 最新发布包：<https://github.com/84593320z/gkd-/releases/latest>
- 安装包名 `li.songe.gkdx`，与原版 GKD 可并存
- 应用内「检查更新」读取 `miuix-ai` 分支的 [`version/stable.json`](https://github.com/84593320z/gkd-/blob/miuix-ai/version/stable.json)（测试通道为 `version/beta.json`）

## 分支与发版

| 分支 / 标签 | 用途 |
| ---- | ---- |
| `miuix-ai` | 唯一的开发分支，所有提交都在这里 |
| `v*` 标签 | 推送标签触发 `Build-Release`，用固定 keystore 签名并发布 Release |
| 其他提交 | 由 `Build-Apk` 出未发布的测试包（Actions 产物，90 天过期） |

自行编译：`./gradlew :app:assembleGkdRelease`（在 `miuix-ai` 分支）

## 声明

本项目遵循 [GPL-3.0](https://github.com/84593320z/gkd-/blob/miuix-ai/LICENSE) 开源，仅供学习交流，禁止用于商业或非法用途。
