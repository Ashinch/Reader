package me.ash.reader.ui.page.home.feeds

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import me.ash.reader.R
import me.ash.reader.domain.model.group.Group
import me.ash.reader.ui.component.base.RYSelectionChip
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.theme.palette.alwaysLight

@Composable
fun FeedOptionView(
    modifier: Modifier = Modifier,
    link: String = "",
    groups: List<Group> = emptyList(),
    selectedAllowNotificationPreset: Boolean = false,
    selectedParseFullContentPreset: Boolean = false,
    isMoveToGroup: Boolean = false,
    showGroup: Boolean = true,
    showUnsubscribe: Boolean = true,
    notSubscribeMode: Boolean = false,
    selectedGroupId: String = "",
    allowNotificationPresetOnClick: () -> Unit = {},
    parseFullContentPresetOnClick: () -> Unit = {},
    clearArticlesOnClick: () -> Unit = {},
    unsubscribeOnClick: () -> Unit = {},
    onGroupClick: (groupId: String) -> Unit = {},
    onAddNewGroup: () -> Unit = {},
    onFeedUrlClick: () -> Unit = {},
    onFeedUrlLongClick: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        if (groups.isNotEmpty() && selectedGroupId.isEmpty()) onGroupClick(groups.first().id)
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        EditableUrl(
            text = link,
            onClick = onFeedUrlClick,
            onLongClick = onFeedUrlLongClick,
        )
        Spacer(modifier = Modifier.height(26.dp))

        Preset(
            selectedAllowNotificationPreset = selectedAllowNotificationPreset,
            selectedParseFullContentPreset = selectedParseFullContentPreset,
            showUnsubscribe = showUnsubscribe,
            notSubscribeMode = notSubscribeMode,
            allowNotificationPresetOnClick = allowNotificationPresetOnClick,
            parseFullContentPresetOnClick = parseFullContentPresetOnClick,
            clearArticlesOnClick = clearArticlesOnClick,
            unsubscribeOnClick = unsubscribeOnClick,
        )

        if (showGroup) {
            Spacer(modifier = Modifier.height(26.dp))

            AddToGroup(
                isMoveToGroup = isMoveToGroup,
                groups = groups,
                selectedGroupId = selectedGroupId,
                onGroupClick = onGroupClick,
                onAddNewGroup = onAddNewGroup,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditableUrl(
    text: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            text = text,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Preset(
    selectedAllowNotificationPreset: Boolean = false,
    selectedParseFullContentPreset: Boolean = false,
    showUnsubscribe: Boolean = true,
    notSubscribeMode: Boolean = false,
    allowNotificationPresetOnClick: () -> Unit = {},
    parseFullContentPresetOnClick: () -> Unit = {},
    clearArticlesOnClick: () -> Unit = {},
    unsubscribeOnClick: () -> Unit = {},
) {
    Subtitle(text = stringResource(R.string.preset))
    Spacer(modifier = Modifier.height(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        RYSelectionChip(
            modifier = Modifier.animateContentSize(),
            content = stringResource(R.string.allow_notification),
            selected = selectedAllowNotificationPreset,
            selectedIcon = {
                Icon(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp),
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = stringResource(R.string.allow_notification),
                    tint = MaterialTheme.colorScheme.onSurface alwaysLight true,
                )
            },
        ) {
            allowNotificationPresetOnClick()
        }
        RYSelectionChip(
            modifier = Modifier.animateContentSize(),
            content = stringResource(R.string.parse_full_content),
            selected = selectedParseFullContentPreset,
            selectedIcon = {
                Icon(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp),
                    imageVector = Icons.AutoMirrored.Outlined.Article,
                    contentDescription = stringResource(R.string.parse_full_content),
                    tint = MaterialTheme.colorScheme.onSurface alwaysLight true,
                )
            },
        ) {
            parseFullContentPresetOnClick()
        }
        if (notSubscribeMode) {
            RYSelectionChip(
                modifier = Modifier.animateContentSize(),
                content = stringResource(R.string.clear_articles),
                selected = false,
            ) {
                clearArticlesOnClick()
            }
            if (showUnsubscribe) {
                RYSelectionChip(
                    modifier = Modifier.animateContentSize(),
                    content = stringResource(R.string.unsubscribe),
                    selected = false,
                ) {
                    unsubscribeOnClick()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddToGroup(
    isMoveToGroup: Boolean = false,
    groups: List<Group>,
    selectedGroupId: String,
    onGroupClick: (groupId: String) -> Unit = {},
    onAddNewGroup: () -> Unit = {},
) {
    Subtitle(text = stringResource(if (isMoveToGroup) R.string.move_to_group else R.string.add_to_group))
    Spacer(modifier = Modifier.height(10.dp))

    if (groups.size > 6) {
        LazyRow(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(groups) {
                RYSelectionChip(
                    modifier = Modifier.animateContentSize(),
                    content = it.name,
                    selected = it.id == selectedGroupId,
                ) {
                    onGroupClick(it.id)
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            item {
                NewGroupButton(
                    onAddNewGroup,
                    Modifier,
                )
            }
        }
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            groups.forEach {
                RYSelectionChip(
                    modifier = Modifier.animateContentSize(),
                    content = it.name,
                    selected = it.id == selectedGroupId,
                ) {
                    onGroupClick(it.id)
                }
            }
            NewGroupButton(
                onAddNewGroup,
                Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun NewGroupButton(onAddNewGroup: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onAddNewGroup() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = Icons.Outlined.Add,
            contentDescription = stringResource(R.string.create_new_group),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}
