package io.legado.app.constant

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 发现页只允许 6 个分组标题。书源的分组名是手写字符串，写错就会多出第 7 档，
 * 所以这里把归一规则钉死：任何输入都必须落到 [ExploreGroups.ordered] 里。
 */
class ExploreGroupsTest {

    @Test
    fun `every declared group maps to itself`() {
        ExploreGroups.ordered.forEach { group ->
            val type = when {
                group.startsWith(ExploreGroups.MANGA) -> BookSourceType.image
                group.startsWith(ExploreGroups.VIDEO) -> BookSourceType.video
                else -> BookSourceType.default
            }
            assertEquals(group, ExploreGroups.canonical(group, type))
        }
    }

    @Test
    fun `missing punctuation still lands in the right bucket`() {
        // 历史上写成过「视频·18+」（无空格），不能因为少个空格就变成第 7 个分组。
        assertEquals(
            ExploreGroups.VIDEO_ADULT,
            ExploreGroups.canonical("视频·18+", BookSourceType.video)
        )
        assertEquals(
            ExploreGroups.MANGA_ADULT,
            ExploreGroups.canonical("漫画18+", BookSourceType.image)
        )
    }

    @Test
    fun `unknown group falls back to the source type`() {
        // 新加的源写了别的分类词（或压根没写），按 bookSourceType 落档，不另开一档。
        assertEquals(
            ExploreGroups.VIDEO,
            ExploreGroups.canonical("短剧", BookSourceType.video)
        )
        assertEquals(
            ExploreGroups.MANGA,
            ExploreGroups.canonical("日漫", BookSourceType.image)
        )
        assertEquals(ExploreGroups.NOVEL, ExploreGroups.canonical(null, BookSourceType.default))
        assertEquals(ExploreGroups.NOVEL, ExploreGroups.canonical("", BookSourceType.audio))
    }

    @Test
    fun `declared media word wins over the source type`() {
        // 分组名明确写了媒体类型时以它为准：bookSourceType 是渲染方式，分组名是分类意图。
        assertEquals(
            ExploreGroups.VIDEO_ADULT,
            ExploreGroups.canonical("视频 · 18+", BookSourceType.default)
        )
        assertEquals(
            ExploreGroups.MANGA,
            ExploreGroups.canonical("漫画", BookSourceType.default)
        )
    }

    @Test
    fun `18+ marker is only read from the group name`() {
        assertEquals(
            ExploreGroups.NOVEL_ADULT,
            ExploreGroups.canonical("小说 · 18+", BookSourceType.default)
        )
        assertEquals(
            ExploreGroups.NOVEL,
            ExploreGroups.canonical("小说", BookSourceType.default)
        )
    }
}
