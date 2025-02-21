package me.ash.reader.ui.page.home.flow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.eventFlow
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.work.WorkInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.infrastructure.preference.LocalFlowArticleListDateStickyHeader
import me.ash.reader.infrastructure.preference.LocalFlowArticleListFeedIcon
import me.ash.reader.infrastructure.preference.LocalFlowArticleListTonalElevation
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarFilled
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarPadding
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarStyle
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarTonalElevation
import me.ash.reader.infrastructure.preference.LocalFlowTopBarTonalElevation
import me.ash.reader.infrastructure.preference.LocalMarkAsReadOnScroll
import me.ash.reader.infrastructure.preference.LocalSharedContent
import me.ash.reader.ui.component.FilterBar
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYExtensibleVisibility
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.surfaceColorAtElevation
import me.ash.reader.ui.motion.materialSharedAxisYIn
import me.ash.reader.ui.motion.materialSharedAxisYOut
import me.ash.reader.ui.page.common.RouteName
import me.ash.reader.ui.page.home.HomeViewModel

@OptIn(
    ExperimentalMaterial3Api::class,
)
@Composable
fun FlowPage(
    navController: NavHostController,
    flowViewModel: FlowViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val articleListTonalElevation = LocalFlowArticleListTonalElevation.current
    val articleListFeedIcon = LocalFlowArticleListFeedIcon.current
    val articleListDateStickyHeader = LocalFlowArticleListDateStickyHeader.current
    val topBarTonalElevation = LocalFlowTopBarTonalElevation.current
    val filterBarStyle = LocalFlowFilterBarStyle.current
    val filterBarFilled = LocalFlowFilterBarFilled.current
    val filterBarPadding = LocalFlowFilterBarPadding.current
    val filterBarTonalElevation = LocalFlowFilterBarTonalElevation.current
    val sharedContent = LocalSharedContent.current
    val markAsReadOnScroll = LocalMarkAsReadOnScroll.current.value
    val context = LocalContext.current

    val homeUiState = homeViewModel.homeUiState.collectAsStateValue()
    val flowUiState = flowViewModel.flowUiState.collectAsStateValue()
    val filterUiState = homeViewModel.filterUiState.collectAsStateValue()
    val pagingItems = homeUiState.pagingData.collectAsLazyPagingItems()
    val listState =
        if (pagingItems.itemCount > 0) flowUiState.listState else rememberLazyListState()

    val isTopBarElevated = topBarTonalElevation.value > 0
    val isScrolled by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex != 0 } }
    val topBarContainerColor by animateColorAsState(with(MaterialTheme.colorScheme) {
        if (isScrolled && isTopBarElevated) surfaceContainer else surface
    }, label = "")

    val titleText = when {
        filterUiState.group != null -> filterUiState.group.name
        filterUiState.feed != null -> filterUiState.feed.name
        else -> filterUiState.filter.toName()
    }

    if (markAsReadOnScroll) {
        LaunchedEffect(listState.isScrollInProgress) {
            if (!listState.isScrollInProgress) {
                val firstItemIndex = listState.firstVisibleItemIndex
                val diffMap = flowViewModel.diffMap
                if (firstItemIndex < pagingItems.itemCount)
                    for (index in 0 until firstItemIndex) {
                        val item = pagingItems.peek(index)
                        with(item) {
                            when (this) {
                                is ArticleFlowItem.Article -> {
                                    val id = articleWithFeed.article.id
                                    if (!diffMap.containsKey(id)) {
                                        diffMap[id] = Diff(isUnread = false)
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var markAsRead by remember { mutableStateOf(false) }
    var onSearch by rememberSaveable { mutableStateOf(false) }

    val owner = LocalLifecycleOwner.current

    var isSyncing by remember { mutableStateOf(false) }
    val syncingState = rememberPullToRefreshState()
    val syncingScope = rememberCoroutineScope()
    val doSync: () -> Unit = {
        isSyncing = true
        val group = filterUiState.group?.id
        val feed = filterUiState.feed?.id
        syncingScope.launch {
            flowViewModel.sync(group, feed)
        }
    }

    DisposableEffect(pagingItems) {
        onDispose {
            flowViewModel.commitDiff()
        }
    }

    DisposableEffect(owner) {
        scope.launch {
            owner.lifecycle.eventFlow.collect {
                if (it == Lifecycle.Event.ON_PAUSE) {
                    flowViewModel.commitDiff()
                }
            }
        }
        homeViewModel.syncWorkLiveData.observe(owner) { workInfoList ->
            workInfoList.let {
                isSyncing = it.any { workInfo -> workInfo.state == WorkInfo.State.RUNNING }
            }
        }
        onDispose { homeViewModel.syncWorkLiveData.removeObservers(owner) }
    }

    val onToggleStarred: (ArticleWithFeed) -> Unit = remember {
        { article ->
            flowViewModel.updateStarredStatus(
                articleId = article.article.id,
                isStarred = !article.article.isStarred,
            )
        }
    }

    val onToggleRead: (ArticleWithFeed) -> Unit = remember {
        { article ->
            val id = article.article.id
            val isUnread = article.article.isUnread

            with(flowViewModel.diffMap) {
                if (contains(id)) remove(id)
                else put(id, Diff(isUnread = !isUnread))
            }
        }
    }

    val onMarkAboveAsRead: ((ArticleWithFeed) -> Unit)? = remember {
        {
            flowViewModel.markAsReadFromListByDate(
                date = it.article.date,
                isBefore = false,
                lazyPagingItems = pagingItems
            )
        }
    }

    val onMarkBelowAsRead: ((ArticleWithFeed) -> Unit)? = remember {
        {
            flowViewModel.markAsReadFromListByDate(
                date = it.article.date,
                isBefore = true,
                lazyPagingItems = pagingItems
            )
        }
    }

    val onShare: ((ArticleWithFeed) -> Unit)? = remember {
        { articleWithFeed ->
            with(articleWithFeed.article) {
                sharedContent.share(context, title, link)
            }
        }
    }
    LaunchedEffect(onSearch) {
        if (!onSearch) {
            keyboardController?.hide()
            if (homeUiState.searchContent.isNotBlank()) {
                homeViewModel.inputSearchContent("")
            }
        }
    }

    LaunchedEffect(flowUiState.listState) {
        snapshotFlow { flowUiState.listState.firstVisibleItemIndex }.collect {
            if (it > 0) {
                keyboardController?.hide()
            }
        }
    }

    BackHandler(onSearch) {
        onSearch = false
    }

    RYScaffold(
        containerTonalElevation = articleListTonalElevation.value.dp,
        topBar = {
            TopAppBar(
                modifier = Modifier.clickable(
                    onClick = {
                        scope.launch {
                            if (listState.firstVisibleItemIndex != 0) {
                                listState.animateScrollToItem(0)
                            }
                        }
                    },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
                title = {
                    AnimatedVisibility(
                        isScrolled,
                        enter = materialSharedAxisYIn(initialOffsetY = { it / 4 }),
                        exit = materialSharedAxisYOut(targetOffsetY = { it / 4 })
                    ) {
                        Text(
                            text = titleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        )
                    }
                },
                navigationIcon = {
                    FeedbackIconButton(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurface
                    ) {
                        onSearch = false
                        if (navController.previousBackStackEntry == null) {
                            navController.navigate(RouteName.FEEDS) {
                                launchSingleTop = true
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }
                },
                actions = {
                    RYExtensibleVisibility(visible = !filterUiState.filter.isStarred()) {
                        FeedbackIconButton(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = stringResource(R.string.mark_all_as_read),
                            tint = if (markAsRead) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        ) {
                            scope.launch {
                                if (flowUiState.listState.firstVisibleItemIndex != 0) {
                                    flowUiState.listState.animateScrollToItem(0)
                                }
                                markAsRead = !markAsRead
                                onSearch = false
                            }
                        }
                    }
                    FeedbackIconButton(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = if (onSearch) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ) {
                        scope.launch {
                            if (flowUiState.listState.firstVisibleItemIndex != 0) {
                                flowUiState.listState.animateScrollToItem(0)
                            }
                            onSearch = !onSearch
                            if (onSearch) {
                                delay(100)
                                focusRequester.requestFocus()
                            }
                        }
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarContainerColor
                )
            )
        },
        content = {
            PullToRefreshBox(
                state = syncingState,
                isRefreshing = isSyncing,
                onRefresh = doSync
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                ) {
                    item {
                        DisplayText(
                            modifier = Modifier.padding(start = if (articleListFeedIcon.value) 30.dp else 0.dp),
                            text = titleText,
                            desc = "",
                        )
                        RYExtensibleVisibility(visible = markAsRead) {
                            Spacer(modifier = Modifier.height((56 + 24 + 10).dp))
                        }
                        MarkAsReadBar(
                            visible = markAsRead,
                            absoluteY = if (isSyncing) (4 + 16 + 180).dp else 180.dp,
                            onDismissRequest = {
                                markAsRead = false
                            },
                        ) {
                            markAsRead = false
                            flowViewModel.updateReadStatus(
                                groupId = filterUiState.group?.id,
                                feedId = filterUiState.feed?.id,
                                articleId = null,
                                conditions = it,
                                isUnread = false
                            )
                        }
                        RYExtensibleVisibility(visible = onSearch) {
                            SearchBar(
                                value = homeUiState.searchContent,
                                placeholder = when {
                                    filterUiState.group != null -> stringResource(
                                        R.string.search_for_in,
                                        filterUiState.filter.toName(),
                                        filterUiState.group.name
                                    )

                                    filterUiState.feed != null -> stringResource(
                                        R.string.search_for_in,
                                        filterUiState.filter.toName(),
                                        filterUiState.feed.name
                                    )

                                    else -> stringResource(
                                        R.string.search_for,
                                        filterUiState.filter.toName()
                                    )
                                },
                                focusRequester = focusRequester,
                                onValueChange = {
                                    homeViewModel.inputSearchContent(it)
                                },
                                onClose = {
                                    onSearch = false
                                    homeViewModel.inputSearchContent("")
                                }
                            )
                            Spacer(modifier = Modifier.height((56 + 24 + 10).dp))
                        }
                    }
                    ArticleList(
                        pagingItems = pagingItems,
                        diffMap = flowViewModel.diffMap,
                        isShowFeedIcon = articleListFeedIcon.value,
                        isShowStickyHeader = articleListDateStickyHeader.value,
                        articleListTonalElevation = articleListTonalElevation.value,
                        isSwipeEnabled = { listState.isScrollInProgress },
                        onClick = {
                            navController.navigate("${RouteName.READING}/${it.article.id}") {
                                launchSingleTop = true
                            }
                        },
                        onToggleStarred = onToggleStarred,
                        onToggleRead = onToggleRead,
                        onMarkAboveAsRead = onMarkAboveAsRead,
                        onMarkBelowAsRead = onMarkBelowAsRead,
                        onShare = onShare,
                    )
                    item {
                        Spacer(modifier = Modifier.height(128.dp))
                        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        },
        bottomBar = {
            FilterBar(
                filter = filterUiState.filter,
                filterBarStyle = filterBarStyle.value,
                filterBarFilled = filterBarFilled.value,
                filterBarPadding = filterBarPadding.dp,
                filterBarTonalElevation = filterBarTonalElevation.value.dp,
            ) {
                scope.launch {
                    if (flowUiState.listState.firstVisibleItemIndex != 0) {
                        flowUiState.listState.animateScrollToItem(0)
                    }
                }
                if (filterUiState.filter != it) {
                    homeViewModel.changeFilter(filterUiState.copy(filter = it))
                }
            }
        }
    )
}
