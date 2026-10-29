package io.legado.app.data.repository

import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.ExploreSourceSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「隐藏成人内容」对发现页源列表的过滤。
 *
 * 分组标题由实际存在的源推导，所以源被滤光后「漫画 · 18+」这类标题自然消失，
 * 不需要另外维护一份标题黑名单——这正是把过滤放在源列表而非分组列表的原因。
 */
class AdultContentFilterTest {

    private val adultUrl = "https://adult.example.com"
    private val normalUrl = "https://normal.example.com"

    private fun source(url: String, group: String?, type: Int = 0) = ExploreSourceSummary(
        bookSourceUrl = url,
        bookSourceName = url,
        bookSourceGroup = group,
        bookSourceType = type,
    )

    @Test
    fun `开关关闭时不过滤任何源`() {
        val sources = listOf(
            source(adultUrl, "小说 · 18+"),
            source(normalUrl, "小说"),
        )
        val result = AdultContentFilter.filterSources(sources, hideAdult = false)
        assertEquals(2, result.size)
    }

    @Test
    fun `开关开启时隐藏成人源`() {
        val sources = listOf(
            source(adultUrl, "小说 · 18+"),
            source(normalUrl, "小说"),
        )
        val result = AdultContentFilter.filterSources(sources, hideAdult = true)
        assertEquals(listOf(normalUrl), result.map { it.bookSourceUrl })
    }

    @Test
    fun `隐藏成人源后不留下空的成人分组标题`() {
        val sources = listOf(
            source(adultUrl, "漫画 · 18+", type = 2),
            source(normalUrl, "漫画", type = 2),
        )
        val visible = AdultContentFilter.filterSources(sources, hideAdult = true)
        val groups = visible.map { it.exploreGroup }.toSet()
        assertFalse("漫画 · 18+" in groups)
        assertTrue("漫画" in groups)
    }

    @Test
    fun `只剩成人源时分组列表为空`() {
        val sources = listOf(source(adultUrl, "视频 · 18+", type = 4))
        val visible = AdultContentFilter.filterSources(sources, hideAdult = true)
        assertTrue(visible.isEmpty())
    }

    private val allGroups = listOf("漫画", "漫画 · 18+", "视频", "视频 · 18+", "小说", "小说 · 18+")

    @Test
    fun `开关关闭时分组名列表原样返回`() {
        assertEquals(allGroups, AdultContentFilter.filterGroups(allGroups, hideAdult = false))
    }

    @Test
    fun `开关开启时分组名列表去掉成人分组`() {
        val result = AdultContentFilter.filterGroups(allGroups, hideAdult = true)
        assertEquals(listOf("漫画", "视频", "小说"), result)
    }

    @Test
    fun `分组名判成人按标记清单，不只看 18+`() {
        val groups = listOf("小说", "短剧", "玄幻", "小说 · 18+", "成人", "🔞 本子", "R18")
        assertEquals(
            listOf("小说", "短剧", "玄幻"),
            AdultContentFilter.filterGroups(groups, hideAdult = true)
        )
    }

    private fun group(id: Long, name: String) = BookGroup(groupId = id, groupName = name)

    @Test
    fun `书架分组里带成人标记的一起隐藏`() {
        val groups = listOf(
            group(BookGroup.IdAll, "全部"),
            group(0b1, "小说"),
            group(0b10, "小说 · 18+"),
            group(0b100, "漫画 · 18+"),
            group(0b1000, "视频 · 18+"),
            group(0b10000, "成人自建分组"),
        )
        assertEquals(
            listOf(BookGroup.IdAll, 0b1L),
            AdultContentFilter.filterShelfGroups(groups, hideAdult = true).map { it.groupId }
        )
    }

    @Test
    fun `开关关闭时书架分组原样返回`() {
        val groups = listOf(group(BookGroup.IdAll, "全部"), group(0b10, "小说 · 18+"))
        assertEquals(groups, AdultContentFilter.filterShelfGroups(groups, hideAdult = false))
    }

    @Test
    fun `全部分组永不隐藏`() {
        // 书架清空后没有标签页可显示，所以「全部」即使名字被判成人也留着
        val groups = listOf(group(BookGroup.IdAll, "18+ 全部"))
        assertEquals(1, AdultContentFilter.filterShelfGroups(groups, hideAdult = true).size)
    }
}
