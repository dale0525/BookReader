package io.legado.app.feature.explore

import io.legado.app.constant.BookSourceType
import io.legado.app.constant.ExploreGroups
import io.legado.app.data.entities.ExploreSourceSummary
import io.legado.app.data.entities.rule.ExploreKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreMainSelectionTest {
    @Test
    fun `sources with an unknown group fall back into a real bucket`() {
        // 分组名是手写的，写成别的词也不能另开一档：按 bookSourceType 落进 6 档之一。
        val groups = buildExploreSourceGroups(
            sources = listOf(
                summary("a", "A", "小说"),
                summary("b", "B", "短剧", type = BookSourceType.video),
                summary("c", "C", "小说"),
            ),
            searchKey = "",
            selectedGroup = EXPLORE_GROUP_ALL,
        )

        assertEquals(listOf(ExploreGroups.NOVEL, ExploreGroups.VIDEO), groups.map { it.name })
        assertEquals(listOf("B"), groups[1].sources.map { it.name })
    }

    @Test
    fun `multi-value group keeps the card in its first group only`() {
        val groups = buildExploreSourceGroups(
            sources = listOf(summary("a", "A", "小说, 玄幻")),
            searchKey = "",
            selectedGroup = EXPLORE_GROUP_ALL,
        )

        assertEquals(listOf(ExploreGroups.NOVEL), groups.map { it.name })
    }

    @Test
    fun `group order is fixed, not the order sources arrive in`() {
        // 顺序固定成 小说 / 漫画 / 视频 / 各 18+，源的排列变了也不会让分组标题换位置。
        val groups = buildExploreSourceGroups(
            sources = listOf(
                summary("v", "V", "视频 · 18+", type = BookSourceType.video),
                summary("m", "M", "漫画", type = BookSourceType.image),
                summary("n", "N", "小说"),
            ),
            searchKey = "",
            selectedGroup = EXPLORE_GROUP_ALL,
        )

        assertEquals(
            listOf(ExploreGroups.NOVEL, ExploreGroups.MANGA, ExploreGroups.VIDEO_ADULT),
            groups.map { it.name },
        )
    }

    @Test
    fun `every card lands in one of the six groups`() {
        // 随便写什么分组名、什么类型，都只能落到 6 档里，不许出现第 7 个标题。
        val groups = buildExploreSourceGroups(
            sources = listOf(
                summary("a", "A", null, type = BookSourceType.default),
                summary("b", "B", "", type = BookSourceType.image),
                summary("c", "C", "玄幻", type = BookSourceType.audio),
                summary("d", "D", "视频·18+", type = BookSourceType.video),
                summary("e", "E", "未分组", type = BookSourceType.file),
            ),
            searchKey = "",
            selectedGroup = EXPLORE_GROUP_ALL,
        )

        assertTrue(
            "出现了 6 档之外的分组: ${groups.map { it.name }}",
            groups.all { it.name in ExploreGroups.ordered }
        )
        assertEquals(5, groups.sumOf { it.sources.size })
    }

    @Test
    fun `category key distinguishes same title with different url`() {
        val books = ExploreKind(title = "books", url = "https://a/books")
        val other = ExploreKind(title = "books", url = "https://b/books")
        assertEquals(exploreKindKey(books), exploreKindKey(books.copy()))
        assertNotEquals(exploreKindKey(books), exploreKindKey(other))
    }

    @Test
    fun `category memory round-trips and ignores malformed entries`() {
        val selections = mapOf(
            "https://source-a" to "1:a:https://a/books",
            "https://source-b" to "1:b:https://b/books",
        )
        assertEquals(
            selections,
            parseExploreKindSelections(encodeExploreKindSelections(selections))
        )
        assertTrue(parseExploreKindSelections("").isEmpty())
        assertTrue(parseExploreKindSelections("no-separator").isEmpty())
        assertEquals(
            mapOf("https://source-a" to "1:a:x"),
            parseExploreKindSelections("\u0001https://source-a\u00001:a:x\u0001malformed"),
        )
    }

    @Test
    fun `card subtitle survives grouping`() {
        val groups = buildExploreSourceGroups(
            sources = listOf(summary("a", "A", "小说", "8 个分类 · 2 个榜单")),
            searchKey = "",
            selectedGroup = EXPLORE_GROUP_ALL,
        )

        assertEquals("8 个分类 · 2 个榜单", groups.single().sources.single().subtitle)
    }

    private fun summary(
        url: String,
        name: String,
        group: String?,
        comment: String? = null,
        type: Int = BookSourceType.default,
    ) = ExploreSourceSummary(
        bookSourceUrl = url,
        bookSourceName = name,
        bookSourceGroup = group,
        bookSourceComment = comment,
        bookSourceType = type,
    )

    @Test
    fun `pager contains only loadable unique categories`() {
        val kinds = listOf(
            ExploreKind(title = "filter", url = null, type = ExploreKind.Type.select),
            ExploreKind(title = "books", url = "https://example.com", type = ExploreKind.Type.url),
        )
        assertEquals(
            listOf(kinds[1]), loadableExploreMainKinds(
                kinds + kinds[1] +
                        ExploreKind(title = "blank", url = "", type = ExploreKind.Type.url)
            )
        )
    }
}
