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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adm.lite.R
import com.adm.lite.ldap.ops.AccountProperties

@Composable
fun AccountPropertiesDialog(
    properties: AccountProperties,
    onDismiss: () -> Unit,
    onConfirm: (AccountProperties) -> Unit,
    onUnlock: () -> Unit = {}
) {
    var forcePasswordChange by remember { mutableStateOf(properties.forcePasswordChange) }
    var passwordNeverExpires by remember { mutableStateOf(properties.passwordNeverExpires) }
    var userCannotChangePassword by remember { mutableStateOf(properties.userCannotChangePassword) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val conflictMessage = stringResource(R.string.error_password_never_expires_and_force)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.action_account_properties))
        },
        text = {
            Column {
                // Force password change
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.action_force_password_change),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = forcePasswordChange,
                        onCheckedChange = { forcePasswordChange = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Password never expires
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.action_password_never_expires),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = passwordNeverExpires,
                        onCheckedChange = { passwordNeverExpires = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // User cannot change password
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.action_user_cannot_change_password),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = userCannotChangePassword,
                        onCheckedChange = { userCannotChangePassword = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                validationError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Button(
                    onClick = onUnlock,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_unlock_account))
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    if (passwordNeverExpires && forcePasswordChange) {
                        validationError = conflictMessage
                        return@OutlinedButton
                    }
                    validationError = null
                    onConfirm(
                        AccountProperties(
                            forcePasswordChange = forcePasswordChange,
                            passwordNeverExpires = passwordNeverExpires,
                            userCannotChangePassword = userCannotChangePassword
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
