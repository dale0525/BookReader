package io.legado.app.help.source

import io.legado.app.data.entities.ExploreSourceSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成人书源的判定口径。
 *
 * 只有一条可靠依据：`bookSourceGroup` 里带 [AdultContentPolicy.MARKERS] 里的任一项。
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
    fun `成人 18加与禁止符号都判为成人`() {
        assertTrue(AdultContentPolicy.isAdult(source("成人")))
        assertTrue(AdultContentPolicy.isAdult(source("成人漫画")))
        assertTrue(AdultContentPolicy.isAdult(source("🔞专區")))
        assertTrue(AdultContentPolicy.isAdult(source("R18")))
        assertTrue(AdultContentPolicy.isAdult(source("r-18 同人")))
        assertTrue(AdultContentPolicy.isAdult(source("小说,成人")))
    }

    @Test
    fun `裸 18 与年份不判为成人`() {
        // 分组名里写年份、写字数是常见的无害写法，裸 18 会把它整组藏掉
        assertFalse(AdultContentPolicy.isAdult(source("2018 书单")))
        assertFalse(AdultContentPolicy.isAdult(source("18 禁")))
    }

    @Test
    fun `SQL 片段覆盖全部标记`() {
        // 三处 SQL 都靠 AdultContentPolicy.SQL_GROUP_MATCHES_ADULT 匹配分组名，它由
        // MARKERS 拼出来。这里核对每个标记都进了片段，防止只加 Kotlin 侧忘了 SQL 侧，
        // 结果是「书架藏了、发现页还在」这种半藏状态。
        AdultContentPolicy.MARKERS.forEach { marker ->
            assertTrue(
                "SQL 片段缺少标记 $marker",
                AdultContentPolicy.SQL_GROUP_MATCHES_ADULT.contains("'$marker'"),
            )
        }
    }
}
