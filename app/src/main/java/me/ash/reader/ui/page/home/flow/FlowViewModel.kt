package me.ash.reader.ui.page.home.flow

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.paging.compose.LazyPagingItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.general.MarkAsReadConditions
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class FlowViewModel @Inject constructor(
    private val rssService: RssService,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope
    private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val _flowUiState = MutableStateFlow(FlowUiState())
    val flowUiState: StateFlow<FlowUiState> = _flowUiState.asStateFlow()
    val diffMap = mutableStateMapOf<String, Diff>()

    fun sync(group: String?, feed: String?) {
        applicationScope.launch(ioDispatcher) {
            rssService.get().doSyncOneTime(group, feed)
        }
    }

    fun updateReadStatus(
        groupId: String?,
        feedId: String?,
        articleId: String?,
        conditions: MarkAsReadConditions,
        isUnread: Boolean,
    ) {
        applicationScope.launch(ioDispatcher) {
            rssService.get().markAsRead(
                groupId = groupId,
                feedId = feedId,
                articleId = articleId,
                before = conditions.toDate(),
                isUnread = isUnread,
            )
        }
    }

    fun updateStarredStatus(
        articleId: String?,
        isStarred: Boolean,
    ) {
        applicationScope.launch(ioDispatcher) {
            if (articleId != null) {
                rssService.get().markAsStarred(
                    articleId = articleId,
                    isStarred = isStarred,
                )
            }
        }
    }

    fun markAsReadFromListByDate(
        date: Date,
        isBefore: Boolean,
        lazyPagingItems: LazyPagingItems<ArticleFlowItem>,
    ) {
        applicationScope.launch(ioDispatcher) {
            val articleIdSet = lazyPagingItems.itemSnapshotList.asSequence()
                .filterIsInstance<ArticleFlowItem.Article>()
                .map { it.articleWithFeed.article }
                .filter {
                    if (isBefore) {
                        date > it.date
                    } else {
                        date < it.date
                    }
                }
                .map { it.id }
                .toSet()
            rssService.get().batchMarkAsRead(articleIds = articleIdSet, isUnread = false)
        }
    }

    fun commitDiff() {
        applicationScope.launch(ioDispatcher) {
            val markAsReadArticles =
                diffMap.filter { !it.value.isUnread }.map { it.key }.toSet()
            val markAsUnreadArticles =
                diffMap.filter { it.value.isUnread }.map { it.key }.toSet()

            rssService.get()
                .batchMarkAsRead(articleIds = markAsReadArticles, isUnread = false)
            rssService.get()
                .batchMarkAsRead(articleIds = markAsUnreadArticles, isUnread = true)

        }.invokeOnCompletion { diffMap.clear() }
    }
}

data class FlowUiState(
    val filterImportant: Int = 0,
    val listState: LazyListState = LazyListState(),
    val isBack: Boolean = false,
    val syncWorkInfo: String = "",
)

data class Diff(val isUnread: Boolean)
