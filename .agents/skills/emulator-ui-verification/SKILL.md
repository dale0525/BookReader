---
name: emulator-ui-verification
description: 在模拟器或真机上验证界面改动时使用，也用于排查「adb 点了没反应」「界面渲染正常但按钮点不到」「装包后崩在数据库迁移」这类只在设备上出现的问题。界面改动要冒烟、或要装调试包前先加载。
---

# 界面验证

界面相关改动要在真实界面上确认，不要只凭单元测试下结论。以下每条都对应一次误判。

## 装包

```bash
pixi run bash -c './gradlew assembleAppDebug -PenableAbiSplits=false'
adb -s emulator-5554 install -r app/build/outputs/apk/app/debug/app-app-debug.apk
```

调试包只在装机验证时装，不归档、不外发。

**装包前先比一次 `AppDatabase.version`。** 分支基于旧 `main` 时它可能低于 `main`（历史一例：分支 133、当时的 `main` 134），`install -r` 覆盖装到用 `main` 构建过的模拟器上会直接崩在 `A migration from 134 to 133 was required but not found`。这是环境残留，不是代码缺陷——先 `adb uninstall` 再干净安装，别去补降级迁移。

## 点坐标：不要从截图量

`adb shell input tap` 的坐标**不能从 `screencap` 截图里量**：截图预览会被缩放，量出来的点和真实触摸坐标能差 300–400px，表现为「点了没反应」，极易被误判成代码缺陷。

坐标只从这两个来源取：

- `uiautomator dump` 的 `bounds="[x1,y1][x2,y2]"`，取中心点。这是真实屏幕坐标。
- `Modifier.onGloballyPositioned { it.positionInWindow() }` 打日志，验完即删。

模拟器软渲染下容易 ANR，卡住时优先换 JVM 测试验证逻辑，界面只做最后冒烟。

## 「点了没反应」的两种结构性成因

- **整屏浮层抢走点击**。控制层这类「遮罩 + 一排按钮」的结构，把两者放成同一个 `Column` 的兄弟分块，不要用整屏 `Box` 遮罩再靠 `Spacer` 或坐标判定让出底部——让出的高度一旦小于按钮实际高度，按钮就永远点不到。
- **普通 Compose 层永远压不住 miuix 的原生窗口**。`AppModalBottomSheet`、`AppAlertDialog`、`RoundDropdownMenu` 分别走 `WindowBottomSheet`、`WindowDialog`、`WindowListPopup`，各自创建独立原生窗口，不在 Activity 的 Compose 树里。因此从这些弹层里打开的二层界面（如「更多设置」→「屏幕触控」九宫格）若只是同级的 `Box`，会被父弹层的窗口整个盖住：界面看不见，点击也收不到，且 `uiautomator dump` 里能看到它的节点——很容易误判成「渲染正常但点击失效」。

  二层界面必须自己成窗口（`Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))` 或 miuix 窗口组件），把动作选择之类的子弹窗一并放进同一个窗口内。同一结构在独立设置页（`ReadConfigScreen`）也会复现，因为那里它同样是列表的 Compose 兄弟节点。

## 判读结果

- `adb devices` 里有 `emulator-5554` 且 `getprop sys.boot_completed` 为 `1` 才算可用。TV box 之类的远程设备（`192.168.123.240:5555`）实测不可用，别往那儿装。
- 确认改动生效，看**新起进程**后的界面，不要看旧进程里残留的 Activity：`adb shell am force-stop <包名>` 再 `am start`。
- 调试包包名是 `com.logiconsole.bookreader.debug`，启动 Activity 是 `io.legado.app.ui.main.MainActivity`（不是 WelcomeActivity，用 `cmd package resolve-activity --brief <包名>` 确认）。
