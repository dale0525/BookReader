---
name: upstream-sync
description: 合并 upstream（HapeLee/legado-with-MD3）到本 fork 时使用。也用于排查「合并后数据库崩溃」「覆盖安装撞 Room schema 校验」「上游把 fork 删掉的功能改回来了」这类合并后遗症。
---

# 上游同步

`upstream` 指向 `HapeLee/legado-with-MD3`，fork 在 `main` 上累积自己的删除与改造。合并时有两处必查，都不是冲突能提醒你的。

## 数据库版本号不能沿用上游的

fork 已把版本号发过版，同一版本号换了 schema，覆盖安装的用户会撞 Room 的 schema 校验。

1. 上游的 `AutoMigration` 顺延到「已发布的最高版本 + 1」，`AppDatabase.version` 同步改。
2. 跑 `:app:kspAppDebugKotlin` 重新生成 `app/schemas/…/<新版本>.json` 并提交。
3. 核对方式：拿新旧 schema 的 `entities` 对比，确认只多出预期的那几列。

漏了这一步的表现是覆盖安装直接崩在 `A migration from N to N-1 was required but not found`，或全新安装后某个字段恒为默认值。

## fork 删掉的功能会被静默改回来

上游对同一功能的后续提交会以 modify/delete 冲突出现，这部分好处理。真正的坑是同一批改动里**与功能无关的钩子**（按钮池条目、Intent、Effect、菜单项）被自动合并静默留下：

- 轻则编译期才暴露；
- 重则编译通过，但留下一个点了没反应的入口。

最坏的一档是**导航 entry 被注册两次**：编译、单测、打包全过，装机后冷启动直接崩。navigation3 的 `EntryProviderScope` 不允许同一个 `NavKey` 注册两个 entry，组装导航图时抛 `IllegalArgumentException: An \`entry\` with the same \`clazz\` has already been added`。0.3.13 就是这么崩的——upstream 带入的播放页 entry 和 fork 自己的那份同时留在 `MainNavGraph.kt` 里，R8 混淆后的栈只看得出 `MainActivity.z` 与一堆 `q3.d`，认不出是路由问题，且与机型无关。

合并后除跑校验脚本，再扫一次全仓 entry 唯一性：

```bash
grep -rhno "entry<[A-Za-z0-9_]*>" --include=*.kt app/src/main \
  | sed -E 's/.*entry<([A-Za-z0-9_]*)>/\1/' | sort | uniq -c | awk '$1>1'
```

无输出才算通过。这类重复 `check-fork-deletions.sh` 发现不了——它查路径与关键词，而这里两个 entry 都在同一个现存文件里。

合并裁定完、提交前跑一次校验脚本，它会查三类情况：已删路径重新出现、已删功能的特征关键词有新命中、最新 schema 里含已删的表。

```bash
./scripts/check-fork-deletions.sh
```

脚本只报告不改代码——自动删文件在误判时会静默破坏构建，报告误判只是多看一眼。发现新项就人工处理，并把新规则补进脚本的 `DELETED_PATHS` 或 `check_keyword` 调用。

## 基准线由 merge 自己维护

合并提交的第二个父提交就是上游基准线，`git merge-base HEAD upstream/main` 会算出同一个提交，下次合并只带入基准线之后的提交。**所以同步只能用 `git merge`**：`cherry-pick` 和 `rebase` 不产生基准线，下次会把同样的改动再拉一遍。

## 验收

`verifyConfigArchitecture` 必须过——它拦的正是 `data/`、`domain/` 反向 import UI 配置、`ui/` 里乱加 DAO 依赖这类合并时最容易带回来的架构漂移。再跑一次 `assembleAppDebug`，最后在模拟器上装一次确认数据库迁移没崩。
