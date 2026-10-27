package io.legado.app.help.storage

import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.Signature
import androidx.core.content.edit
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.LocalConfig
import io.legado.app.support.InMemoryAppDatabaseFixture
import io.legado.app.utils.GSON
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
import java.io.File
import java.nio.file.Files

/**
 * 恢复备份不能把内置书源退回备份时的旧规则。
 *
 * 冲突成因：内置源规则只在 `LocalConfig.needUpBookSources` 为真时更新，而
 * `bookSourceVersion` 存在 `SharedPreferences("local")` 里；备份的 `config.xml`
 * 只导 `AppConfigStore`（DataStore），这个计数既不进备份也不被恢复。升级门禁早已
 * 推进到最新，恢复时按 url 整行 REPLACE 又把旧规则写回去，之后没有任何路径会再
 * 覆盖它——发现页会一直停在备份那版。
 *
 * 所以这里直接跑真实的 `Restore.restoreBookSources()`，喂一份「备份里的旧书源」，
 * 断言落库后的规则必须是包内版本。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class RestoreBuiltInBookSourceTest {

    private lateinit var fixture: InMemoryAppDatabaseFixture
    private lateinit var backupDir: File

    private val builtInUrl = "https://zuidazy.co"

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        installFakeSignature(app)
        AppConfigStore.init(app)
        fixture = InMemoryAppDatabaseFixture(app)
        backupDir = Files.createTempDirectory("restore-src").toFile()
        // 升级门禁已推进到最新：模拟「装过新版、版本计数早已写满」的设备。
        LocalConfig.edit {
            putLong("appVersionCode", appVersionCode())
            putInt("bookSourceVersion", 16)
        }
    }

    @After
    fun tearDown() {
        LocalConfig.edit {
            remove("appVersionCode")
            remove("bookSourceVersion")
        }
        backupDir.deleteRecursively()
        fixture.close()
    }

    /** 备份里的内置源是旧规则，恢复后必须换成包内规则，用户的开关/分组/排序仍保留。 */
    @Test
    fun restoreKeepsBundledRulesInsteadOfBackupCopy() = runBlocking {
        val staleJsLib = "function exploreAll(){return 'stale-from-backup'}"
        writeBackupSourceFile(
            BookSource(
                bookSourceUrl = builtInUrl,
                bookSourceName = "最大资源",
                bookSourceGroup = "我自己的分组",
                bookSourceType = 4,
                enabled = false,
                enabledExplore = false,
                customOrder = 42,
                exploreUrl = "<js>exploreAll(java)</js>",
                jsLib = staleJsLib,
            )
        )

        Restore.restoreBookSources(backupDir.absolutePath)

        val after = appDb.bookSourceDao.getBookSource(builtInUrl)
        assertNotNull("恢复后内置书源丢失", after)
        assertNotEquals("内置书源规则仍停在备份里的旧版本", staleJsLib, after!!.jsLib)
        assertTrue(
            "内置书源规则没有换成包内版本：jsLib=${after.jsLib?.take(40)}",
            after.jsLib?.contains("return JSON.stringify(out)") == true,
        )
        assertEquals("用户分组被覆盖", "我自己的分组", after.bookSourceGroup)
        assertEquals("用户排序被覆盖", 42, after.customOrder)
        assertEquals("用户停用状态被覆盖", false, after.enabled)
        assertEquals("用户发现开关被覆盖", false, after.enabledExplore)
    }

    /** 备份里还带着已停止分发的内置源时，恢复后要按 url 清掉，不能让它复活。 */
    @Test
    fun restoreRemovesRetiredBuiltInSource() = runBlocking {
        val retiredUrl = "https://www.kaixin7days.com"
        writeBackupSourceFile(
            BookSource(
                bookSourceUrl = retiredUrl,
                bookSourceName = "消消乐听书",
                bookSourceType = 1,
            )
        )

        Restore.restoreBookSources(backupDir.absolutePath)

        assertNull(
            "已下线的内置书源被备份带回来了",
            appDb.bookSourceDao.getBookSource(retiredUrl),
        )
    }

    private fun writeBackupSourceFile(vararg sources: BookSource) {
        File(backupDir, "bookSource.json").writeText(GSON.toJson(sources.toList()))
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
