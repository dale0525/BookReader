#!/usr/bin/env bash
# 由 pixi.toml 的 [activation] 引入，给每个 pixi 任务准备好 Android 工具链的环境变量。
# 只在 shell 里注入变量，不下载任何东西（SDK 本身由 `pixi run sdk` 装）。

export ANDROID_HOME="$PIXI_PROJECT_ROOT/.android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# Gradle 通过 PATH 找 adb/aapt2/emulator，通过 ANDROID_HOME 找 platforms。
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# 模拟器与 AVD 数据留在仓库内，避免污染 ~/.android。
export ANDROID_AVD_HOME="$PIXI_PROJECT_ROOT/.android-avd"
