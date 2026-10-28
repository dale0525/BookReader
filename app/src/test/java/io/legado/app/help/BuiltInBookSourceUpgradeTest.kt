package io.legado.app.help

import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Looper
import androidx.core.content.edit
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.help.config.LocalConfig
import io.legado.app.support.InMemoryAppDatabaseFixture
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 内置源在「覆盖安装」时必须更新规则。
 *
 * 回归成因：规则导入原本挂在 `LocalConfig.versionCode < AppConst.appInfo.versionCode`
 * 门禁内，而本地构建的 versionCode 恒为 32640（无 COMMIT_NUMBER 时的兜底值），
 * 覆盖安装时两边相等，整段被跳过，老设备上的坏规则永远不换；删除重装才会走到。
 *
 * 所以这里的关键是先把 versionCode 设成「和当前版本相同」——正是覆盖安装的状态，
 * 再要求规则依然被更新。只在全新安装状态下测，就测不到这个缺陷。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BuiltInBookSourceUpgradeTest {

    private lateinit var fixture: InMemoryAppDatabaseFixture

    /** 拆源后内置源就是单站源，这里拿视频分支的采集接口做样本。 */
    private val url = "https://zuidazy.co"

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        installFakeSignature(app)
        fixture = InMemoryAppDatabaseFixture(app)
        // 覆盖安装：app 版本号没变，旧的 bookSourceVersion 计数留在本地
        LocalConfig.edit {
            putLong("appVersionCode", appVersionCode())
            putInt("bookSourceVersion", 1)
        }
        appDb.bookSourceDao.insert(
            BookSource(
                bookSourceUrl = url,
                bookSourceName = "最大资源",
                bookSourceGroup = "我自己的分组",
                bookSourceType = 4,
                enabled = false,
                enabledExplore = false,
                customOrder = 42,
                exploreUrl = "<js>exploreAll(java)</js>",
                jsLib = "function exploreAll(){return []}",
            )
        )
    }

    @After
    fun tearDown() {
        LocalConfig.edit {
            remove("appVersionCode")
            remove("bookSourceVersion")
        }
        fixture.close()
    }

    @Test
    fun upgradeOverExistingInstallRefreshesRules() {
        DefaultData.upVersion()

        // upVersion 内部走 Dispatchers.Main 起协程、Dispatchers.IO 执行；
        // Robolectric 的主 looper 不会自己转，要手动 idle 才能让协程跑起来。
        val after = awaitSource(url) {
            it.jsLib?.contains("return JSON.stringify(out)") == true
        }

        assertNotNull("导入后书源丢失", after)
        assertTrue(
            "覆盖安装后旧规则没被替换，发现页仍会显示 NativeArray@…：jsLib=${after!!.jsLib?.take(40)}",
            after.jsLib?.contains("return JSON.stringify(out)") == true
        )
        // 用户自己起的组名要留着，只有「等于旧内置值」的才允许被刷新
        assertEquals("用户自定义分组被覆盖", "我自己的分组", after.bookSourceGroup)
        assertEquals("用户排序被覆盖", 42, after.customOrder)
        assertEquals("用户停用状态被覆盖", false, after.enabled)
        assertEquals("用户发现开关被覆盖", false, after.enabledExplore)
        assertEquals("bookSourceVersion 未推进", 19, LocalConfig.getInt("bookSourceVersion", 0))
    }

    /** 拆源后新站必须一起补进来，老用户升级才能拿到全部单站源。 */
    @Test
    fun upgradeInstallsEverySplitSource() {
        LocalConfig.edit { putInt("bookSourceVersion", 12) }
        DefaultData.upVersion()
        // 等 asset 里最后一条落库：导入是按 asset 顺序逐条写的，
        // 拿靠前的源当信号会在还没写完时就去比对。
        awaitSource("https://www.cdngwc.cc") { true }

        val assetUrls = RuntimeEnvironment.getApplication().assets
            .open("defaultData/bookSources.json").bufferedReader().use { it.readText() }
            .let { GSON.fromJsonArray<BookSource>(it).getOrThrow() }
            .map { it.bookSourceUrl }
        val installed = appDb.bookSourceDao.all.map { it.bookSourceUrl }
        assertTrue("看书网没装进来", installed.contains("https://www.kanshuw.com"))
        assertTrue("台湾小说网没装进来", installed.contains("https://www.twkan.cc"))
        assertEquals(
            "拆源后的单站源与 asset 不一致: ${installed.filterNot { it in assetUrls }}",
            assetUrls.toSet(),
            installed.toSet(),
        )
    }

    /**
     * 「消消乐听书」已停止分发，但老设备的书源表里还留着这条记录。
     * 只从 asset 里删掉只影响全新安装，覆盖安装的用户仍会看到这个已下线的源，
     * 所以升级时必须按 url 把已有条目清掉。
     */
    @Test
    fun upgradeRemovesRetiredBuiltInSource() {
        LocalConfig.edit { putInt("bookSourceVersion", 11) }
        val retiredUrl = "https://www.kaixin7days.com"
        appDb.bookSourceDao.insert(
            BookSource(
                bookSourceUrl = retiredUrl,
                bookSourceName = "消消乐听书",
                bookSourceType = 1,
            )
        )

        DefaultData.upVersion()
        awaitSource(retiredUrl) { false }

        assertNull(
            "已下线的内置书源在覆盖安装后仍然存在",
            appDb.bookSourceDao.getBookSource(retiredUrl),
        )
        assertEquals("bookSourceVersion 未推进", 19, LocalConfig.getInt("bookSourceVersion", 0))
    }

    /**
     * 聚合源已拆成单站源，库里不该再留着它们。
     *
     * `agg.example.com` 是 `example.com` 下的占位域名，从来没有真实内容，
     * 书架上的书不可能靠它读到正文，留着只会让书源列表里多出 6 条死条目。
     */
    @Test
    fun upgradeDeletesRetiredAggregateSources() {
        val aggUrls = listOf(
            "https://agg.example.com/novel-normal",
            "https://agg.example.com/manga-normal",
            "https://agg.example.com/video-normal",
            "https://agg.example.com/novel-adult",
            "https://agg.example.com/manga-adult",
            "https://agg.example.com/video-adult",
        )
        aggUrls.forEach { aggUrl ->
            appDb.bookSourceDao.insert(
                BookSource(
                    bookSourceUrl = aggUrl,
                    bookSourceName = "聚合源",
                    bookSourceType = 0,
                    enabled = true,
                    enabledExplore = true,
                    exploreUrl = "<js>exploreAll(java)</js>",
                    jsLib = "var SITES=[];function exploreAll(){return '[]'}",
                )
            )
        }

        DefaultData.upVersion()
        awaitSource(aggUrls.last()) { false }

        aggUrls.forEach { aggUrl ->
            assertNull(
                "已下线的聚合源在覆盖安装后仍然存在: $aggUrl",
                appDb.bookSourceDao.getBookSource(aggUrl),
            )
        }
    }

    /**
     * 远端清单里还带着聚合源时，不能把它们重新写回库里。
     *
     * 真实成因：`main` 分支上的 `bookSources.json` 还是拆源前的 6 聚合源版本，
     * 覆盖安装先删掉聚合源，随后 `RemoteBookSources.refresh()` 拉到那份旧清单，
     * 一写就复活。删除只做一次，导入侧必须按 url 滤掉。
     */
    @Test
    fun importIgnoresRetiredAggregateSources() = runBlocking {
        DefaultData.importBookSources(
            listOf(
                BookSource(
                    bookSourceUrl = "https://agg.example.com/novel-normal",
                    bookSourceName = "小说·常规",
                    bookSourceType = 0,
                ),
                BookSource(
                    bookSourceUrl = "https://www.twkan.cc",
                    bookSourceName = "台湾小说网",
                    bookSourceType = 0,
                ),
            )
        )

        assertNull(
            "聚合源被远端清单写回库里了",
            appDb.bookSourceDao.getBookSource("https://agg.example.com/novel-normal"),
        )
        assertNotNull(
            "正常源也被一起滤掉了",
            appDb.bookSourceDao.getBookSource("https://www.twkan.cc"),
        )
    }

    /**
     * 远端刷新必须和 asset 导入一样能删掉已下线的源。
     *
     * 真实成因：删除原本只写在 `importDefaultBookSources()` 里，远端走的是
     * `importBookSources()`，只对传入清单做 filterNot、不碰库里已有的行。
     * 于是 `bookSourceVersion` 门禁已推进的设备，远端更新只能加新源、删不掉旧源：
     * 365小说网、玩文学小说网、两个 `/adult` 孪生源会永久留在书源表里。
     */
    @Test
    fun importRemovesRetiredSourcesAlreadyInDb() = runBlocking {
        val retiredUrl = "http://www.shukuge.com"
        appDb.bookSourceDao.insert(
            BookSource(
                bookSourceUrl = retiredUrl,
                bookSourceName = "365小说网",
                bookSourceType = 0,
            )
        )

        DefaultData.importBookSources(
            listOf(
                BookSource(
                    bookSourceUrl = "https://www.twkan.cc",
                    bookSourceName = "台湾小说网",
                    bookSourceType = 0,
                ),
            )
        )

        assertNull(
            "已下线的源没被远端刷新删掉，书源列表里会一直留着它",
            appDb.bookSourceDao.getBookSource(retiredUrl),
        )
        assertNotNull(
            "正常源也被一起删掉了",
            appDb.bookSourceDao.getBookSource("https://www.twkan.cc"),
        )
    }

    /**
     * 内置源的分组名改了，老用户也必须跟着变。
     *
     * 真实成因：`importBookSources()` 无条件保留 `existing.bookSourceGroup`，于是
     * 0.2.0 里 `黄果短剧` 停在 `视频`、asset 已是 `视频 · 18+`，升级后它不带 18+ 标记，
     * 「隐藏成人内容」对它不生效——它会继续显示在普通「视频」分组里。
     *
     * 判据是「库里存的组名是否等于旧版内置值」：相等说明用户没动过，可以刷新；
     * 不相等说明是用户自己改的，必须保留。
     */
    @Test
    fun upgradeRefreshesBuiltInGroupNameWhenUserDidNotChangeIt() {
        // 覆盖安装：库里是 0.2.0 的旧组名，与旧内置值一致
        appDb.bookSourceDao.insert(
            BookSource(
                bookSourceUrl = "https://huangguoai.com",
                bookSourceName = "黄果短剧",
                bookSourceGroup = "视频",
                bookSourceType = 4,
            )
        )

        DefaultData.upVersion()
        val after = awaitSource("https://huangguoai.com") { it.bookSourceGroup == "视频 · 18+" }

        assertEquals(
            "内置源的分组名没随 asset 刷新，隐藏成人内容对它不生效",
            "视频 · 18+",
            after?.bookSourceGroup,
        )
    }

    /**
     * 八叉书库按「零命中」下线过、随后恢复并改判到成人档。
     *
     * 老设备的库里可能还留着它（停在没有执行下线删除的那个版本），组名是旧内置值
     * `小说`；升级时必须跟着 asset 变成 `小说 · 18+`，否则它不带 18+ 标记，
     * 「隐藏成人内容」对它不生效。
     */
    @Test
    fun restoredSourceMovesToAdultGroup() {
        appDb.bookSourceDao.insert(
            BookSource(
                bookSourceUrl = "https://bcshuku.com",
                bookSourceName = "八叉书库",
                bookSourceGroup = "小说",
                bookSourceType = 0,
            )
        )

        DefaultData.upVersion()
        val after = awaitSource("https://bcshuku.com") { it.bookSourceGroup == "小说 · 18+" }

        assertEquals(
            "恢复的八叉书库没进成人档，隐藏成人内容对它不生效",
            "小说 · 18+",
            after?.bookSourceGroup,
        )
    }

    /** 用户自己改过的组名不能被内置值覆盖，否则等于悄悄重置用户的整理结果。 */
    @Test
    fun upgradeKeepsGroupNameTheUserChanged() {
        appDb.bookSourceDao.insert(
            BookSource(
                bookSourceUrl = "https://huangguoai.com",
                bookSourceName = "黄果短剧",
                bookSourceGroup = "我自己分的类",
                bookSourceType = 4,
            )
        )

        DefaultData.upVersion()
        awaitSource("https://huangguoai.com") { it.bookSourceGroup == "视频 · 18+" }

        assertEquals(
            "用户自己改的分组名被内置值覆盖了",
            "我自己分的类",
            appDb.bookSourceDao.getBookSource("https://huangguoai.com")?.bookSourceGroup,
        )
    }

    /** upVersion 走协程，Robolectric 的主 looper 不会自己转，要轮询直到条件成立。 */
    private fun awaitSource(url: String, until: (BookSource) -> Boolean): BookSource? {
        val deadline = System.currentTimeMillis() + 10_000
        var current = appDb.bookSourceDao.getBookSource(url)
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            current = appDb.bookSourceDao.getBookSource(url)
            if (current != null && until(current)) break
            Thread.sleep(50)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return appDb.bookSourceDao.getBookSource(url)
    }

    /** AppConst 静态初始化要读签名，Robolectric 默认不给，补一个假的让它能初始化。 */
    private fun installFakeSignature(app: Application) {
        @Suppress("DEPRECATION")
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES)
        info.signatures = arrayOf(Signature("302a300506032b6570032100"))
        shadowOf(app.packageManager).installPackage(info)
    }

    private fun appVersionCode(): Long {
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        return info.longVersionCode
    }
}
