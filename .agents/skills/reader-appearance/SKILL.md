---
name: reader-appearance
description: 改阅读界面的默认排版、配色、菜单样式的默认值时使用，也用于排查「改了阅读配置但界面没变」「内置默认排版对老用户不生效」「菜单卡片挡不住正文」这类问题。改 assets/defaultData 下三份阅读配置前先加载。
---

# 阅读排版与菜单

改阅读界面的默认外观前先分清两个配置来源——它们互不覆盖，只改一处不会生效。

## 排版与配色是两个来源

- **排版**（字号、字距、行距、段距、页边距、标题、页眉页脚）走 `ReadBookConfig.config`，即 `if (shareLayout) shareConfig else durConfig`。`shareLayout` 默认 true，所以生效的是 `assets/defaultData/shareReadConfig.json`；`readConfig.json` 里各预设的同名排版字段此时**不参与渲染**。
- **配色**（背景、正文色）不经过 `config`：`ReadBookViewModel.buildStyleConfig()` 与 `ReadBookRouteScreen` 直接取 `durConfig`，即 `readConfig.json` 中当前选中的那一份预设。

## 菜单配色是第三套开关

由 `readBarStyle` 决定：0=黑白、1=跟随阅读背景、2=自定义。只有 2 会读 `readMenuBgColor/AccentColor/ContainerColor/TextColor`（见 `ReadBookColorTheme.kt` 的 `rememberCustomReadMenuTheme`）。

菜单卡片默认 `readMenuBlurAlpha = 85` 是半透明，要做实心卡片必须设 100，否则正文会透上来。

## 改内置默认值只对全新安装生效

`assets/defaultData/` 那三份是**兜底**：`ReadStyleRepository.readConfigs/readShareConfig` 先看 `filesDir` 下的同名文件，取不到才读 asset。因此改内置默认值只对全新安装生效，已装过的设备读的是它自己的那份，不会被覆盖——要让升级也换排版，得另做一条覆盖路径，光改 asset 不生效。

验证时先确认测的是哪条路径：模拟器上若之前装过同包名的包，`filesDir` 里已有旧配置，改 asset 看不出任何变化。要验全新安装语义就先 `adb uninstall` 再装。
