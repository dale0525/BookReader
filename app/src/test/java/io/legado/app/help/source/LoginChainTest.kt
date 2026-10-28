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
 * 复刻客户端登录时的真实拼接：
 *   evalJS("<loginUrl 去掉 @js:>\n" + "if(typeof login=='function'){login.apply(this)} ...")
 * 并用探针替换掉真正发请求那一段，只验证「能取到登录信息」这一环。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LoginChainTest {
    private lateinit var source: BookSource

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val json = RuntimeEnvironment.getApplication().assets
            .open("defaultData/bookSources.json").bufferedReader().use { it.readText() }
        source = GSON.fromJsonArray<BookSource>(json).getOrThrow()
            .first { it.bookSourceName == "UAA小说" }
    }

    @Test
    fun loginUrlDefinesLoginThatReachesSource() {
        val loginJs = source.getLoginJs() ?: error("loginUrl 为空")
        val tail = "if (typeof login=='function'){ login.apply(this); } else { throw('Function login not implements!!!') }"
        // getLoginInfoMap 依赖 Koin 的 CacheManager，Robolectric 起不来，
        // 所以只探「闭包能否拿到 source 句柄」这一环——那正是之前炸掉的地方。
        val probeJs = loginJs.replace(
            "var m = source.getLoginInfoMap();",
            "return 'REACHED:source=' + (typeof source);"
        )
        assertTrue("探针没替换成功", probeJs != loginJs)
        val out = source.evalJS("$probeJs\n$tail").toString()
        assertEquals(
            "loginUrl 里的 login() 取不到 source 句柄，真机登录会抛 EcmaError",
            "REACHED:source=object",
            out,
        )
    }
}
