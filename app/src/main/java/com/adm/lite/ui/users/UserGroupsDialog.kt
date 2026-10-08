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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.adm.lite.R
import com.adm.lite.ui.theme.ContentCopyIcon

/**
 * Groups dialog — shows CN only, copy button for DN.
 */
@Composable
internal fun UserGroupsDialog(
    groups: List<String>,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(stringResource(R.string.users_groups)) },
        text = {
            if (groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.groups_empty),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                LazyColumn {
                    items(groups, key = { it }) { groupDn ->
                        val cn = cnFromDn(groupDn)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cn,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(groupDn))
                            }) {
                                Icon(
                                    imageVector = ContentCopyIcon,
                                    contentDescription = stringResource(R.string.action_copy),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

/** Extract CN from a DN string: "CN=Admins,OU=Groups,DC=domain,DC=com" → "Admins" */
private fun cnFromDn(dn: String): String {
    val firstComponent = dn.split(",").firstOrNull()?.trim() ?: return dn
    return if (firstComponent.startsWith("CN=", ignoreCase = true)) {
        firstComponent.substring(3)
    } else {
        firstComponent
    }
}
