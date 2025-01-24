package me.ash.reader.domain.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import me.ash.reader.R
import me.ash.reader.domain.model.account.Account
import me.ash.reader.domain.model.account.security.GoogleReaderSecurityKey
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.repository.AccountDao
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.infrastructure.android.NotificationHelper
import me.ash.reader.infrastructure.di.DefaultDispatcher
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.di.MainDispatcher
import me.ash.reader.infrastructure.html.Readability
import me.ash.reader.infrastructure.rss.FetchedFeed
import me.ash.reader.infrastructure.rss.RssHelper
import me.ash.reader.infrastructure.rss.provider.greader.GoogleReaderAPI
import me.ash.reader.infrastructure.rss.provider.greader.GoogleReaderAPI.Companion.ofCategoryIdToStreamId
import me.ash.reader.infrastructure.rss.provider.greader.GoogleReaderAPI.Companion.ofCategoryStreamIdToId
import me.ash.reader.infrastructure.rss.provider.greader.GoogleReaderAPI.Companion.ofFeedStreamIdToId
import me.ash.reader.infrastructure.rss.provider.greader.GoogleReaderAPI.Companion.ofItemStreamIdToId
import me.ash.reader.infrastructure.rss.provider.greader.GoogleReaderDTO
import me.ash.reader.ui.ext.currentAccountId
import me.ash.reader.ui.ext.decodeHTML
import me.ash.reader.ui.ext.dollarLast
import me.ash.reader.ui.ext.isFuture
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.ext.spacerDollar
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

