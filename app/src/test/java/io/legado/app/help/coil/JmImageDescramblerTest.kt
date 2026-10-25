package io.legado.app.help.coil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JmImageDescramblerTest {

    @Test
    fun `segments follow the official get_num mapping`() {
        // 与官方 jquery.photo 的 get_num 逐值对齐：md5("$aid$page") 末位字符码
        // 按年代取模，再映射到 2..20 的偶数。这里锁住映射本身，
        // 避免日后有人把 switch 表简化成乘 2 时漏掉取模分支。
        assertEquals(12, JmImageDescrambler.segments(1485646, "00001"))
        assertEquals(4, JmImageDescrambler.segments(1485646, "00002"))
        assertEquals(10, JmImageDescrambler.segments(1485646, "00003"))
        assertEquals(12, JmImageDescrambler.segments(1485645, "00001"))
        assertEquals(2, JmImageDescrambler.segments(1485645, "00002"))
    }

    @Test
    fun `segments stay within the two to twenty even range`() {
        for (aid in listOf(220980, 268850, 421925, 421926, 999999, 1485646)) {
            for (i in 1..40) {
                val num = JmImageDescrambler.segments(aid, "%05d".format(i))
                assertTrue("aid=$aid i=$i num=$num", num in 2..20)
                assertEquals("num 必须是偶数", 0, num % 2)
            }
        }
    }

    @Test
    fun `only jm photo urls below the scramble threshold are left alone`() {
        val scrambled = "https://cdn.example.cc/media/photos/1485646/00001.webp?v=1792"
        assertTrue(JmImageDescrambler.needsDescramble(scrambled))

        // 低于阈值的老章节服务端没有切图，再还原会把好图打乱。
        val plain = "https://cdn.example.cc/media/photos/100000/00001.webp"
        assertFalse(JmImageDescrambler.needsDescramble(plain))

        // 其他站点的图片一律不碰。
        assertFalse(JmImageDescrambler.needsDescramble("https://a.example.com/cover/1.jpg"))
        assertFalse(JmImageDescrambler.needsDescramble("https://cdn.example.cc/media/albums/1.jpg"))
    }
}
