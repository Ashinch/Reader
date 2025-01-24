package me.ash.reader.ui.page.home.feeds.drawer.group

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.ui.component.base.RYDialog
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.showToast

@Composable
fun AllOpenInBrowserDialog(
    groupName: String,
    groupOptionViewModel: GroupOptionViewModel = hiltViewModel(),
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val groupOptionUiState = groupOptionViewModel.groupOptionUiState.collectAsStateValue()
    val scope = rememberCoroutineScope()
    val allowToastString = stringResource(R.string.all_open_in_browser_toast, groupName)
    val denyToastString = stringResource(R.string.all_deny_open_in_browser_toast, groupName)

    RYDialog(
        visible = groupOptionUiState.allOpenInBrowserDialogVisible,
        onDismissRequest = {
            groupOptionViewModel.hideAllOpenInBrowserDialog()
        },
        icon = {
            Icon(
                imageVector = Icons.Outlined.OpenInBrowser,
                contentDescription = stringResource(R.string.open_in_browser),
            )
        },
        title = {
            Text(text = stringResource(R.string.open_in_browser))
        },
        text = {
            Text(text = stringResource(R.string.all_open_in_browser_tips, groupName))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    groupOptionViewModel.allOpenInBrowser(true) {
                        groupOptionViewModel.hideAllOpenInBrowserDialog()
                        onConfirm()
                        context.showToast(allowToastString)
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.allow),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    groupOptionViewModel.allOpenInBrowser(false) {
                        groupOptionViewModel.hideAllOpenInBrowserDialog()
                        onConfirm()
                        context.showToast(denyToastString)
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.deny),
                )
            }
        },
    )
}
