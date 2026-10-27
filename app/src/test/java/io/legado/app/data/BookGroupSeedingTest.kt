package io.legado.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.repository.BookGroupMutationRepository
import io.legado.app.data.repository.TagGroupRuleApplier
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
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 内置分组的播种时机。
 *
 * 回归成因：13 个内置分组原本全挂在 `dbCallback.onOpen` 里，用
 * `insert ... where not exists` 每次打开数据库重播。这条 SQL 只挡得住「已存在的行」，
 * 挡不住「用户删掉的行」——删掉的内置分组下次启动又长回来，删除等于没删。
 *
 * 所以这里走真实的文件数据库（必须带生产 callback 才测得到播种），并且要跨一次
 * close/reopen：只测建库那一刻的话，onOpen 的重播根本不会执行，缺陷测不出来。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BookGroupSeedingTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val dbName = "book-group-seeding-test.db"

    @Before
    fun setUp() {
        // 生产 callback 的 onOpen 会读 DefaultData.keyboardAssists，它要 appCtx
        context.injectAsAppCtx()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun openDatabase(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        .allowMainThreadQueries()
        .addCallback(AppDatabase.dbCallback)
        .build()

    private fun AppDatabase.deleteGroup(groupId: Long) {
        val database = this
        runBlocking {
            BookGroupMutationRepository(database, TagGroupRuleApplier(database)).deleteGroup(groupId)
        }
    }

    @Test
    fun `新建数据库时播种全部内置分组`() {
        val database = openDatabase()
        try {
            val ids = database.bookGroupDao.all.map { it.groupId }
            assertEquals(
                "内置分组应全部播种",
                listOf(
                    BookGroup.IdAll, BookGroup.IdLocal, BookGroup.IdText, BookGroup.IdManga,
                    BookGroup.IdAudio, BookGroup.IdReading, BookGroup.IdUnread,
                    BookGroup.IdReadFinished, BookGroup.IdReadFinishedUpdate,
                    BookGroup.IdReadFinishedComplete, BookGroup.IdNetNone, BookGroup.IdLocalNone,
                    BookGroup.IdError,
                ).sorted(),
                ids.sorted(),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `删除内置分组后重开数据库不复活`() {
        val first = openDatabase()
        try {
            assertNotNull(first.bookGroupDao.getByID(BookGroup.IdNetNone))
            first.deleteGroup(BookGroup.IdNetNone)
            assertNull(first.bookGroupDao.getByID(BookGroup.IdNetNone))
        } finally {
            first.close()
        }

        val second = openDatabase()
        try {
            assertNull(
                "重开数据库后已删除的内置分组又回来了",
                second.bookGroupDao.getByID(BookGroup.IdNetNone),
            )
            assertTrue("其余内置分组被误删", second.bookGroupDao.getByID(BookGroup.IdText) != null)
        } finally {
            second.close()
        }
    }
}
