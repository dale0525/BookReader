package io.legado.app.help.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 远端内置书的响应门禁。
 *
 * 这段逻辑决定了「远端返回什么才允许覆盖本地书源表」，而远端是唯一一处
 * 用户无法预检、出错就直接落到所有人设备上的路径。所以每一条拒绝分支都要守住。
 */
class RemoteBookSourcesTest {

    private val valid = """[{"bookSourceUrl":"https://example.com","bookSourceName":"示例"}]"""

    @Test
    fun `parses a normal payload`() {
        val sources = parseRemoteSources(200, valid)

        assertEquals(1, sources?.size)
        assertEquals("https://example.com", sources?.first()?.bookSourceUrl)
    }

    @Test
    fun `304 means unchanged`() {
        assertNull(parseRemoteSources(304, null))
    }

    @Test
    fun `error responses never reach the database`() {
        assertNull(parseRemoteSources(404, valid))
        assertNull(parseRemoteSources(500, valid))
    }

    /**
     * CDN 和 GitHub raw 出错时会回一页 HTML。把它当成功会解析出空列表，
     * 而空列表写进库等于把用户的全部内置源删掉。
     */
    @Test
    fun `html error page is rejected`() {
        assertNull(parseRemoteSources(200, "<html><body>502 Bad Gateway</body></html>"))
    }

    @Test
    fun `empty array is rejected`() {
        assertNull(parseRemoteSources(200, "[]"))
    }

    @Test
    fun `blank body is rejected`() {
        assertNull(parseRemoteSources(200, ""))
        assertNull(parseRemoteSources(200, null))
    }

    @Test
    fun `payload carrying the real rules is accepted`() {
        val body = """[{"bookSourceUrl":"https://example.com","bookSourceName":"示例",""" +
                """"ruleSearch":{"bookList":"<js>1</js>"}}]"""

        val sources = parseRemoteSources(200, body)

        assertTrue(sources.orEmpty().single().ruleSearch?.bookList?.contains("<js>") == true)
    }
}
