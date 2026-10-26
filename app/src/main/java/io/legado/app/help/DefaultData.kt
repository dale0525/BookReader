package io.legado.app.help

import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ThemeConfigStore
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.model.BookCover
import io.legado.app.model.SharedJsScope
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.printOnDebug
import splitties.init.appCtx

object DefaultData {

    fun upVersion() {
        Coroutine.async {
            // 内置书源用独立的 bookSourceVersion 计数，不能挂在 app 版本号门禁里：
            // 本地构建的 versionCode 恒为 32640（无 COMMIT_NUMBER 时的兜底值），
            // 覆盖安装时 LocalConfig.versionCode 已是同一个值，外层判断为 false，
            // 规则更新会被整段跳过。删除重装才会走到，所以只有重装才「修好」。
            if (LocalConfig.needUpBookSources) {
                importDefaultBookSources()
            }
            if (LocalConfig.versionCode < AppConst.appInfo.versionCode) {
                if (LocalConfig.needUpHttpTTS) {
                    importDefaultHttpTTS()
                }
                if (LocalConfig.needUpTxtTocRule) {
                    importDefaultTocRules()
                }
                if (LocalConfig.needUpRssSources) {
                    importDefaultRssSources()
                }
                if (LocalConfig.needUpDictRule) {
                    importDefaultDictRules()
                }
            }
        }.onError {
            it.printOnDebug()
        }
    }

    val httpTTS: List<HttpTTS> by lazy {
        val json =
            String(
                appCtx.assets.open("defaultData/httpTTS.json")
                    .readBytes()
            )
        HttpTTS.fromJsonArray(json).getOrElse {
            emptyList()
        }
    }

    val readConfigs: List<ReadBookConfig.Config> by lazy {
        val json = String(
            appCtx.assets.open("defaultData/${ReadBookConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ReadBookConfig.Config>(json).getOrNull()
            ?: emptyList()
    }

    /** 共享排版的内置默认值。读不到就返回 null，由调用方退到「预设5」。 */
    val shareReadConfig: ReadBookConfig.Config? by lazy {
        runCatching {
            val json = String(
                appCtx.assets.open("defaultData/${ReadBookConfig.shareConfigFileName}")
                    .readBytes()
            )
            GSON.fromJsonObject<ReadBookConfig.Config>(json).getOrThrow()
        }.onFailure {
            it.printOnDebug()
        }.getOrNull()
    }

    val txtTocRules: List<TxtTocRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData/txtTocRule.json")
                .readBytes()
        )
        GSON.fromJsonArray<TxtTocRule>(json).getOrNull() ?: emptyList()
    }

    val themeConfigs: List<ThemeConfigStore.Config> by lazy {
        val json = String(
            appCtx.assets.open("defaultData/${ThemeConfigStore.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ThemeConfigStore.Config>(json).getOrNull() ?: emptyList()
    }

    val rssSources: List<RssSource> by lazy {
        val json = String(
            appCtx.assets.open("defaultData/rssSources.json")
                .readBytes()
        )
        GSON.fromJsonArray<RssSource>(json).getOrDefault(emptyList())
    }

    private val bookSourcesJson: String by lazy {
        String(appCtx.assets.open("defaultData/bookSources.json").readBytes())
    }

    val bookSources: List<BookSource> by lazy {
        GSON.fromJsonArray<BookSource>(bookSourcesJson).getOrDefault(emptyList())
    }

    /** 随包那份的摘要：远端取到的内容与它相同就不必再写一遍库。 */
    val bookSourcesHash: String by lazy { MD5Utils.md5Encode(bookSourcesJson) }

    val coverRule: BookCover.CoverRule by lazy {
        val json = String(
            appCtx.assets.open("defaultData/coverRule.json")
                .readBytes()
        )
        GSON.fromJsonObject<BookCover.CoverRule>(json).getOrThrow()
    }

    val dictRules: List<DictRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData/dictRules.json")
                .readBytes()
        )
        GSON.fromJsonArray<DictRule>(json).getOrThrow()
    }

    val keyboardAssists: List<KeyboardAssist> by lazy {
        val json = String(
            appCtx.assets.open("defaultData/keyboardAssists.json")
                .readBytes()
        )
        GSON.fromJsonArray<KeyboardAssist>(json).getOrThrow()
    }

