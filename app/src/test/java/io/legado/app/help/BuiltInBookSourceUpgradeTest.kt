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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private val url = "https://agg.example.com/novel-normal"

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
                bookSourceName = "小说·常规",
                bookSourceGroup = "我自己的分组",
                bookSourceType = 0,
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
        val deadline = System.currentTimeMillis() + 10_000
        var after: BookSource? = null
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            after = appDb.bookSourceDao.getBookSource(url)
            if (after?.jsLib?.contains("return JSON.stringify(out)") == true) break
            Thread.sleep(50)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull("导入后书源丢失", after)
        assertTrue(
            "覆盖安装后旧规则没被替换，发现页仍会显示 NativeArray@…：jsLib=${after!!.jsLib?.take(40)}",
            after.jsLib?.contains("return JSON.stringify(out)") == true
        )
        assertEquals("用户分组被覆盖", "我自己的分组", after.bookSourceGroup)
        assertEquals("用户排序被覆盖", 42, after.customOrder)
        assertEquals("用户停用状态被覆盖", false, after.enabled)
        assertEquals("用户发现开关被覆盖", false, after.enabledExplore)
        assertEquals("bookSourceVersion 未推进", 7, LocalConfig.getInt("bookSourceVersion", 0))
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
