package io.legado.app.model.webBook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 精准搜索的入选判据。
 *
 * 导书单（[io.legado.app.domain.usecase.ImportBookshelfUseCase]）与换源、自动换源都走
 * [WebBook.preciseSearchAwait]，它按书名与作者判定「这是不是同一本书」。台湾小说网这类
 * 站点对简体检索词返回繁体书名与繁体作者，精确相等会把整条结果丢掉，
 * 表现为「站内搜得到、导书单却说找不到」。
 */
class PreciseSearchMatchTest {

    @Test
    fun `traditional result matches simplified target`() {
        assertTrue(
            WebBook.matchesPreciseSearchTarget(
                resultName = "誰讓他修仙的！",
                resultAuthor = "最白的烏鴉",
                name = "谁让他修仙的！",
                author = "最白的乌鸦",
            )
        )
    }

    @Test
    fun `same glyphs still match`() {
        assertTrue(
            WebBook.matchesPreciseSearchTarget(
                resultName = "剑来",
                resultAuthor = "烽火戏诸侯",
                name = "剑来",
                author = "烽火戏诸侯",
            )
        )
    }

    @Test
    fun `different title is rejected`() {
        assertFalse(
            WebBook.matchesPreciseSearchTarget(
                resultName = "誰讓他修仙的！外傳",
                resultAuthor = "最白的烏鴉",
                name = "谁让他修仙的！",
                author = "最白的乌鸦",
            )
        )
    }

    @Test
    fun `different author is rejected`() {
        assertFalse(
            WebBook.matchesPreciseSearchTarget(
                resultName = "誰讓他修仙的！",
                resultAuthor = "別人",
                name = "谁让他修仙的！",
                author = "最白的乌鸦",
            )
        )
    }
}
