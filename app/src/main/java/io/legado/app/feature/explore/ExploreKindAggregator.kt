package io.legado.app.feature.explore

import io.legado.app.data.entities.rule.ExploreKind

/** 聚合后的分类 URL：多个站点地址用该分隔符拼接。 */
const val URL_SEPARATOR = "\n"

/**
 * 把标题相同、档位相同的分类合并成一个，顺序按首次出现的位置。
 *
 * 聚合源的每个站点都会给出同名分类（例如「玄幻」），合并后客户端只展示一个入口，
 * 点进去由多个 URL 并发取回再合并，而不是让用户逐站翻。
 */
fun aggregateExploreKinds(kinds: List<ExploreKind>): List<ExploreKind> =
    kinds.groupBy { it.title to it.rank }
        .values
        .map { group ->
            group.first().copy(url = group.mapNotNull { it.url }.joinToString(URL_SEPARATOR))
        }

fun ExploreKind.targetUrls(): List<String> =
    url.orEmpty().split(URL_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
