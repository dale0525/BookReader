#!/usr/bin/env bash
# 由 pixi.toml 的 [activation] 引入，给每个 pixi 任务准备好 Android 工具链的环境变量。
# 只在 shell 里注入变量，不下载任何东西（SDK 本身由 `pixi run sdk` 装）。

# 工具链目录一律取主检出（main worktree），不用 $PIXI_PROJECT_ROOT。
# 原因：从 worktree 里跑 gradle 会启动一个 javaHome 指向该 worktree 的 daemon；worktree 一删，
# 这个 daemon 仍在复用，主工作区下次构建就报
#   Directory '…/.worktrees/<name>/.pixi/envs/default/lib/jvm' (Current JVM) … does not exist
#   Cannot find a Java installation … languageVersion=21
# 且只有 `./gradlew --stop` + 清配置缓存才能恢复。JDK 路径固定在主检出后，删 worktree 不再影响任何 daemon。
_toolchain_root="$PIXI_PROJECT_ROOT"
_git_common_dir="$(git -C "$PIXI_PROJECT_ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)"
if [ -n "$_git_common_dir" ]; then
  _main_root="${_git_common_dir%/.git}"
  # 主检出还没建过 pixi 环境时留在当前根目录，避免把 JAVA_HOME 指到不存在的路径。
  if [ -x "$_main_root/.pixi/envs/default/lib/jvm/bin/java" ]; then
    _toolchain_root="$_main_root"
  fi
fi

export JAVA_HOME="$_toolchain_root/.pixi/envs/default/lib/jvm"

export ANDROID_HOME="$_toolchain_root/.android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# Gradle 通过 PATH 找 adb/aapt2/emulator，通过 ANDROID_HOME 找 platforms。
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# 模拟器与 AVD 数据留在仓库内，避免污染 ~/.android。
export ANDROID_AVD_HOME="$_toolchain_root/.android-avd"

unset _toolchain_root _git_common_dir _main_root
