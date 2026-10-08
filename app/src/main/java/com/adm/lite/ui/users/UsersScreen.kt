/*
 * Copyright 2026 Alexandr <aleks.spv@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.adm.lite.ui.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adm.lite.ui.theme.ContentCopyIcon
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adm.lite.R
import com.adm.lite.model.AdError
import com.adm.lite.model.AdUser
import com.adm.lite.ui.actions.AccountPropertiesDialog
import com.adm.lite.ui.actions.ChangePasswordDialog
import com.adm.lite.ui.actions.ModifyGroupsDialog
import com.adm.lite.ui.actions.RenameUserDialog
import com.adm.lite.ui.actions.UserActionsViewModel
import com.adm.lite.ui.actions.UserActionsViewModel.ActionResult
import com.adm.lite.ldap.ops.AccountProperties

sealed interface UserAction {
    object ChangePassword : UserAction
    object ModifyGroups : UserAction
    object RenameUser : UserAction
    object Properties : UserAction
    object ToggleEnabled : UserAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    factory: ViewModelProvider.Factory,
    viewModel: UsersViewModel = viewModel(factory = factory),
    actionsViewModel: UserActionsViewModel = viewModel(factory = factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionsSettings by actionsViewModel.currentSettings.collectAsStateWithLifecycle()
    val actionResult by actionsViewModel.result.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedAction by remember { mutableStateOf<UserAction?>(null) }

    // Refresh user list after any successful action (toggle, rename, password change, etc.)
    LaunchedEffect(actionResult) {
        when (val r = actionResult) {
            is ActionResult.Success -> {
                val message = if (r.formatArgs.isEmpty()) {
                    context.getString(r.messageKey)
                } else {
                    context.getString(r.messageKey, *r.formatArgs)
                }
                android.widget.Toast.makeText(
                    context,
                    message,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                viewModel.refreshUsers()
                actionsViewModel.resetResult()
            }
            is ActionResult.Failure -> {
                android.widget.Toast.makeText(
                    context,
                    r.error.message ?: "Error",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                actionsViewModel.resetResult()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_users)) },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search field — acts as local filter, keyboard stays visible
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.users_search_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 8.dp),
                shape = RoundedCornerShape(50),
                singleLine = true,
                enabled = uiState.connected && !uiState.loading
            )

            val isConnected = uiState.connected

            // Content area
            Column(modifier = Modifier.padding(16.dp)) {
                // Not connected message
                if (!uiState.connected) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.connect_status_disconnected),
                                color = Color.DarkGray,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Error card
                uiState.error?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    ErrorCard(error = error)
                }

                // Loading indicator
                if (uiState.loading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // Results list or empty state
                if (!uiState.loading) {
                    if (isConnected && uiState.users.isEmpty()) {
                        Text(
                            text = stringResource(R.string.users_no_results),
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    } else if (isConnected) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(uiState.users, key = { it.dn }) { user ->
                                UserItem(user = user, onClick = viewModel::selectUser)
                            }
                        }

                        // Truncated warning + explicit paging
                        if (uiState.truncated) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = stringResource(R.string.users_truncated),
                                        color = Color.DarkGray,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (uiState.hasMore) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = viewModel::loadMore,
                                            enabled = !uiState.loading,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(text = stringResource(R.string.users_load_more))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected user bottom sheet
            val selectedUser = uiState.selected
            if (selectedUser != null) {
                UserDetailSheet(
                    user = selectedUser,
                    onAction = { selectedAction = it },
                    onDismissRequest = viewModel::clearSelection
                )
            }
        }
    }

    val selectedUser = uiState.selected

    when (selectedAction) {
        is UserAction.ChangePassword -> {
            if (selectedUser != null) {
                ChangePasswordDialog(
                    userDn = selectedUser.dn,
                    serverId = viewModel.currentServerId,
                    requireSecureChannel = actionsSettings.requireSecureChannelForPassword,
                    onDismiss = { selectedAction = null },
                    onConfirm = { srvId, usrDn, pwd ->
                        actionsViewModel.changePassword(srvId, usrDn, pwd)
                        selectedAction = null
                    }
                )
            }
        }
        is UserAction.ModifyGroups -> {
            if (selectedUser != null) {
                val groupSearchResults by actionsViewModel.groupSearchResults.collectAsStateWithLifecycle()
                val groupSearchLoading by actionsViewModel.groupSearchLoading.collectAsStateWithLifecycle()
                val groupSearchError by actionsViewModel.groupSearchError.collectAsStateWithLifecycle()
                LaunchedEffect(selectedAction) {
                    actionsViewModel.clearGroupSearch()
                }
                ModifyGroupsDialog(
                    userDn = selectedUser.dn,
                    currentGroups = selectedUser.groups,
                    baseDn = uiState.serverBaseDn,
                    searchResults = groupSearchResults,
                    searchLoading = groupSearchLoading,
                    searchError = groupSearchError,
                    onSearchGroups = { query -> actionsViewModel.searchGroups(query, uiState.serverBaseDn) },
                    onClearSearch = { actionsViewModel.clearGroupSearch() },
                    onDismiss = {
                        actionsViewModel.clearGroupSearch()
                        selectedAction = null
                    },
                    onConfirm = { userDn, add, remove ->
                        actionsViewModel.modifyGroups(userDn, add, remove)
                        actionsViewModel.clearGroupSearch()
                        selectedAction = null
                    }
                )
            }
        }
        is UserAction.RenameUser -> {
            if (selectedUser != null) {
                RenameUserDialog(
                    currentFirstName = selectedUser.givenName ?: "",
                    currentLastName = selectedUser.sn ?: "",
                    onDismiss = { selectedAction = null },
                    onConfirm = { firstName, lastName ->
                        actionsViewModel.editName(selectedUser.dn, firstName, lastName)
                        selectedAction = null
                    }
                )
            }
        }
        is UserAction.Properties -> {
            if (selectedUser != null) {
                LaunchedEffect(selectedUser.dn) {
                    actionsViewModel.loadAccountProperties(selectedUser.dn)
                }
                val props by actionsViewModel.accountProperties.collectAsStateWithLifecycle()
                if (props != null) {
                    AccountPropertiesDialog(
                        properties = props!!,
                        onDismiss = {
                            actionsViewModel.resetAccountProperties()
                            selectedAction = null
                        },
                        onConfirm = { propsToSave ->
                            actionsViewModel.saveAccountProperties(selectedUser.dn, propsToSave)
                            actionsViewModel.resetAccountProperties()
                            selectedAction = null
                        },
                        onUnlock = {
                            actionsViewModel.unlockAccount(selectedUser.dn)
                            actionsViewModel.resetAccountProperties()
                            selectedAction = null
                        }
                    )
                }
            }
        }
        is UserAction.ToggleEnabled -> {
            if (selectedUser != null) {
                val enableLabel = if (selectedUser.enabled) {
                    stringResource(R.string.action_confirm_disable)
                } else {
                    stringResource(R.string.action_confirm_enable)
                }
                AlertDialog(
                    onDismissRequest = { selectedAction = null },
                    title = { Text(stringResource(R.string.action_toggle_enabled)) },
                    text = { Text(enableLabel) },
                    confirmButton = {
                        TextButton(onClick = {
                            actionsViewModel.toggleEnabled(selectedUser.dn, !selectedUser.enabled)
                            selectedAction = null
                        }) {
                            Text(stringResource(R.string.action_apply))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedAction = null }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }
        null -> { /* no-op */ }
    }
}

