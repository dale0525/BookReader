#!/usr/bin/env bash
# 一条命令起开发用的 web 服务：
#   起模拟器 → 装调试包 → 打开「启动 web 服务」开关 → 打印本机与局域网地址
# 等价于客户端「设置 → 其他设置 → 启动 web 服务」那个开关，只是从命令行触发。
set -euo pipefail

ROOT="$PIXI_PROJECT_ROOT"
AVD_NAME="legado-api37"
APP_ID="io.legato.kazusa.debug"
WEB_PORT=1122          # OtherConfigContract.kt 里 webPort 的默认值
ACTIVITY="$APP_ID/io.legado.app.ui.main.MainActivity"
APK="$ROOT/app/build/outputs/apk/app/debug/app-app-arm64-v8a-debug.apk"

say() { printf '\n==> %s\n' "$*"; }
die() { printf '\n[错误] %s\n' "$*" >&2; exit 1; }

command -v adb >/dev/null || die "找不到 adb，先跑一次 pixi run sdk"
[ -d "$ANDROID_HOME/platforms/android-37.1" ] || die "SDK 不完整，先跑一次 pixi run sdk"

say "启动模拟器 $AVD_NAME"
if ! adb devices | grep -q "emulator.*device$"; then
  # AVD 根目录不存在时 emulator 会静默失败，先补上。
  mkdir -p "$ANDROID_AVD_HOME"
  # -netdelay/-netspeed none：避免模拟器默认的弱网模拟拖慢局域网请求。
  nohup emulator -avd "$AVD_NAME" -no-snapshot-save -no-boot-anim \
    -gpu swiftshader_indirect -netdelay none -netspeed full \
    >"$ANDROID_AVD_HOME/emulator.log" 2>&1 &
  adb wait-for-device
  # wait-for-device 只等到 adbd 可用，sys.boot_completed 出现才算系统起来。
  until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
fi
echo "已就绪：Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"

say "编译并安装调试包"
(cd "$ROOT" && ./gradlew assembleAppDebug --no-configuration-cache -q)
# 先停掉旧进程再装。若 App 正在前台，install -r 会留下一个正在销毁的 Activity 记录，
# 之后 am start 的 extra 会投给那个将死的实例、随重建一起丢掉（实测服务起不来）。
adb shell am force-stop "$APP_ID" 2>/dev/null || true
adb install -r "$APK" >/dev/null
echo "安装完成"

say "打开 web 服务开关"
# Android 17 起局域网入站需要该权限；先授掉，免得起服务时弹框等人点。
adb shell pm grant "$APP_ID" android.permission.ACCESS_LOCAL_NETWORK 2>/dev/null || true

# 走 MainActivity 的 webServiceLocalNetwork extra：与磁贴同一个入口，
# 它会检查权限再 startForeground，比直接 startservice 可靠。
start_service() {
  adb shell am start -n "$ACTIVITY" -a android.intent.action.MAIN \
    --ez webServiceLocalNetwork true >/dev/null 2>&1 || true
}

activity_resumed() {
  adb shell dumpsys activity activities 2>/dev/null \
    | grep -q "topResumedActivity.*$APP_ID/io.legado.app.ui.main.MainActivity"
}

ready() { curl -sS --max-time 3 "http://127.0.0.1:$WEB_PORT/getReadConfig" 2>/dev/null | grep -q isSuccess; }
service_running() {
  adb shell "dumpsys activity services $APP_ID" 2>/dev/null | grep -q "io.legado.app.service.WebService"
}

# 只发一次，然后纯轮询。不能循环重发：MainActivity 每收到这个 extra 都会再调一次
# WebService.startForeground → upWebServer()，而 upWebServer() 第一句是
# ktorServer?.stop()，重发等于把刚建好的服务反复拆掉。
# 上面已经 force-stop 过，这里必然是干净冷启动，extra 不会丢。
start_service
for _ in $(seq 1 30); do
  activity_resumed && break
  sleep 2
done
activity_resumed || die "MainActivity 没起来，看日志：adb logcat -s MainActivity AndroidRuntime"

say "映射端口到本机"
# 先清掉旧规则：上一次运行残留的 forward 会指向已失效的会话，表现为 Empty reply。
adb forward --remove-all >/dev/null 2>&1 || true
adb forward tcp:$WEB_PORT tcp:$WEB_PORT >/dev/null
adb forward tcp:$((WEB_PORT + 1)) tcp:$((WEB_PORT + 1)) >/dev/null

say "自检"
# Ktor 的 embeddedServer 是异步 start 的，端口可能还要几十秒才接受连接。
for _ in $(seq 1 60); do
  ready && break
  sleep 2
done
ready || die "本机端口不通，看日志：adb logcat -s WebService KtorServer"
echo "http://127.0.0.1:$WEB_PORT 通"

# adb forward 只绑回环，局域网设备连不进来；用 socat 把本机局域网 IP 转发到回环。
LAN_IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
if [ -n "$LAN_IP" ]; then
  say "转发局域网地址"
  for p in $WEB_PORT $((WEB_PORT + 1)); do
    pkill -f "socat TCP-LISTEN:$p,bind=$LAN_IP" 2>/dev/null || true
    nohup socat "TCP-LISTEN:$p,bind=$LAN_IP,fork,reuseaddr" "TCP:127.0.0.1:$p" \
      >"/tmp/legado-socat-$p.log" 2>&1 &
  done
  sleep 2
  if curl -sS --max-time 5 "http://$LAN_IP:$WEB_PORT/getReadConfig" 2>/dev/null | grep -q isSuccess; then
    echo "局域网可访问"
  else
    echo "局域网转发未生效，见 /tmp/legado-socat-*.log"
  fi
fi

cat <<EOF

web 服务已启动
  本机     http://127.0.0.1:$WEB_PORT/
  WebSocket ws://127.0.0.1:$((WEB_PORT + 1))/searchBook
EOF
if [ -n "$LAN_IP" ]; then
  cat <<EOF
  局域网   http://$LAN_IP:$WEB_PORT/
           ws://$LAN_IP:$((WEB_PORT + 1))/searchBook
EOF
fi
cat <<EOF

改端口：客户端「设置 → 其他设置 → web 服务端口」，改完重跑本命令。
关服务：adb shell am force-stop $APP_ID
EOF
