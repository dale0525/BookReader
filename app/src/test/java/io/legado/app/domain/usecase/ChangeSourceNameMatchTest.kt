package io.legado.app.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeSourceNameMatchTest {

    @Test
    fun `simplified target name matches traditional result name`() {
        assertTrue(
            matchesChangeSourceTarget(
                resultName = "劍來",
                resultAuthor = "烽火戲諸侯",
                name = "剑来",
                author = "烽火戏诸侯",
                checkAuthor = false,
            )
        )
    }

    @Test
    fun `taiwan glyph variants match`() {
        assertTrue(
            matchesChangeSourceTarget(
                resultName = "裡面",
                resultAuthor = "某甲",
                name = "里面",
                author = "某甲",
                checkAuthor = false,
            )
        )
    }

    @Test
    fun `traditional target name matches simplified result name`() {
        assertTrue(
            matchesChangeSourceTarget(
                resultName = "斗破苍穹",
                resultAuthor = "天蚕土豆",
                name = "鬥破蒼穹",
                author = "天蠶土豆",
                checkAuthor = false,
            )
        )
    }

    @Test
    fun `different book names do not match`() {
        assertFalse(
            matchesChangeSourceTarget(
                resultName = "劍來：謫仙臨世",
                resultAuthor = "烽火戲諸侯",
                name = "剑来",
                author = "烽火戏诸侯",
                checkAuthor = false,
            )
        )
    }

    @Test
    fun `author check accepts cross script author`() {
        assertTrue(
            matchesChangeSourceTarget(
                resultName = "劍來",
                resultAuthor = "烽火戲諸侯",
                name = "剑来",
                author = "烽火戏诸侯",
                checkAuthor = true,
            )
        )
    }

    @Test
    fun `author check rejects another author`() {
        assertFalse(
            matchesChangeSourceTarget(
                resultName = "劍來",
                resultAuthor = "會飛翔的企鵝",
                name = "剑来",
                author = "烽火戏诸侯",
                checkAuthor = true,
            )
        )
    }
}
