package io.legado.app.help

import android.app.Application
import io.legado.app.web.utils.AssetsWeb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DefaultAssetPathsTest {
    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun bundledDefaultsLoadWithoutHostFilesystemSeparators() {
        assertTrue(DefaultData.httpTTS.isNotEmpty())
        assertTrue(DefaultData.readConfigs.isNotEmpty())
        assertTrue(DefaultData.txtTocRules.isNotEmpty())
        assertTrue(DefaultData.themeConfigs.isNotEmpty())
        assertTrue(DefaultData.rssSources.isEmpty())
        assertTrue(DefaultData.bookSources.isNotEmpty())
        assertNotNull(DefaultData.coverRule)
        assertTrue(DefaultData.dictRules.isNotEmpty())
        assertTrue(DefaultData.keyboardAssists.isNotEmpty())
        assertTrue(DirectLinkUpload.defaultRules.isNotEmpty())
    }

    @Test
    fun webAssetsAcceptBothRelativeAndRequestPaths() {
        val assets = AssetsWeb("defaultData")
        val expected = RuntimeEnvironment.getApplication().assets
            .open("defaultData/keyboardAssists.json").bufferedReader().use { it.readText() }
        for (path in listOf("keyboardAssists.json", "/keyboardAssists.json", "//keyboardAssists.json")) {
            val actual = assets.getInputStream(path)?.bufferedReader()?.use { it.readText() }
            assertEquals(path, expected, actual)
        }
        assertNull(assets.getInputStream("missing.json"))
    }

    /**
     * 内置样式默认值取自用户的既有配置，六份预设与共享排版都要能被解析成完整配置。
     * 少了共享排版那一份时，`readShareConfig` 会退到「预设5」，两者排版就分叉了。
     */
    @Test
    fun bundledStyleDefaultsCarrySixPresetsAndShareConfig() {
        assertEquals(6, DefaultData.readConfigs.size)
        assertEquals(
            listOf("纸墨", "预设1", "预设2", "预设3", "预设4", "预设5"),
            DefaultData.readConfigs.map { it.name },
        )
        assertNotNull(DefaultData.shareReadConfig)
        assertEquals(4, DefaultData.themeConfigs.size)
    }

    /**
     * 共享排版里的字体路径是备份设备上的绝对路径，换机器或换包名后必然不存在。
     * 留着会让阅读器每次排版都去打开一个不存在的文件。
     */
    @Test
    fun bundledShareConfigHasNoDeviceAbsoluteFontPath() {
        val share = requireNotNull(DefaultData.shareReadConfig)
        assertTrue(share.textFont.isEmpty())
    }
}
