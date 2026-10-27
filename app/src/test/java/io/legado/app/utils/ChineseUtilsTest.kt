package io.legado.app.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 简繁归一比对：搜索路径不依赖阅读界面的转换设置。
 *
 * 排除词表（[ChineseUtils.fixT2sDict]）会改变「先繁后简」落到哪个形态，
 * 但必须两侧一致——否则搜不搜得到取决于用户在阅读设置里选了什么。
 */
class ChineseUtilsTest {

    @Test
    fun `same text across scripts`() {
        assertTrue(ChineseUtils.sameText("剑来", "劍來"))
        assertTrue(ChineseUtils.sameText("剑来", "剑来"))
        assertTrue(ChineseUtils.sameText("斗破苍穹", "鬥破蒼穹"))
    }

    @Test
    fun `taiwan glyph variants collapse too`() {
        assertTrue(ChineseUtils.sameText("里面", "裡面"))
        assertTrue(ChineseUtils.sameText("里面", "裏面"))
        assertTrue(ChineseUtils.sameText("众生", "眾生"))
        assertTrue(ChineseUtils.sameText("众生", "衆生"))
    }

    @Test
    fun `different titles stay different`() {
        assertFalse(ChineseUtils.sameText("剑来", "劍來：謫仙臨世"))
        assertFalse(ChineseUtils.sameText("剑来", "剑仙"))
    }

    @Test
    fun `result does not depend on the exclude dict being loaded`() {
        val before = ChineseUtils.sameText("规则", "規則")
        ChineseUtils.fixT2sDict()
        val after = ChineseUtils.sameText("规则", "規則")

        assertTrue(before)
        assertTrue(after)
    }

    @Test
    fun `author containment works across scripts`() {
        assertTrue(ChineseUtils.containsText("烽火戲諸侯", "烽火戏诸侯"))
        assertFalse(ChineseUtils.containsText("會飛翔的企鵝", "烽火戏诸侯"))
    }

    /**
     * 字形候选是查库的唯一入口，漏一种写法就等于那条缓存结果搜不到。
     */
    @Test
    fun `variants cover every common traditional spelling`() {
        listOf(
            "剑来" to "劍來",
            "里面" to "裡面",
            "里面" to "裏面",
            "众生" to "眾生",
            "众生" to "衆生",
            "斗破苍穹" to "鬥破蒼穹",
            "轮回" to "輪迴",
            "诡秘之主" to "詭祕之主",
            "西游记" to "西遊記",
            "水浒传" to "水滸傳",
            "昆仑" to "崑崙",
            "活着" to "活著",
        ).forEach { (simplified, traditional) ->
            assertTrue(
                "$simplified 的候选字形里缺 $traditional",
                ChineseUtils.variants(simplified).contains(traditional),
            )
        }
    }
}
