package io.legado.app.feature.explore

import io.legado.app.data.entities.rule.ExploreKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 聚合把同名分类收成一个入口，URL 用 [URL_SEPARATOR] 拼起来交给多目标并发取回。
 * 这条合并规则决定了发现页是一个分类格子还是一个站点列表，改动会直接改变界面结构。
 */
class ExploreKindAggregatorTest {

    @Test
    fun sameTitleAndRankMergesIntoSingleEntry() {
        val kinds = listOf(
            kind("玄幻", "https://a.example/x#a"),
            kind("玄幻", "https://b.example/y#b"),
            kind("都市", "https://a.example/d#a"),
        )

        val merged = aggregateExploreKinds(kinds)

        assertEquals(listOf("玄幻", "都市"), merged.map { it.title })
        assertEquals(
            listOf("https://a.example/x#a", "https://b.example/y#b"),
            merged.first().targetUrls(),
        )
    }

    @Test
    fun sameTitleWithDifferentRankStaysSeparate() {
        // 分类「玄幻」与榜单档位「玄幻」不是同一个入口，档位单独成卡片。
        val kinds = listOf(
            kind("玄幻", "https://a.example/x#a"),
            kind("玄幻", "https://a.example/top#a", rank = "all"),
        )

        val merged = aggregateExploreKinds(kinds)

        assertEquals(2, merged.size)
        assertEquals(listOf("", "all"), merged.map { it.rank })
    }

    @Test
    fun firstSeenOrderIsPreserved() {
        val kinds = listOf(
            kind("仙侠", "https://a.example/1#a"),
            kind("玄幻", "https://a.example/2#a"),
            kind("仙侠", "https://b.example/1#b"),
        )

        assertEquals(listOf("仙侠", "玄幻"), aggregateExploreKinds(kinds).map { it.title })
    }

    @Test
    fun kindWithoutUrlYieldsNoTargets() {
        // 文本/开关类分类没有 URL，聚合后不能凭空造出一个目标。
        val merged = aggregateExploreKinds(listOf(kind("筛选", null)))

        assertEquals(emptyList<String>(), merged.single().targetUrls())
    }

    private fun kind(title: String, url: String?, rank: String = "") =
        ExploreKind(title = title, url = url, rank = rank)
}
