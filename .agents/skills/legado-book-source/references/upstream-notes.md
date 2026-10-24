# 上游语料说明

`corpus/` 下是从两个上游仓库原样搬运的语料，供查证具体说法时对照。它们不是本技能的规范，只是资料。

## 目录

```
corpus/
├── upstream-a/            book-source-creator-skill（无 license 声明）
│   ├── SKILL.md           原技能说明
│   ├── references/        15 份 md + 2 份 JSON（规则包、经验教训）
│   └── examples/          按模式分类的示例书源
└── upstream-b/            legadoSkill（MIT，Copyright (c) 2026 天天的鸟蛋蛋）
    ├── LICENSE            MIT 原文
    ├── assets/            规则手册、CSS 选择器清单、严格输出模板、编码指南、JS 方法表
    └── samples/           5 份真实书源记录
```

## 用法

- 查"某条规则/某个字段怎么用"：先看本技能的 `syntax.md`、`selector-traps.md`、`js-runtime.md`，那里是按本仓库 App 实测校正过的。上游文档与实测冲突时以实测为准。
- 查"某个站点类型的完整示例"：`corpus/upstream-a/examples/` 按模式分了目录（如 `pattern-post-detail-toc`、`pattern-css-pagination`）。
- 查"严格输出模板"：`corpus/upstream-b/assets/书源输出模板_严格模式.md`。
- 查"CSS 选择器完整清单"：`corpus/upstream-b/assets/css选择器规则.txt`（含 jsoup 不支持的 jQuery 伪类，读时注意）。
- 查"编码处理"：`corpus/upstream-b/assets/Legado书源编码处理指南.md`。
- 查"JS 扩展方法"：`corpus/upstream-b/assets/方法-JS扩展类.md`、`方法-加密解密.md`。

## 已知与实测冲突的地方

| 上游说法 | 实测结论 |
| --- | --- |
| 支持 `:first`、`:last`、`:eq()` 等 jQuery 伪类 | jsoup 1.16.2 无 `:first`/`:last`/`:text`/`:visible`/`:odd`/`:even`，抛异常；`:eq`/`:lt`/`:gt` 是兄弟下标语义 |
| 用 `;post=` 传 POST | 本分支无此语法，用 `,{"method":"POST","body":"..."}` |
| 索引可写在动作后（`@text[0]`） | 取不到值，索引必须在选择器段后 |
| `##匹配##替换###` 是"只替换第一个" | 实际是"只保留第一个匹配的替换文本"，其余内容丢弃 |
| validator / Android Probe 是必需验证环节 | 本技能改用本仓库 App 的 `saveBookSources` + `bookSourceDebug` WebSocket，不用 JVM validator 和 Probe APK |

## 版权

- `upstream-a/` 的上游仓库未附 license 文件。搬运范围限于规则文档与示例，未包含其 validator jar 与 Probe APK。
- `upstream-b/` 为 MIT，`LICENSE` 原文随语料一并保留，版权声明不得删除。
