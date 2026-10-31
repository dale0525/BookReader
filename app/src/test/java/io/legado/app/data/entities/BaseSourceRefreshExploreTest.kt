package io.legado.app.data.entities

import android.app.Application
import android.os.Looper
import androidx.lifecycle.Observer
import io.legado.app.constant.EventBus
import io.legado.app.utils.eventObservable
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 登录后「我的收藏」要当场出现，靠的是 [BaseSource.refreshExplore] 清完缓存再广播一次。
 * 这里只冻结「广播」这一步：清缓存本身是 ACache 的普通删除，发现页是否重算由
 * `ExploreMainViewModelTest` 覆盖，两段拼起来才是完整链路。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BaseSourceRefreshExploreTest {

    @Test
    fun `refreshExplore broadcasts the source url so open explore pages reload`() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val source = BookSource(bookSourceUrl = "https://example.com", bookSourceName = "示例")
        val received = mutableListOf<String>()
        val observer = Observer<String> { received += it }
        val observable = eventObservable<String>(EventBus.EXPLORE_KINDS_INVALIDATED)
        observable.observeForever(observer)
        try {
            // 登录脚本跑在 IO 线程；refreshExplore 自己也会拒绝主线程调用，
            // 这个前置约束一并冻结在这里。
            thread { source.refreshExplore() }.join()
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            observable.removeObserver(observer)
        }

        assertEquals(listOf("https://example.com"), received)
    }
}
