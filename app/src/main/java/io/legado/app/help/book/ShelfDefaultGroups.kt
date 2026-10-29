package io.legado.app.help.book

import io.legado.app.constant.AppPattern
import io.legado.app.constant.ExploreGroups
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.utils.splitNotBlank

/**
 * 六个默认分组：名字与发现页的六个分类完全一致，首次入架时按书源分类自动归组。
 *
 * 建组只在升级路径上跑一次（`LocalConfig.shelfDefaultGroupsCreated` 标记），不在每次冷启动
 * 重播：重播会让用户删掉的默认分组下次启动又长回来，删除等于没删——这和内置分组的播种
 * 时机是同一个教训。建组本身是幂等的，所以直接调也不会多出第二份。
 *
 * 这里的 id 必须由 [io.legado.app.data.dao.BookGroupDao.getUnusedId] 现取，不能用固定常量：
 * `Book.group` 是位图，用户分组的 id 必须是尚未占用的那一位。内置分组里的「小说」
 * （`BookGroup.IdText`）和「漫画」（`BookGroup.IdManga`）名字与发现页分类撞车但 id 是负数，
 * 一旦被当成成员位写进 `Book.group`，位图就整个算错了——所以按名字找分组时只认正数 id。
 */
fun ensureShelfDefaultGroups() {
    val bookGroupDao = appDb.bookGroupDao
    val existing = bookGroupDao.all
    // MAX(`order`) 在没有用户分组时是 NULL，Room 读成 0，从 1 开始排即可。
    var order = bookGroupDao.maxOrder
    for (name in ExploreGroups.ordered) {
        if (existing.any { it.groupId > 0 && it.groupName == name }) continue
        val groupId = bookGroupDao.getUnusedId()
        // 64 个成员位全占满时 getUnusedId 返回 0，Long.MIN_VALUE 是私有分组的保留位，
        // 两者都不能拿来做公开分组。
        if (groupId == 0L || groupId == Long.MIN_VALUE) return
        order += 1
        bookGroupDao.insert(BookGroup(groupId = groupId, groupName = name, order = order))
    }
}

/**
 * 首次加入书架时按书源的发现页分类归组。调用点只有两处：`Book.save()` 与
 * `BookRepository.insert()`——加入书架的各条路径（搜索、详情页、阅读器、音频、书单导入、
 * 批量换源添加）都汇到这两处。另有 `MangaReaderActionRepository.persistOnShelf()`
 * 直接写 DAO，绕开了它们，需要显式调用。
 *
 * 三个前提缺一不可：
 * - 本地书没有书源，谈不上分类。
 * - 已有 `group` 的书是用户自己归的组（或标签规则给的），不覆盖。
 * - 库里已有这条上架记录，就不是首次入架。用户可能刚把它移出所有分组，
 *   保存书籍信息、同步阅读进度都会再走一遍 insert，塞回去等于改不动。
 *   详情页试读先插一行「未上架」的记录，之后点「加入书架」复用同一个 bookUrl，
 *   所以「记录存在」不等于「已上架」，要看 `isNotShelf`。
 */
fun assignShelfDefaultGroup(book: Book) {
    if (book.isLocal || book.isNotShelf || book.group != 0L) return
    if (appDb.bookDao.getBook(book.bookUrl)?.isNotShelf == false) return
    val source = appDb.bookSourceDao.getBookSource(book.origin) ?: return
    // 与发现页同一套归一：分组名写错（「短剧」「玄幻」）或没写时按 bookSourceType 兜底。
    val firstGroup = source.bookSourceGroup
        ?.splitNotBlank(AppPattern.splitGroupRegex)?.firstOrNull()
    val targetName = ExploreGroups.canonical(firstGroup, source.bookSourceType)
    val targetId = appDb.bookGroupDao.all
        .firstOrNull { it.groupId > 0 && it.groupName == targetName }
        ?.groupId ?: return
    book.group = targetId
}