    fun importDefaultHttpTTS() {
        appDb.httpTTSDao.deleteDefault()
        appDb.httpTTSDao.insert(*httpTTS.toTypedArray())
    }

    fun importDefaultTocRules() {
        appDb.txtTocRuleDao.deleteDefault()
        appDb.txtTocRuleDao.insert(*txtTocRules.toTypedArray())
    }

    fun importDefaultRssSources() {
        appDb.rssSourceDao.deleteDefault()
        appDb.rssSourceDao.insert(*rssSources.toTypedArray())
    }

    /**
     * 历史上内置过、现已停止分发的书源。只从 asset 里删掉只影响全新安装，
     * 覆盖安装的用户书源表里还留着这些条目，所以升级时按 url 清掉。
     */
    private val retiredBookSourceUrls = listOf(
        "https://www.kaixin7days.com", // 消消乐听书
        // 拆源前的 6 个聚合源。`agg.example.com` 是 example.com 下的占位域名，
        // 从来没有真实内容，书架上的书不可能靠它读到正文。
        "https://agg.example.com/novel-normal",
        "https://agg.example.com/manga-normal",
        "https://agg.example.com/video-normal",
        "https://agg.example.com/novel-adult",
        "https://agg.example.com/manga-adult",
        "https://agg.example.com/video-adult",
    )

    /**
     * 内置源随版本更新规则：规则字段用 asset 里的新值覆盖，用户自己的开关/分组/排序保留。
     * 内置源的规则是随包发布的代码资产，用户不该改也不该被旧版本锁死；
     * 只按 has() 判重会在升级时留下旧规则，正是发现页报错的成因。
     */
    suspend fun importDefaultBookSources() {
        retiredBookSourceUrls.forEach { url -> appDb.bookSourceDao.delete(url) }
        importBookSources(bookSources)
    }

    /**
     * 把一批内置源写进库：规则字段用新值覆盖，用户自己的开关/分组/排序保留。
     * asset 版和远端版都走这里，两边语义必须一致，否则远端更新会悄悄改掉用户的设置。
     */
    suspend fun importBookSources(sources: List<BookSource>) {
        // 远端清单可能是旧版（`main` 上还留着拆源前的聚合源），一写就把刚删掉的
        // 条目复活。删除只在升级时做一次，导入侧必须按 url 滤掉，否则删不干净。
        sources.filterNot { it.bookSourceUrl in retiredBookSourceUrls }.forEach { incoming ->
            val existing = appDb.bookSourceDao.getBookSource(incoming.bookSourceUrl)
            if (existing == null) {
                appDb.bookSourceDao.insert(incoming)
            } else {
                // 分类缓存按 exploreUrl 失效，而内置源的 exploreUrl 通常恒为同一段 JS，
                // 改的是它背后的 jsLib。不清缓存的话，覆盖安装后发现页仍显示旧分类。
                existing.clearExploreKindsCache()
                // jsLib 换掉后，旧作用域里的函数还挂在 MD5(旧 jsLib) 这个 key 上；
                // 远程 jsLib 更是把整段规则按 MD5(url) 缓存在 ACache 里且永不过期，
                // 不清掉就会继续跑旧规则，表现为「改了 asset 却没生效」。
                SharedJsScope.remove(existing.jsLib)
                appDb.bookSourceDao.insert(
                    incoming.copy(
                        enabled = existing.enabled,
                        bookSourceGroup = existing.bookSourceGroup,
                        customOrder = existing.customOrder,
                        enabledExplore = existing.enabledExplore,
                    )
                )
            }
        }
    }

    /**
     * 历史上内置过、但源站已不可解析的词典规则（规则名 -> 旧 urlRule）。
     * 只在 urlRule 与旧内置值完全一致时删除，避免误删用户自建的同名规则。
     */
    private val obsoleteDictRules = mapOf(
        "百度汉语" to "https://dict.baidu.com/s?wd={{key}}"
    )

    fun importDefaultDictRules() {
        // 清理已失效的内置词典规则（源站改为前端渲染，规则无法解析出内容）
        obsoleteDictRules.forEach { (name, urlRule) ->
            val existing = appDb.dictRuleDao.getByName(name) ?: return@forEach
            if (existing.urlRule == urlRule) appDb.dictRuleDao.deleteByName(name)
        }
        appDb.dictRuleDao.insert(*dictRules.toTypedArray())
    }

}