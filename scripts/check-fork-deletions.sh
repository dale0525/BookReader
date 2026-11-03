#!/usr/bin/env bash
# 校验 fork 主动删除的上游功能有没有被重新合并回来。
#
# 用法：在合并裁定完、提交前运行。
#   ./scripts/check-fork-deletions.sh          # 检查工作区
#   ./scripts/check-fork-deletions.sh --staged # 检查暂存区
#
# 只报告，不改代码。退出码 1 表示有需要处理的项。

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

FAIL=0
report() { printf '  %s\n' "$1"; FAIL=1; }

echo "== 1. 已删除路径是否重新出现 =="
# fork 主动删除的目录/文件。上游把它们加回来时，这里会命中。
DELETED_PATHS=(
  "app/src/main/java/io/legado/app/data/repository/manga/translation"
  "app/src/main/java/io/legado/app/feature/manga/translation"
  "app/src/main/java/io/legado/app/domain/model/TranslationConstants.kt"
  "app/src/main/java/io/legado/app/domain/model/TranslationModels.kt"
  "app/src/main/java/io/legado/app/domain/model/TranslationResultPreviewParser.kt"
  "app/src/main/java/io/legado/app/domain/model/TranslationDictionaryPolicy.kt"
  "app/src/main/java/io/legado/app/domain/model/PartialTranslationAssembler.kt"
  "app/src/main/java/io/legado/app/domain/gateway/TranslationSettingsGateway.kt"
  "app/src/main/java/io/legado/app/domain/gateway/TranslationCacheGateway.kt"
  "app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt"
  "app/src/main/java/io/legado/app/domain/usecase/AiChatGenerationUseCase.kt"
  "app/src/main/java/io/legado/app/model/translation"
  "app/src/main/java/io/legado/app/ui/config/labConfig"
  "app/src/main/java/io/legado/app/ui/config/translation"
  "app/src/main/java/io/legado/app/ui/ai/chat"
  "app/src/core"
  "app/src/full"
  "app/src/testFull"
  "app/src/androidTestFull"
  "app/src/main/assets/manga-vision"
)

for p in "${DELETED_PATHS[@]}"; do
  [ -e "$p" ] && report "重新出现：$p"
done

echo "== 2. 已删除功能的特征关键词是否有新命中 =="
# 关键词 -> 说明。命中即说明钩子被静默合并回来。
# 排除清单里保留的自有功能（听书角色分配用 AiCast*，段评气泡用 Bubble*）。
# 默认排除两类合法引用：数据库迁移里的 DeleteTable 删除语句，以及注释行。
DEFAULT_EXCLUDE='DeleteTable|:[0-9]+:[[:space:]]*(\*|//|/\*)'

check_keyword() {
  local kw="$1" why="$2" extra_exclude="$3"
  local exclude="$DEFAULT_EXCLUDE"
  [ -n "$extra_exclude" ] && exclude="$DEFAULT_EXCLUDE|$extra_exclude"
  local hits
  hits=$(grep -rn --include=*.kt --include=*.xml "$kw" app/src 2>/dev/null \
         | grep -vE "$exclude" | head -5)
  [ -n "$hits" ] && { report "$why（关键词 $kw）"; printf '%s\n' "$hits" | sed 's/^/    /'; }
}

check_keyword "ai_chat_conversations" "AI 聊天数据表被加回" ""
check_keyword "AiChatScreen"           "AI 聊天界面被加回" ""
check_keyword "LabSettingsGateway"     "实验室设置页被加回" ""
check_keyword "TranslationManager"     "章节翻译管理器被加回" ""
check_keyword "ToggleTranslation"      "翻译开关被加回" ""
check_keyword "TranslateChapterUseCase" "章节翻译用例被加回" ""
check_keyword "MangaLocalEngine"       "本地推理引擎被加回" "app/src/core|app/src/full"
check_keyword "MANGA_LOCAL_ENGINE"     "本地推理构建开关被加回" ""

echo "== 3. 数据库 schema 是否含已删除的表 =="
SCHEMA_DIR="app/schemas/io.legado.app.data.AppDatabase"
if [ -d "$SCHEMA_DIR" ]; then
  latest=$(ls "$SCHEMA_DIR"/*.json 2>/dev/null | sort -V | tail -1)
  if [ -n "$latest" ]; then
    if grep -q "ai_chat_conversations\|ai_chat_messages" "$latest" 2>/dev/null; then
      report "最新 schema $(basename "$latest") 含 AI 聊天表"
    fi
  fi
fi

echo
if [ "$FAIL" -eq 0 ]; then
  echo "通过：没有发现被删除功能回流。"
  exit 0
else
  echo "发现需要处理的项（见上）。"
  exit 1
fi
