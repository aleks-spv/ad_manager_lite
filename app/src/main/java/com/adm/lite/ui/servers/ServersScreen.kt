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

package com.adm.lite.ui.servers

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adm.lite.R
import com.adm.lite.model.AdServer
import com.adm.lite.model.TlsMode
import com.adm.lite.ui.theme.VisibilityIcon
import com.adm.lite.ui.theme.VisibilityOffIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    onServerClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    factory: ViewModelProvider.Factory,
    viewModel: ServersViewModel = viewModel(factory = factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var serverToDelete by remember { mutableStateOf<AdServer?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_servers)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.editing == null) {
                FloatingActionButton(onClick = viewModel::startCreate) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.server_add))
                }
            }
        }
    ) { padding ->
        if (uiState.editing != null) {
            ServerEditor(
                server = uiState.editing!!,
                fieldErrors = uiState.fieldErrors,
                saving = uiState.saving,
                saveError = uiState.saveError,
                onFieldUpdate = viewModel::updateField,
                onTlsModeUpdate = { mode ->
                    viewModel.updateField("tlsMode", mode.name)
                },
                onSave = viewModel::save,
                onCancel = viewModel::cancelEdit,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(uiState.servers, key = { it.id }) { server ->
                    ServerListItem(
                        server = server,
                        onClick = { onServerClick(server.id) },
                        onEdit = { viewModel.startEdit(server) },
                        onDelete = { serverToDelete = server }
                    )
                }
            }
        }
    }

    serverToDelete?.let { server ->
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text(stringResource(R.string.server_delete)) },
            text = { Text(stringResource(R.string.server_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(server)
                    serverToDelete = null
                }) {
                    Text(stringResource(R.string.server_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ServerListItem(
    server: AdServer,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(server.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${server.host}:${server.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    server.tlsMode.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.server_edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.server_delete))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerEditor(
    server: AdServer,
    fieldErrors: Map<String, Int?>,
    saving: Boolean,
    saveError: String?,
    onFieldUpdate: (String, String) -> Unit,
    onTlsModeUpdate: (TlsMode) -> Unit,
    onSave: (String?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = if (server.id == 0L) stringResource(R.string.server_add) else stringResource(R.string.server_edit),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = server.name,
            onValueChange = { onFieldUpdate("name", it) },
            label = { Text(stringResource(R.string.server_name)) },
            singleLine = true,
            isError = fieldErrors["name"] != null,
            supportingText = fieldErrors["name"]?.let { { Text(stringResource(R.string.validation_required)) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = server.host,
            onValueChange = { onFieldUpdate("host", it) },
            label = { Text(stringResource(R.string.server_host)) },
            singleLine = true,
            isError = fieldErrors["host"] != null,
            supportingText = fieldErrors["host"]?.let { { Text(stringResource(R.string.validation_invalid_host)) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        var portExpanded by remember { mutableStateOf(false) }
        val standardPorts = listOf(389, 636, 3268, 3269)
        val portLabels = listOf("LDAP (389)", "LDAPS (636)", "GC (3268)", "GC SSL (3269)")

        ExposedDropdownMenuBox(
            expanded = portExpanded,
            onExpandedChange = { portExpanded = it }
        ) {
            OutlinedTextField(
                value = server.port.toString(),
                onValueChange = { onFieldUpdate("port", it) },
                label = { Text(stringResource(R.string.server_port)) },
                singleLine = true,
                isError = fieldErrors["port"] != null,
                supportingText = fieldErrors["port"]?.let { { Text(stringResource(R.string.validation_invalid_port)) } },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = portExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = portExpanded,
                onDismissRequest = { portExpanded = false }
            ) {
                standardPorts.forEachIndexed { index, port ->
                    DropdownMenuItem(
                        text = { Text(portLabels[index]) },
                        onClick = {
                            onFieldUpdate("port", port.toString())
                            portExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = server.baseDn,
            onValueChange = { onFieldUpdate("baseDn", it) },
            label = { Text(stringResource(R.string.server_base_dn)) },
            singleLine = true,
            isError = fieldErrors["baseDn"] != null,
            supportingText = fieldErrors["baseDn"]?.let { { Text(stringResource(R.string.validation_invalid_dn)) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = server.bindDn,
            onValueChange = { onFieldUpdate("bindDn", it) },
            label = { Text(stringResource(R.string.server_bind_dn)) },
            singleLine = true,
            isError = fieldErrors["bindDn"] != null,
            supportingText = fieldErrors["bindDn"]?.let { { Text(stringResource(R.string.validation_invalid_dn)) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        if (server.bindDn.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.server_password_placeholder)) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                trailingIcon = {
                    val description = if (passwordVisible) "Hide" else "Show"
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) VisibilityOffIcon else VisibilityIcon,
                            contentDescription = description
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(stringResource(R.string.server_tls_mode), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TlsMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index, TlsMode.entries.size),
                    onClick = { onTlsModeUpdate(mode) },
                    selected = server.tlsMode == mode
                ) {
                    Text(mode.name)
                }
            }
        }

        if (server.tlsMode == TlsMode.PLAIN) {
            Text(
                text = stringResource(R.string.plain_text_warning),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (saveError != null) {
            Text(
                text = saveError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_cancel))
            }
            TextButton(
                onClick = { onSave(password) },
                enabled = !saving,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.server_save))
            }
        }
    }
}