class GoogleReaderRssService @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val rssHelper: RssHelper,
    private val notificationHelper: NotificationHelper,
    private val accountDao: AccountDao,
    private val groupDao: GroupDao,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher
    private val mainDispatcher: CoroutineDispatcher,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
    private val workManager: WorkManager,
) : AbstractRssRepository(
    context, accountDao, articleDao, groupDao,
    feedDao, workManager, rssHelper, notificationHelper, ioDispatcher, defaultDispatcher
) {

    override val importSubscription: Boolean = false
    override val addSubscription: Boolean = true
    override val moveSubscription: Boolean = true
    override val deleteSubscription: Boolean = true
    override val updateSubscription: Boolean = true

    private suspend fun getGoogleReaderAPI() =
        GoogleReaderSecurityKey(accountDao.queryById(context.currentAccountId)!!.securityKey).run {
            GoogleReaderAPI.getInstance(
                context = context,
                serverUrl = serverUrl!!,
                username = username!!,
                password = password!!,
                httpUsername = null,
                httpPassword = null,
                clientCertificateAlias = clientCertificateAlias,
            )
        }

    override suspend fun validCredentials(account: Account): Boolean {
        return getGoogleReaderAPI().validCredentials().also { success ->
            if (success) try {
                getGoogleReaderAPI().getUserInfo().userName?.let {
                    accountDao.update(account.copy(name = it))
                }
            } catch (ignore: Exception) {
                Log.e("RLog", "get user info is failed: ", ignore)
            }
        }
    }

    override suspend fun clearAuthorization() {
        GoogleReaderAPI.clearInstance()
    }

    override suspend fun subscribe(
        feedLink: String, searchedFeed: FetchedFeed, groupId: String,
        isNotification: Boolean, isFullContent: Boolean, isBrowser: Boolean,
    ) {
        val accountId = context.currentAccountId
        val quickAdd = getGoogleReaderAPI().subscriptionQuickAdd(feedLink)
        val feedId = quickAdd.streamId?.ofFeedStreamIdToId()
        requireNotNull(feedId) {
            "feedId is null"
        }
        val feedTitle = searchedFeed.title
        requireNotNull(feedTitle) {
            "feedTitle is null"
        }

        getGoogleReaderAPI().subscriptionEdit(
            destFeedId = feedId,
            destCategoryId = groupId.dollarLast(),
            destFeedName = feedTitle
        )
        feedDao.insert(
            Feed(
                id = accountId.spacerDollar(feedId),
                name = feedTitle,
                url = feedLink,
                groupId = groupId,
                accountId = accountId,
                isNotification = isNotification,
                isFullContent = isFullContent,
                isBrowser = isBrowser,
            )
        )
        // TODO: When users need to subscribe to multiple feeds continuously, this makes them uncomfortable.
        //  It is necessary to make syncWork support synchronizing individual specified feeds.
        // super.doSyncOneTime()
    }

    override suspend fun addGroup(destFeed: Feed?, newGroupName: String): String {
        val accountId = context.currentAccountId
        getGoogleReaderAPI().subscriptionEdit(
            destFeedId = destFeed?.id?.dollarLast(),
            destCategoryId = newGroupName
        )
        val id = accountId.spacerDollar(newGroupName.ofCategoryIdToStreamId())
        groupDao.insert(
            Group(
                id = id,
                name = newGroupName,
                accountId = accountId
            )
        )
        return id
    }

    override suspend fun renameGroup(group: Group) {
        getGoogleReaderAPI().renameTag(
            categoryId = group.id.dollarLast(),
            renameToName = group.name
        )
        // TODO: Whether to switch the old ID to the new ID?
        super.renameGroup(group)
    }

    override suspend fun moveFeed(originGroupId: String, feed: Feed) {
        getGoogleReaderAPI().subscriptionEdit(
            destFeedId = feed.id.dollarLast(),
            destCategoryId = feed.groupId.dollarLast().ofCategoryStreamIdToId(),
            originCategoryId = originGroupId.dollarLast().ofCategoryStreamIdToId(),
        )
        super.moveFeed(originGroupId, feed)
    }

    override suspend fun changeFeedUrl(feed: Feed) {
        throw Exception("Unsupported")
    }

    override suspend fun renameFeed(feed: Feed) {
        getGoogleReaderAPI().subscriptionEdit(
            destFeedId = feed.id.dollarLast(),
            destFeedName = feed.name
        )
        // TODO: Whether to switch the old ID to the new ID?
        super.renameFeed(feed)
    }

    override suspend fun deleteGroup(group: Group, onlyDeleteNoStarred: Boolean?) {
        feedDao.queryByGroupId(context.currentAccountId, group.id)
            .forEach { deleteFeed(it) }
        getGoogleReaderAPI().disableTag(group.id.dollarLast())
        super.deleteGroup(group, false)
    }

    override suspend fun deleteFeed(feed: Feed, onlyDeleteNoStarred: Boolean?) {
        getGoogleReaderAPI().subscriptionEdit(
            action = "unsubscribe",
            destFeedId = feed.id.dollarLast()
        )
        super.deleteFeed(feed, false)
    }

    /**
     * This is improved from Reeder's synchronization strategy,
     * which syncs well across multiple devices.
     *
     * 1. Fetch tags (not supported yet)
     * 2. Fetch folder and subscription list
     * 3. Fetch all unread item id list
     * 4. Fetch all starred item id list
     * 5. Fetch unread contents of items with differences (up to 10k items per sync process)
     * 6. Fetch starred contents of items with differences
     * 7. Fetch read contents of items with differences (up to one month old)
     * 8. Remove orphaned groups and feeds, after synchronizing the starred/un-starred
     *
     * The following link contains other great synchronization logic,
     * but it was not adopted due to the solidified domain model of this application.
     *
     * @link https://github.com/FreshRSS/FreshRSS/issues/2566#issuecomment-541317776
     * @link https://github.com/bazqux/bazqux-api?tab=readme-ov-file
     * @link https://github.com/theoldreader/api
     */
    override suspend fun sync(coroutineWorker: CoroutineWorker): ListenableWorker.Result =
        supervisorScope {
            coroutineWorker.setProgress(SyncWorker.setIsSyncing(true))

            try {
                val preTime = System.currentTimeMillis()
                val preDate = Date(preTime)
                val accountId = context.currentAccountId
                val account = accountDao.queryById(accountId)
                requireNotNull(account) {
                    "cannot find account"
                }
                val googleReaderAPI = getGoogleReaderAPI()
                val groupIds = mutableSetOf<String>()
                val feedIds = mutableSetOf<String>()
                val lastMonthAt = Calendar.getInstance().apply {
                    time = preDate
                    add(Calendar.MONTH, -1)
                }.time.time / 1000

                // 1. Fetch tags (not supported yet)

                // 2. Fetch folder and subscription list
                googleReaderAPI.getSubscriptionList()
                    .subscriptions.groupBy { it.categories?.first() }
                    .toSortedMap { c1, c2 -> c1?.label.toString().compareTo(c2?.label.toString()) }
                    .forEach { (category, feeds) ->
                        val categoryId = category?.id
                        requireNotNull(categoryId) {
                            "category id is null"
                        }
                        val groupId =
                            accountId spacerDollar categoryId.ofCategoryStreamIdToId()

                        // Handle folders
                        groupDao.insertOrUpdate(
                            listOf(
                                Group(
                                    id = groupId,
                                    name = category.label.toString(),
                                    accountId = accountId,
                                )
                            )
                        )
                        groupIds.add(groupId)

                        // Handle feeds
                        feedDao.insertOrUpdate(
                            feeds.map {
                                requireNotNull(it.id) {
                                    "feed id is null"
                                }
                                requireNotNull(it.url) {
                                    "feed url is null"
                                }
                                val feedId = accountId spacerDollar it.id.ofFeedStreamIdToId()
                                Feed(
                                    id = feedId,
                                    name = it.title.decodeHTML()
                                        ?: context.getString(R.string.empty),
                                    url = it.url,
                                    groupId = groupId,
                                    accountId = accountId,
                                    icon = it.iconUrl
                                ).also {
                                    feedIds.add(feedId)
                                }
                            }
                        )
                    }

                // Handle empty icon for feeds
                feedDao.queryNoIcon(accountId).let {
                    it.forEach { feed ->
                        feed.icon = rssHelper.queryRssIconLink(feed.url)
                    }
                    feedDao.update(*it.toTypedArray())
                }

                val localAllItems = articleDao.queryMetadataAll(accountId)
                val localUnreadIds =
                    localAllItems.filter { it.isUnread }.map { it.id.dollarLast() }.toSet()
                val localStarredIds =
                    localAllItems.filter { it.isStarred }.map { it.id.dollarLast() }.toSet()

                // 3. Fetch all unread item id list
                val unreadIds = fetchItemIdsAndContinue {
                    googleReaderAPI.getUnreadItemIds(continuationId = it)
                }.toSet()
                Log.i("RLog", "sync unreadIds size: ${unreadIds.size}")
                val toBeUnread = (unreadIds - localUnreadIds).run {
                    if (size > 10000) take(10000).toSet() else this
                }
                Log.i("RLog", "sync toBeUnread size: ${toBeUnread.size}")
                toBeUnread.takeIf { it.isNotEmpty() }?.chunked(500)?.forEach {
                    articleDao.markAsReadByIdSet(
                        accountId = accountId,
                        ids = it.toSet(),
                        isUnread = true,
                    )
                }

                // 4. Fetch all starred item id list
                val starredIds = fetchItemIdsAndContinue {
                    googleReaderAPI.getStarredItemIds(continuationId = it)
                }.toSet()
                Log.i("RLog", "sync starredIds size: ${starredIds.size}")
                val toBeStarred = starredIds - localStarredIds
                Log.i("RLog", "sync toBeStarred size: ${toBeStarred.size}")
                toBeStarred.takeIf { it.isNotEmpty() }?.chunked(500)?.forEach {
                    articleDao.markAsStarredByIdSet(
                        accountId = accountId,
                        ids = it.toSet(),
                        isStarred = true,
                    )
                }

                // 5. Fetch unread contents of items with differences (up to 10k items per sync process)
                fetchItemsContents(
                    itemIds = toBeUnread,
                    googleReaderAPI = googleReaderAPI,
                    accountId = accountId,
                    feedIds = feedIds,
                    unreadIds = toBeUnread,
                    starredIds = starredIds,
                    preDate = preDate,
                )

                // 6. Fetch starred contents of items with differences
                fetchItemsContents(
                    itemIds = toBeStarred,
                    googleReaderAPI = googleReaderAPI,
                    accountId = accountId,
                    feedIds = feedIds,
                    unreadIds = unreadIds,
                    starredIds = toBeStarred,
                    preDate = preDate,
                )

                // 7. Fetch read contents of items with differences (up to one month old)
                val readIds = fetchItemIdsAndContinue {
                    googleReaderAPI.getReadItemIds(since = lastMonthAt, continuationId = it)
                }.toSet()
                Log.i("RLog", "sync readIds size: ${readIds.size}")
                val localReadIds = articleDao.queryMetadataAll(accountId).filter { !it.isUnread }
                    .map { it.id.dollarLast() }.toSet()
                var toBeRead = readIds - unreadIds - localReadIds
                Log.i("RLog", "sync toBeRead size: ${toBeRead.size}")
                if (toBeRead.isNotEmpty()) {
                    fetchItemsContents(
                        itemIds = toBeRead,
                        googleReaderAPI = googleReaderAPI,
                        accountId = accountId,
                        feedIds = feedIds,
                        unreadIds = setOf(),
                        starredIds = starredIds,
                        preDate = preDate,
                    )
                }
                // Sync the read status of articles prior to last month
                toBeRead = localUnreadIds - unreadIds
                Log.i("RLog", "sync toBeRead (last month) size: ${toBeRead.size}")
                toBeRead.takeIf { it.isNotEmpty() }?.chunked(500)?.forEach {
                    articleDao.markAsReadByIdSet(
                        accountId = accountId,
                        ids = it.toSet(),
                        isUnread = false,
                    )
                }

                // 8. Remove orphaned groups and feeds, after synchronizing the starred/un-starred
                groupDao.queryAll(accountId)
                    .filter { it.id !in groupIds }
                    .forEach { super.deleteGroup(it, true) }
                feedDao.queryAll(accountId)
                    .filter { it.id !in feedIds }
                    .forEach { super.deleteFeed(it, true) }

                Log.i("RLog", "onCompletion: ${System.currentTimeMillis() - preTime}")
                accountDao.update(account.apply {
                    updateAt = Date()
                })
                ListenableWorker.Result.success(SyncWorker.setIsSyncing(false))
            } catch (e: Exception) {
                Log.e("RLog", "On sync exception: ${e.message}", e)
                withContext(mainDispatcher) {
                    context.showToast(e.message)
                }
                ListenableWorker.Result.failure(SyncWorker.setIsSyncing(false))
            }
        }

    private suspend fun fetchItemIdsAndContinue(getItemIdsFunc: suspend (continuationId: String?) -> GoogleReaderDTO.ItemIds): MutableList<String> {
        var result = getItemIdsFunc(null)
        val ids = result.itemRefs?.mapNotNull { it.id }?.toMutableList() ?: return mutableListOf()
        while (result.continuation != null) {
            result = getItemIdsFunc(result.continuation)
            result.itemRefs?.mapNotNull { it.id }?.let { ids.addAll(it) }
        }
        return ids
    }

    private suspend fun fetchItemsContents(
        itemIds: Set<String>,
        googleReaderAPI: GoogleReaderAPI,
        accountId: Int,
        feedIds: MutableSet<String>,
        unreadIds: Set<String>,
        starredIds: Set<String>,
        preDate: Date,
    ) {
        itemIds.chunked(100).forEach { chunkedIds ->
            articleDao.insert(
                *googleReaderAPI.getItemsContents(chunkedIds).items?.map {
                    val articleId = it.id?.ofItemStreamIdToId()
                    requireNotNull(articleId) {
                        "articleId is null"
                    }
                    Article(
                        id = accountId.spacerDollar(articleId),
                        date = it.published
                            ?.run { Date(this * 1000) }
                            ?.takeIf { !it.isFuture(preDate) }
                            ?: preDate,
                        title = it.title.decodeHTML() ?: context.getString(R.string.empty),
                        author = it.author,
                        rawDescription = it.summary?.content ?: "",
                        shortDescription = Readability
                            .parseToText(it.summary?.content, findArticleURL(it)).take(110),
                        fullContent = it.summary?.content ?: "",
                        img = rssHelper.findThumbnail(it.summary?.content),
                        link = findArticleURL(it),
                        feedId = accountId.spacerDollar(
                            it.origin?.streamId?.ofFeedStreamIdToId()
                                ?: feedIds.first()
                        ),
                        accountId = accountId,
                        isUnread = unreadIds.contains(articleId),
                        isStarred = starredIds.contains(articleId),
                        updateAt = it.crawlTimeMsec?.run { Date(this.toLong()) } ?: preDate,
                    )
                }?.toTypedArray() ?: emptyArray()
            )
        }
    }

    private fun findArticleURL(it: GoogleReaderDTO.Item) = it.canonical?.first()?.href
        ?: it.alternate?.first()?.href
        ?: it.origin?.htmlUrl ?: ""

    override suspend fun markAsRead(
        groupId: String?,
        feedId: String?,
        articleId: String?,
        before: Date?,
        isUnread: Boolean,
    ) {
        val accountId = context.currentAccountId
        val googleReaderAPI = getGoogleReaderAPI()
        val markList: List<String> = when {
            groupId != null -> {
                if (before == null) {
                    articleDao.queryMetadataByGroupIdWhenIsUnread(accountId, groupId, !isUnread)
                } else {
                    articleDao.queryMetadataByGroupIdWhenIsUnread(
                        accountId,
                        groupId,
                        !isUnread,
                        before
                    )
                }.map { it.id.dollarLast() }
            }

            feedId != null -> {
                if (before == null) {
                    articleDao.queryMetadataByFeedId(accountId, feedId, !isUnread)
                } else {
                    articleDao.queryMetadataByFeedId(accountId, feedId, !isUnread, before)
                }.map { it.id.dollarLast() }
            }

            articleId != null -> {
                listOf(articleId.dollarLast())
            }

            else -> {
                if (before == null) {
                    articleDao.queryMetadataAll(accountId, !isUnread)
                } else {
                    articleDao.queryMetadataAll(accountId, !isUnread, before)
                }.map { it.id.dollarLast() }
            }
        }
        super.markAsRead(groupId, feedId, articleId, before, isUnread)
        markList.takeIf { it.isNotEmpty() }?.chunked(500)?.forEachIndexed { index, it ->
            Log.d("RLog", "sync markAsRead:  ${(index * 500) + it.size}/${markList.size} num")
            googleReaderAPI.editTag(
                itemIds = it,
                mark = if (!isUnread) GoogleReaderAPI.Stream.READ.tag else null,
                unmark = if (isUnread) GoogleReaderAPI.Stream.READ.tag else null,
            )
        }
    }

    override suspend fun batchMarkAsRead(articleIds: Set<String>, isUnread: Boolean) {
        super.batchMarkAsRead(articleIds, isUnread)
        val googleReaderAPI = getGoogleReaderAPI()
        articleIds.takeIf { it.isNotEmpty() }?.chunked(500)?.forEachIndexed { index, it ->
            Log.d("RLog", "sync markAsRead:  ${(index * 500) + it.size}/${articleIds.size} num")
            googleReaderAPI.editTag(
                itemIds = it.map { it.dollarLast() },
                mark = if (!isUnread) GoogleReaderAPI.Stream.READ.tag else null,
                unmark = if (isUnread) GoogleReaderAPI.Stream.READ.tag else null,
            )
        }
    }

    override suspend fun markAsStarred(articleId: String, isStarred: Boolean) {
        super.markAsStarred(articleId, isStarred)
        getGoogleReaderAPI().editTag(
            itemIds = listOf(articleId.dollarLast()),
            mark = if (isStarred) GoogleReaderAPI.Stream.STARRED.tag else null,
            unmark = if (!isStarred) GoogleReaderAPI.Stream.STARRED.tag else null,
        )
    }
}
