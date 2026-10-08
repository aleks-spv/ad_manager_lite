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

package com.adm.lite.ui.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adm.lite.R

private enum class GroupMode { MENU, ADD, REMOVE }

@Composable
fun ModifyGroupsDialog(
    userDn: String,
    currentGroups: List<String>,
    baseDn: String,
    searchResults: List<com.adm.lite.model.AdGroup>,
    searchLoading: Boolean,
    searchError: com.adm.lite.model.AdError? = null,
    onSearchGroups: (String) -> Unit,
    onClearSearch: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (userDn: String, add: List<String>, remove: List<String>) -> Unit
) {
    var mode by remember { mutableStateOf(GroupMode.MENU) }
    var addSearchQuery by rememberSaveable { mutableStateOf("") }
    val groupsToRemove = remember { mutableStateOf<Set<String>>(emptySet()) }
    val groupsToAdd = remember { mutableStateOf<Set<String>>(emptySet()) }

    AlertDialog(
        onDismissRequest = {
            onClearSearch()
            onDismiss()
        },
        title = {
            Text(
                when (mode) {
                    GroupMode.MENU -> stringResource(R.string.action_modify_groups)
                    GroupMode.ADD -> stringResource(R.string.action_add_group)
                    GroupMode.REMOVE -> stringResource(R.string.action_remove_group)
                }
            )
        },
        text = {
            when (mode) {
                GroupMode.MENU -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { mode = GroupMode.ADD },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.action_add_group))
                        }
                        OutlinedButton(
                            onClick = { mode = GroupMode.REMOVE },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.action_remove_group))
                        }
                    }
                }

                GroupMode.ADD -> {
                    Column {
                        OutlinedTextField(
                            value = addSearchQuery,
                            onValueChange = {
                                addSearchQuery = it
                                onSearchGroups(it)
                            },
                            label = { Text(stringResource(R.string.action_search_groups)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (searchLoading) {
                            Text(
                                text = stringResource(R.string.action_searching),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (searchError != null) {
                            Text(
                                text = searchError.rawMessage
                                    ?: stringResource(R.string.error_unknown),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(searchResults, key = { it.dn }) { group ->
                                val groupCn = group.dn
                                    .split(",")
                                    .firstOrNull()
                                    ?.substringAfter("=")
                                    ?.ifBlank { group.name }
                                    ?: group.name
                                val isAdded = groupsToAdd.value.contains(group.dn)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isAdded) groupsToAdd.value = groupsToAdd.value - group.dn
                                            else groupsToAdd.value = groupsToAdd.value + group.dn
                                        }
                                ) {
                                    Checkbox(
                                        checked = isAdded,
                                        onCheckedChange = null
                                    )
                                    Text(
                                        text = groupCn,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                GroupMode.REMOVE -> {
                    if (currentGroups.isEmpty()) {
                        Text(
                            text = stringResource(R.string.groups_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(currentGroups, key = { it }) { groupDn ->
                                val cn = groupDn
                                    .split(",")
                                    .firstOrNull()
                                    ?.substringAfter("=")
                                    ?.ifBlank { groupDn }
                                    ?: groupDn
                                val isRemoved = groupsToRemove.value.contains(groupDn)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isRemoved) groupsToRemove.value = groupsToRemove.value - groupDn
                                            else groupsToRemove.value = groupsToRemove.value + groupDn
                                        }
                                ) {
                                    Checkbox(
                                        checked = isRemoved,
                                        onCheckedChange = null
                                    )
                                    Text(
                                        text = cn,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (mode) {
                GroupMode.MENU -> {
                    // No confirm on menu
                }
                GroupMode.ADD -> {
                    OutlinedButton(
                        onClick = {
                            onConfirm(userDn, groupsToAdd.value.toList(), emptyList())
                            onClearSearch()
                        },
                        enabled = groupsToAdd.value.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.action_apply))
                    }
                }
                GroupMode.REMOVE -> {
                    OutlinedButton(
                        onClick = {
                            onConfirm(userDn, emptyList(), groupsToRemove.value.toList())
                        },
                        enabled = groupsToRemove.value.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.action_apply))
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = {
                when (mode) {
                    GroupMode.MENU -> {
                        onClearSearch()
                        onDismiss()
                    }
                    else -> {
                        groupsToAdd.value = emptySet()
                        groupsToRemove.value = emptySet()
                        onClearSearch()
                        addSearchQuery = ""
                        mode = GroupMode.MENU
                    }
                }
            }) {
                Text(
                    when (mode) {
                        GroupMode.MENU -> stringResource(R.string.action_cancel)
                        else -> stringResource(R.string.action_back)
                    }
                )
            }
        }
    )
}
