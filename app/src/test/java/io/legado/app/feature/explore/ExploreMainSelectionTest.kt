package io.legado.app.feature.explore

import io.legado.app.data.entities.ExploreSourceSummary
import io.legado.app.data.entities.rule.ExploreKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreMainSelectionTest {
    @Test
    fun `sources without a group fall into one trailing bucket`() {
        // 未分组的源不能散落成多档，也不能被当成「全部」。
        val groups = buildExploreSourceGroups(
            sources = listOf(
                summary("a", "A", "小说"),
                summary("b", "B", null),
                summary("c", "C", "小说"),
            ),
            searchKey = "",
            selectedGroup = EXPLORE_GROUP_ALL,
        )

        assertEquals(listOf("小说", EXPLORE_GROUP_NONE), groups.map { it.name })
        assertEquals(listOf("B"), groups[1].sources.map { it.name })
    }

    @Test
    fun `multi-value group keeps the card in its first group only`() {
        val groups = buildExploreSourceGroups(
            sources = listOf(summary("a", "A", "小说, 玄幻")),
            searchKey = "",
            selectedGroup = EXPLORE_GROUP_ALL,
        )

        assertEquals(listOf("小说"), groups.map { it.name })
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
    ) = ExploreSourceSummary(
        bookSourceUrl = url,
        bookSourceName = name,
        bookSourceGroup = group,
        bookSourceComment = comment,
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
