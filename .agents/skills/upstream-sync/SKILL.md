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

合并后按功能名全仓 `grep` 一遍（如 `ai_chat`、`ToggleTranslation`），确认**入口、意图、副作用、路由四处都清干净**。

## 验收

`verifyConfigArchitecture` 必须过——它拦的正是 `data/`、`domain/` 反向 import UI 配置、`ui/` 里乱加 DAO 依赖这类合并时最容易带回来的架构漂移。再跑一次 `assembleAppDebug`，最后在模拟器上装一次确认数据库迁移没崩。
