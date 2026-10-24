#!/usr/bin/env bash
# 把 Android SDK 装进仓库内的 .android-sdk/（已 gitignore），并建好模拟器。
#
# 为什么用 sdkmanager 而不是直接解压官方 zip：AGP 需要每个包目录下有 package.xml 才能识别，
# 手工解压的 zip 里没有这个文件，SDK 会被判定为未安装。sdkmanager 会一并写好。
set -euo pipefail

ROOT="$PIXI_PROJECT_ROOT"
SDK="$ROOT/.android-sdk"
AVD_NAME="legado-api37"
IMAGE="system-images;android-37.0;google_apis;arm64-v8a"
PACKAGES=(
  "platform-tools"
  "platforms;android-37.1"
  "build-tools;37.0.0"
  "emulator"
  "$IMAGE"
)

say() { printf '\n==> %s\n' "$*"; }

say "安装 Android 命令行工具"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  tmp="$(mktemp -d)"
  curl -fL --retry 3 -o "$tmp/tools.zip" \
    "https://mirrors.cloud.tencent.com/AndroidSDK/commandlinetools-mac_arm64-16111833_latest.zip"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools/latest"
  # 官方 zip 顶层是 cmdline-tools/，sdkmanager 要求放在 cmdline-tools/latest/ 下。
  mv "$tmp/cmdline-tools/"* "$SDK/cmdline-tools/latest/"
  rm -rf "$tmp"
fi

say "接受许可并安装 SDK 包（约 2.6 GB，首次较慢）"
# sdkmanager 读完许可后会关闭管道，yes 随之收到 SIGPIPE 并以 141 退出。
# 在 pipefail 下这会让整个管道判定失败、触发 set -e 提前退出（包已装好也不继续），
# 所以这两处管道临时关掉 pipefail。
set +o pipefail
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
yes | "$SDKMANAGER" --sdk_root="$SDK" "${PACKAGES[@]}" || true
set -o pipefail

# 上面吞掉了退出码，这里按实际产物判断成败。
for p in "platform-tools/adb" "platforms/android-37.1/android.jar" \
         "build-tools/37.0.0/aapt2" "emulator/emulator" \
         "system-images/android-37.0/google_apis/arm64-v8a/system.img"; do
  [ -e "$SDK/$p" ] || { echo "[错误] 缺少 $p" >&2; exit 1; }
done

say "创建模拟器 $AVD_NAME"
# avdmanager 不会自己建 AVD 根目录，缺了会静默什么都不做。
mkdir -p "${ANDROID_AVD_HOME:?ANDROID_AVD_HOME 未设置，检查 scripts/activate.sh}"
if "$SDK/cmdline-tools/latest/bin/avdmanager" list avd 2>/dev/null | grep -q "Name: $AVD_NAME"; then
  echo "已存在，跳过"
else
  echo "no" | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd \
    --name "$AVD_NAME" --package "$IMAGE" --device "pixel_6"
fi

say "完成"
echo "ANDROID_HOME=$SDK"
echo "模拟器: $AVD_NAME（数据目录 .android-avd/）"
echo "接下来跑：pixi run web"
