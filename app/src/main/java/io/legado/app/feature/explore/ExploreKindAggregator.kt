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

/**
 * 分类 tab 行该高亮哪一项。pager 里同时装着榜单档位和普通分类，停到榜单页时没有任何分类
 * 对应，这里返回 -1 表示「不选中」，由 AppTabRow 负责把指示条藏掉。直接把这个 -1 交给
 * material3 的 TabRow 会抛 IndexOutOfBoundsException。
 */
fun selectedCategoryTabIndex(categoryPages: List<Int>, currentPage: Int): Int =
    categoryPages.indexOf(currentPage)
