---
name: book-identity
description: 改书名/作者的判重与换源匹配时使用——精准搜索的入选判据、换源结果缓存、加入书架查重、书单导入的「已存在就跳过」。也用于排查「同一本书被当成两本」「繁体书单导入重复」「换源匹配不到原书」这类身份比对问题。
---

# 书名与作者的比对

同一本书在客户端有多个身份比对点：精准搜索的入选判据、换源结果缓存、加入书架查重、导入书单的「已存在就跳过」。这些点必须共用同一份归一规则，否则会出现「一条路径认得出、另一条认不出」的自相矛盾。

## 规则

- **简繁与台湾异体必须归一**。书单、检索词是简体，站点返回的常是繁体（台湾小说网给「誰讓他修仙的！」「最白的烏鴉」），字面相等会把同一本书判成两本。判等用 `ChineseUtils.sameText`（转繁再转简做基准），`ChineseUtils.variants` 只负责给出能撞库的字形候选，两者配合：SQL 没法做归一，只能先按候选把行捞出来，再在内存里判等。
- **不要为了归一去加载 `ChineseUtils.t2s`**：它会加载阅读界面的排除词表，搜索路径不该顺手改掉用户的转换设置。`sameText` 直接走底层库就是为了避开这一点。
- **新增比对点不要自己写 `==`**。查现有实现（`WebBook.matchesPreciseSearchTarget`、`SearchRepositoryImpl.findChangeSourceBooks`、`BookRepository.getBookByAnyScript`）复用，或补一个同样走 `sameText` 的方法。
- `Book` 的主键是 `bookUrl`，`OnConflictStrategy.REPLACE` 只按它去重。判重放行的后果不是覆盖，而是**多插一行**——同一本书在两个站各留一条记录。

## 验收

`ShelfBookLookupTest` 守「查书架」，`PreciseSearchMatchTest` 守「精准搜索」。加新比对点时补一条同类测试，别只跑构建。
