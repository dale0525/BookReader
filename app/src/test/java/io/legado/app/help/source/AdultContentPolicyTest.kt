package io.legado.app.help.source

import io.legado.app.data.entities.ExploreSourceSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成人书源的判定口径。
 *
 * 只有一条可靠依据：`bookSourceGroup` 里带 `18+`。
 *
 * 曾经考虑过复用 `assets/18PlusList.txt`（208 个 base64 域名），实测不可行：
 * 内置的 10 个成人源一个都不在其中，而且 `SourceHelp.insertBookSource()` 对命中该清单的
 * 源是**直接拒绝导入**的——它的语义是"禁入黑名单"，不是"成人内容标记"。拿它做展示过滤
 * 既覆盖不到内置源，也和它的原意错位。
 */
class AdultContentPolicyTest {

    private fun source(group: String?) = ExploreSourceSummary(
        bookSourceUrl = "https://example.com",
        bookSourceName = "测试源",
        bookSourceGroup = group,
        bookSourceType = 0,
    )

    @Test
    fun `分组名带 18+ 的书源判为成人`() {
        assertTrue(AdultContentPolicy.isAdult(source("小说 · 18+")))
        assertTrue(AdultContentPolicy.isAdult(source("漫画 · 18+")))
        assertTrue(AdultContentPolicy.isAdult(source("视频 · 18+")))
    }

    @Test
    fun `常规分组不判为成人`() {
        assertFalse(AdultContentPolicy.isAdult(source("小说")))
        assertFalse(AdultContentPolicy.isAdult(source("漫画")))
        assertFalse(AdultContentPolicy.isAdult(source("视频")))
        assertFalse(AdultContentPolicy.isAdult(source(null)))
        assertFalse(AdultContentPolicy.isAdult(source("")))
    }

    @Test
    fun `分组名写错但带 18+ 仍判为成人`() {
        // 用户自建源的分组名不受内置源那六档约束，只要写了 18+ 就该被认出来
        assertTrue(AdultContentPolicy.isAdult(source("成人 18+")))
        assertTrue(AdultContentPolicy.isAdult(source("视频·18+")))
        assertTrue(AdultContentPolicy.isAdult(source("R18+内容")))
    }

    @Test
    fun `多值分组里任一值带 18+ 就判为成人`() {
        assertTrue(AdultContentPolicy.isAdult(source("小说,小说 · 18+")))
        assertTrue(AdultContentPolicy.isAdult(source("漫画；漫画 · 18+")))
    }

    @Test
    fun `判定口径与 SQL 侧 instr 一致`() {
        // BookSourceDao.flowAdultSourceUrls() 用 instr(bookSourceGroup, '18+') > 0，
        // 两边必须等价：书架上按 origin 过滤的书，和这里判成人的源是同一批。
        val groups = listOf("小说 · 18+", "小说", "", null, "R18+", "18")
        groups.forEach { group ->
            assertTrue(
                "分组 $group 的两侧判定不一致",
                AdultContentPolicy.containsMarker(group) == (group?.contains("18+") == true),
            )
        }
    }
}
