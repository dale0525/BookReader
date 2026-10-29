package io.legado.app.help.source

import android.app.Application
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 番茄短剧的清晰度选择。
 *
 * 播放接口 `novel/player/video_model/v1` 实测只给四档：360p / 480p / 540p / 720p，
 * 全是 bytevc1（标准 H.265），没有 1080p；`auto_definition` 恒为 `360p`，那是
 * 平台自己的默认值，不能拿它当「最高档」用。
 *
 * 规则一度把默认档位写死成 360p（注释理由是「体积小、解密快」），但解密并不在
 * 列表页发生：`VideoPlayViewModel.loadPlayUrl()` 是点播放之后才调正文规则，
 * 期间 `isLoadingUrl = true`，播放器已经在转圈。也就是说 360p 换来的「快」用户
 * 根本感觉不到，只换来更糊的画面。现在改成未设置 `hgq` 时取接口给出的最高档。
 *
 * 这里冻结两条位置敏感的前提：默认值不能被重新写死、覆盖变量名不能改。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class FanqieDramaQualityTest {

    private lateinit var lib: String

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val json = RuntimeEnvironment.getApplication().assets
            .open("defaultData/bookSources.json").bufferedReader().use { it.readText() }
        val source: BookSource = GSON.fromJsonArray<BookSource>(json).getOrThrow()
            .first { it.bookSourceName == "🍅番茄短剧" }
        lib = source.jsLib ?: error("番茄短剧缺 jsLib")
    }

    /** 默认档位不能再被写死：写死 360 会让用户永远看不到 480p 以上。 */
    @Test
    fun defaultQualityIsNotHardcoded() {
        assertFalse(
            "默认清晰度又被写死了（prefQ='360'）：解密在点播放后才发生，播放器此时已在加载，" +
                "没有理由为起播速度牺牲到 360p",
            Regex("""prefQ\s*=\s*'\d+'""").containsMatchIn(lib)
        )
        // 覆盖变量名是用户唯一能改档位的入口，改名会让既有设置静默失效。
        assertTrue("覆盖变量 hgq 不见了，画质无法再覆盖", lib.contains("JV.get('hgq')"))
    }

    /** 最高档靠「任意 bytevc1 里取 size 最大」实现，这段兜底不能被删掉。 */
    @Test
    fun fallbackPicksLargestStdCodec() {
        val start = lib.indexOf("function FV_playData")
        assertTrue("FV_playData 不见了", start >= 0)
        val body = lib.substring(start, minOf(start + 4000, lib.length))
        assertTrue(
            "缺少「取 size 最大的 bytevc1」兜底，未设置 hgq 时会选不到任何档位",
            body.contains("codec_type||'')!=='bytevc1'") && body.contains("sz>best")
        )
        // bytevc2 是字节私有 bv2C 编码，Android 解码器不支持，必须排除。
        assertTrue("bytevc1 过滤条件变了", body.contains("bytevc1"))
    }

    /** 四档都在接口侧，规则只负责挑，不应自己写死白名单。 */
    @Test
    fun qualityTiersComeFromApiNotHardcodedList() {
        val start = lib.indexOf("function FV_playData")
        val body = lib.substring(start, minOf(start + 4000, lib.length))
        assertEquals(
            "规则里出现了写死的档位清单，接口增减档位时会失效",
            false,
            Regex("""\['360p',\s*'480p'""").containsMatchIn(body)
        )
        assertTrue("按 definition 精确匹配的路径不见了", body.contains("===want"))
    }
}