@Composable
private fun UserItem(user: AdUser, onClick: (AdUser) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(user) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName ?: user.sAMAccountName ?: "",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (user.sAMAccountName != null && (user.displayName == null || user.displayName != user.sAMAccountName)) {
                    Text(
                        text = user.sAMAccountName,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Normal
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!user.enabled) {
                    Text(
                        text = stringResource(R.string.users_disabled),
                        color = Color.Red,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (user.lockedOut) {
                    Text(
                        text = stringResource(R.string.users_locked),
                        color = Color.Red,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(error: AdError) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(errorToDisplayString(error.kind)),
                fontWeight = FontWeight.Bold,
                color = Color.Red
            )
            Spacer(modifier = Modifier.height(8.dp))
            val rawMsg = error.rawMessage ?: ""
            if (rawMsg.isNotEmpty()) {
                Text(text = rawMsg, color = Color.DarkGray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserDetailSheet(
    user: AdUser,
    onAction: (UserAction) -> Unit,
    onDismissRequest: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var showGroupsDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Header: "Пользователь"
            Text(
                text = stringResource(R.string.users_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            // displayName — bold
            Text(
                text = user.displayName ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Login row with copy
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.users_login),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = user.sAMAccountName ?: "",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = {
                    val text = user.sAMAccountName ?: return@IconButton
                    clipboardManager.setText(AnnotatedString(text))
                }) {
                    Icon(
                        imageVector = ContentCopyIcon,
                        contentDescription = stringResource(R.string.action_copy),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Email row with copy (only if present)
            if (!user.mail.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.users_mail),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = user.mail,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(user.mail))
                    }) {
                        Icon(
                            imageVector = ContentCopyIcon,
                            contentDescription = stringResource(R.string.action_copy),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Groups — button to open dialog
            Button(
                onClick = { showGroupsDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.membership_groups, user.groups.size),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status — single line
            val statusColor = when {
                !user.enabled -> Color(0xFFF44336)
                user.lockedOut -> Color(0xFFF44336)
                else -> Color(0xFF4CAF50)
            }
            val statusText = buildString {
                append(
                    if (user.enabled) stringResource(R.string.users_enabled)
                    else stringResource(R.string.users_disabled)
                )
                append("\u00A0\u00B7\u00A0")
                append(
                    if (user.lockedOut) stringResource(R.string.users_locked)
                    else stringResource(R.string.users_unlocked)
                )
            }
            Text(
                text = statusText,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons — full-width vertical stack
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onAction(UserAction.ChangePassword) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_change_password))
                }
                Button(
                    onClick = { onAction(UserAction.ModifyGroups) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_modify_groups))
                }
                Button(
                    onClick = { onAction(UserAction.RenameUser) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_rename_user))
                }
                Button(
                    onClick = { onAction(UserAction.ToggleEnabled) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (user.enabled) stringResource(R.string.action_toggle_disable)
                        else stringResource(R.string.action_toggle_enable)
                    )
                }
                Button(
                    onClick = { onAction(UserAction.Properties) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_account_properties))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showGroupsDialog) {
        UserGroupsDialog(
            groups = user.groups,
            onDismiss = { showGroupsDialog = false }
        )
    }
}

/** Map error kind to a display string resource. */
private fun errorToDisplayString(kind: AdError.Kind): Int = when (kind) {
    AdError.Kind.INVALID_CREDENTIALS -> R.string.error_invalid_credentials
    AdError.Kind.ACCOUNT_DISABLED -> R.string.error_account_disabled
    AdError.Kind.ACCOUNT_LOCKED -> R.string.error_account_locked
    AdError.Kind.PASSWORD_POLICY -> R.string.error_password_policy
    AdError.Kind.WRONG_OLD_PASSWORD -> R.string.error_wrong_old_password
    AdError.Kind.MUST_CHANGE_PASSWORD -> R.string.error_must_change_password
    AdError.Kind.INSUFFICIENT_RIGHTS -> R.string.error_insufficient_rights
    AdError.Kind.NOT_FOUND -> R.string.error_not_found
    AdError.Kind.INSECURE_CHANNEL -> R.string.error_insecure_channel
    AdError.Kind.TIMEOUT -> R.string.error_timeout
    AdError.Kind.VALIDATION -> R.string.error_invalid_new_cn
    AdError.Kind.SERVER_MISMATCH -> R.string.error_server_mismatch
    AdError.Kind.NETWORK -> R.string.error_hint_network
    AdError.Kind.TLS -> R.string.error_hint_tls
    AdError.Kind.UNKNOWN -> R.string.error_hint_network
}
