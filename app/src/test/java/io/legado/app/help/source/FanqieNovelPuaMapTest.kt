package io.legado.app.help.source

import android.app.Application
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 番茄小说的正文有约一半的字被换成私用区码位，靠一个哈希固定的字体渲染。
 * 客户端拿不到字体，只能靠 jsLib 里那张按码位顺序排列的映射表逐字还原。
 *
 * 这张表是**位置敏感**的：第 i 个字符就是码位 `FQ_MAP0 + i` 的真字。少一个占位、
 * 多一个字符，后面全部错位——而且不会抛异常，只会安静地把整章正文替换成别的字。
 * 实测踩过一次：字体在 e3e8..e55b 之间有 10 个码位根本没有字形，我按「只收有字形的
 * 码位」拼表，结果 6 处之后全错位，正文成了「往里P旧h钨丝灯」。
 *
 * 所以这里冻结三条：表长与码位跨度一致、空位必须留空、首尾码位与真实字体对齐。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class FanqieNovelPuaMapTest {

    private lateinit var source: BookSource

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val json = RuntimeEnvironment.getApplication().assets
            .open("defaultData/bookSources.json").bufferedReader().use { it.readText() }
        source = GSON.fromJsonArray<BookSource>(json).getOrThrow()
            .first { it.bookSourceName == "🍅番茄小说" }
    }

    /** 表长必须等于码位跨度，否则「第 i 个字符」这个前提就不成立了。 */
    @Test
    fun mapCoversContiguousCodepointRange() {
        val span = (source.evalJS("FQ_MAP.length") as Number).toInt()
        val lo = (source.evalJS("FQ_MAP0") as Number).toInt()
        // 字体 cmap 的实际范围：e3e8..e55b，共 372 个码位。
        assertEquals("映射表起始码位变了，字体可能已更换", 0xe3e8, lo)
        assertEquals("映射表长度与字体码位跨度不一致，逐字还原会整体错位", 372, span)
    }

    /**
     * 字体在 10 个码位上没有字形，这些位置在表里必须是空字符占位。
     * 一旦有人「清理空位」把表压紧，后面全部错位。
     */
    @Test
    fun gapCodepointsStayAsPlaceholders() {
        val gaps = listOf(
            0xe3f6, 0xe3fe, 0xe3ff, 0xe419, 0xe45b,
            0xe4b6, 0xe4d4, 0xe4d8, 0xe51a, 0xe51f,
        )
        gaps.forEach { cp ->
            val ch = source.evalJS("FQ_MAP.charAt(${cp - 0xe3e8})").toString()
            assertEquals(
                "码位 ${Integer.toHexString(cp)} 在字体里没有字形，表里必须留空占位",
                1, ch.length
            )
            assertEquals(
                "码位 ${Integer.toHexString(cp)} 的占位被填了字，会导致后面全部错位",
                0, ch[0].code
            )
        }
    }

    /**
     * 首尾码位必须解成字体里真实存在的字：表整体偏移或截断时这里最先暴露。
     * e3e8 对应 ASCII 'D'（字体把常用字符也一起换掉了），e55b 对应「任」。
     */
    @Test
    fun headAndTailResolveToRealCharacters() {
        assertEquals('D', (source.evalJS("FQ_dec(String.fromCharCode(0xe3e8))") as String)[0])
        assertEquals('任', (source.evalJS("FQ_dec(String.fromCharCode(0xe55b))") as String)[0])
    }

    /** 不在私用区的字符必须原样保留，不能被映射表碰到。 */
    @Test
    fun normalCharactersPassThrough() {
        val plain = "第1章 醒了，但没完全醒"
        assertEquals(plain, source.evalJS("FQ_dec('$plain')") as String)
    }

    /** 实测样本：整句必须逐字还原成通顺正文。 */
    @Test
    fun realSentenceDecodesToReadableText() {
        // 取自真实章节的首句：混淆只替换部分字，未被替换的字原样夹在中间，
        // 所以这条同时验证「替换」与「放行」两种分支。
        val encoded = "\ue423乾皇朝，疆域亿\ue466\ue44d。"
        val decoded = source.evalJS("FQ_dec('$encoded')") as String
        assertEquals("大乾皇朝，疆域亿万里。", decoded)
        assertTrue(
            "还原后仍残留私用区字符: $decoded",
            decoded.none { it.code in 0xe000..0xf8ff },
        )
    }
}
