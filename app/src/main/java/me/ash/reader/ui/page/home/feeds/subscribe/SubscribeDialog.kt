package me.ash.reader.ui.page.home.feeds.subscribe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.ui.component.base.ClipboardTextField
import me.ash.reader.ui.component.base.Dialog
import me.ash.reader.ui.component.base.TextFieldDialog
import me.ash.reader.ui.ext.collectAsStateValue

@OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    ExperimentalAnimationApi::class
)
@Composable
fun SubscribeDialog(
    modifier: Modifier = Modifier,
    subscribeViewModel: SubscribeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val subscribeUiState = subscribeViewModel.subscribeUiState.collectAsStateValue()
    val groupsState = subscribeUiState.groups.collectAsState(initial = emptyList())
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let { uri ->
            context.contentResolver.openInputStream(uri)?.let { inputStream ->
                subscribeViewModel.importFromInputStream(inputStream)
            }
        }
    }

    LaunchedEffect(subscribeUiState.visible) {
        if (subscribeUiState.visible) {
            subscribeViewModel.init()
        } else {
            subscribeViewModel.reset()
            subscribeViewModel.switchPage(true)
        }
    }

    Dialog(
        modifier = Modifier.padding(horizontal = 44.dp),
        visible = subscribeUiState.visible,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = {
            focusManager.clearFocus()
            subscribeViewModel.hideDrawer()
        },
        icon = {
            Icon(
                imageVector = Icons.Rounded.RssFeed,
                contentDescription = stringResource(R.string.subscribe),
            )
        },
        title = {
            Text(
                text = if (subscribeUiState.isSearchPage) {
                    subscribeUiState.title
                } else {
                    subscribeUiState.feed?.name ?: stringResource(R.string.unknown)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            AnimatedContent(
                targetState = subscribeUiState.isSearchPage,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() with
                            slideOutHorizontally { width -> -width } + fadeOut()
                }
            ) { targetExpanded ->
                if (targetExpanded) {
                    ClipboardTextField(
                        readOnly = subscribeUiState.lockLinkInput,
                        value = subscribeUiState.linkContent,
                        onValueChange = {
                            subscribeViewModel.inputLink(it)
                        },
                        placeholder = stringResource(R.string.feed_or_site_url),
                        errorText = subscribeUiState.errorMessage,
                        imeAction = ImeAction.Search,
                        focusManager = focusManager,
                        onConfirm = {
                            subscribeViewModel.search()
                        },
                    )
                } else {
                    ResultView(
                        link = subscribeUiState.linkContent,
                        groups = groupsState.value,
                        selectedAllowNotificationPreset = subscribeUiState.allowNotificationPreset,
                        selectedParseFullContentPreset = subscribeUiState.parseFullContentPreset,
                        selectedGroupId = subscribeUiState.selectedGroupId,
                        allowNotificationPresetOnClick = {
                            subscribeViewModel.changeAllowNotificationPreset()
                        },
                        parseFullContentPresetOnClick = {
                            subscribeViewModel.changeParseFullContentPreset()
                        },
                        onGroupClick = {
                            subscribeViewModel.selectedGroup(it)
                        },
                        onAddNewGroup = {
                            subscribeViewModel.showNewGroupDialog()
                        },
                    )
                }
            }
        },
        confirmButton = {
            if (subscribeUiState.isSearchPage) {
                TextButton(
                    enabled = subscribeUiState.linkContent.isNotBlank()
                            && subscribeUiState.title != stringResource(R.string.searching),
                    onClick = {
                        focusManager.clearFocus()
                        subscribeViewModel.search()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.search),
                        color = if (subscribeUiState.linkContent.isNotBlank()) {
                            Color.Unspecified
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                        }
                    )
                }
            } else {
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        subscribeViewModel.subscribe()
                    }
                ) {
                    Text(stringResource(R.string.subscribe))
                }
            }
        },
        dismissButton = {
            if (subscribeUiState.isSearchPage) {
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        launcher.launch("*/*")
                        subscribeViewModel.hideDrawer()
                    }
                ) {
                    Text(text = stringResource(R.string.import_from_opml))
                }
            } else {
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        subscribeViewModel.hideDrawer()
                    }
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        },
    )

    TextFieldDialog(
        visible = subscribeUiState.newGroupDialogVisible,
        title = stringResource(R.string.create_new_group),
        icon = Icons.Outlined.CreateNewFolder,
        value = subscribeUiState.newGroupContent,
        placeholder = stringResource(R.string.name),
        onValueChange = {
            subscribeViewModel.inputNewGroup(it)
        },
        onDismissRequest = {
            subscribeViewModel.hideNewGroupDialog()
        },
        onConfirm = {
            subscribeViewModel.addNewGroup()
        }
    )
}