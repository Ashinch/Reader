package me.ash.reader.ui.page.home.feeds.drawer.feed

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalOpenLink
import me.ash.reader.infrastructure.preference.LocalOpenLinkSpecificBrowser
import me.ash.reader.ui.component.ChangeUrlDialog
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.component.RenameDialog
import me.ash.reader.ui.component.base.BottomDrawer
import me.ash.reader.ui.component.base.TextFieldDialog
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.openURL
import me.ash.reader.ui.ext.roundClick
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.page.home.feeds.FeedOptionView

@Composable
fun FeedOptionDrawer(
    drawerState: ModalBottomSheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden),
    feedOptionViewModel: FeedOptionViewModel = hiltViewModel(),
    content: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current
    val scope = rememberCoroutineScope()
    val feedOptionUiState = feedOptionViewModel.feedOptionUiState.collectAsStateValue()
    val feed = feedOptionUiState.feed
    val toastString = stringResource(R.string.rename_toast, feedOptionUiState.newName)


    BackHandler(drawerState.isVisible) {
        scope.launch {
            drawerState.hide()
        }
    }

    BottomDrawer(
        drawerState = drawerState,
        sheetContent = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    FeedIcon(modifier = Modifier.clickable {
                        feedOptionViewModel.reloadIcon()
                    }, feedName = feed?.name, iconUrl = feed?.icon, size = 24.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        modifier = Modifier.roundClick {
                            if (feedOptionViewModel.rssService.get().updateSubscription) {
                                feedOptionViewModel.showRenameDialog()
                            }
                        },
                        text = feed?.name ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                FeedOptionView(
                    link = feed?.url ?: stringResource(R.string.unknown),
                    groups = feedOptionUiState.groups,
                    selectedAllowNotificationPreset = feedOptionUiState.feed?.isNotification
                        ?: false,
                    selectedParseFullContentPreset = feedOptionUiState.feed?.isFullContent ?: false,
                    selectedOpenInBrowserPreset = feedOptionUiState.feed?.isBrowser ?: false,
                    isMoveToGroup = true,
                    showGroup = feedOptionViewModel.rssService.get().moveSubscription,
                    showUnsubscribe = feedOptionViewModel.rssService.get().deleteSubscription,
                    notSubscribeMode = true,
                    selectedGroupId = feedOptionUiState.feed?.groupId ?: "",
                    allowNotificationPresetOnClick = {
                        feedOptionViewModel.changeAllowNotificationPreset()
                    },
                    parseFullContentPresetOnClick = {
                        feedOptionViewModel.changeParseFullContentPreset()
                    },
                    openInBrowserPresetOnClick = {
                        feedOptionViewModel.changeOpenInBrowserPreset()
                    },
                    clearArticlesOnClick = {
                        feedOptionViewModel.showClearDialog()
                    },
                    unsubscribeOnClick = {
                        feedOptionViewModel.showDeleteDialog()
                    },
                    onGroupClick = {
                        feedOptionViewModel.selectedGroup(it)
                    },
                    onAddNewGroup = {
                        feedOptionViewModel.showNewGroupDialog()
                    },
                    onFeedUrlClick = {
                        context.openURL(feed?.url, openLink, openLinkSpecificBrowser)
                    },
                    onFeedUrlLongClick = {
                        if (feedOptionViewModel.rssService.get().updateSubscription) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            feedOptionViewModel.showFeedUrlDialog()
                        }
                    }
                )
            }
        }
    ) {
        content()
    }

    DeleteFeedDialog(
        feedName = feed?.name ?: "",
        onConfirm = { scope.launch { drawerState.hide() } })

    ClearFeedDialog(
        feedName = feed?.name ?: "",
        onConfirm = { scope.launch { drawerState.hide() } })

    TextFieldDialog(
        visible = feedOptionUiState.newGroupDialogVisible,
        title = stringResource(R.string.create_new_group),
        icon = Icons.Outlined.CreateNewFolder,
        value = feedOptionUiState.newGroupContent,
        placeholder = stringResource(R.string.name),
        onValueChange = {
            feedOptionViewModel.inputNewGroup(it)
        },
        onDismissRequest = {
            feedOptionViewModel.hideNewGroupDialog()
        },
        onConfirm = {
            feedOptionViewModel.addNewGroup()
        }
    )

    RenameDialog(
        visible = feedOptionUiState.renameDialogVisible,
        value = feedOptionUiState.newName,
        onValueChange = {
            feedOptionViewModel.inputNewName(it)
        },
        onDismissRequest = {
            feedOptionViewModel.hideRenameDialog()
        },
        onConfirm = {
            feedOptionViewModel.renameFeed()
            scope.launch { drawerState.hide() }
            context.showToast(toastString)
        }
    )

    ChangeUrlDialog(
        visible = feedOptionUiState.changeUrlDialogVisible,
        value = feedOptionUiState.newUrl,
        onValueChange = {
            feedOptionViewModel.inputNewUrl(it)
        },
        onDismissRequest = {
            feedOptionViewModel.hideFeedUrlDialog()
        },
        onConfirm = {
            feedOptionViewModel.changeFeedUrl()
            scope.launch { drawerState.hide() }
        }
    )
}
