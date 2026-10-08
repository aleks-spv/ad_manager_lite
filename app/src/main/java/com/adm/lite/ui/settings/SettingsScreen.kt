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

package com.adm.lite.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adm.lite.BuildConfig
import com.adm.lite.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    factory: ViewModelProvider.Factory,
    viewModel: SettingsViewModel = viewModel(factory = factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Confirmation dialog state
    var showSecureChannelDialog by remember { mutableStateOf(false) }
    var showTrustAllDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val backup = viewModel.exportData()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.use { os ->
                            os.write(backup.toJson().toByteArray())
                        } ?: throw IllegalStateException("Cannot write file")
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_success),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_error, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.use { inp ->
                            inp.bufferedReader().readText()
                        } ?: throw IllegalStateException("Cannot read file")
                    }
                    val count = viewModel.importData(json).getOrThrow()
                    Toast.makeText(
                        context,
                        context.getString(R.string.import_success, count),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.import_error, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── Security Section ──
            SectionHeader(stringResource(R.string.settings_security))

            Spacer(modifier = Modifier.height(8.dp))

            // Require secure channel switch
            SettingsSwitch(
                label = stringResource(R.string.settings_secure_channel),
                checked = state.requireSecureChannelForPassword,
                onCheckedChange = { showSecureChannelDialog = true },
                checkedLabel = "ON",
                uncheckedLabel = "OFF"
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Trust all certificates switch (red accent)
            SettingsSwitch(
                label = stringResource(R.string.settings_trust_all),
                checked = state.trustAllCertificates,
                onCheckedChange = { showTrustAllDialog = true },
                checkedColor = Color.Red
            )

            // Custom CA PEM
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_custom_ca),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = state.customCaPem ?: "",
                onValueChange = { viewModel.setCustomCaPem(it.ifBlank { null }) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----") }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // ── Network Section ──
            SectionHeader(stringResource(R.string.settings_network))

            Spacer(modifier = Modifier.height(8.dp))

            // Connect timeout
            TimeoutField(
                label = stringResource(R.string.settings_connect_timeout),
                value = state.connectTimeoutMs,
                onValueChange = { viewModel.setConnectTimeout(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Response timeout
            TimeoutField(
                label = stringResource(R.string.settings_response_timeout),
                value = state.responseTimeoutMs,
                onValueChange = { viewModel.setResponseTimeout(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // ── Data Section ──
            SectionHeader(stringResource(R.string.settings_data))

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("adm_backup.json") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_export))
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_import))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // ── About Section ──
            SectionHeader(stringResource(R.string.settings_app_version))

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "ADM Lite v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── Confirmation Dialogs ──

    if (showSecureChannelDialog) {
        AlertDialog(
            onDismissRequest = { showSecureChannelDialog = false },
            title = { Text(stringResource(R.string.settings_secure_channel)) },
            text = { Text(stringResource(R.string.settings_secure_channel_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setRequireSecureChannel(!state.requireSecureChannelForPassword)
                    showSecureChannelDialog = false
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecureChannelDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showTrustAllDialog) {
        AlertDialog(
            onDismissRequest = { showTrustAllDialog = false },
            title = { Text(stringResource(R.string.settings_trust_all)) },
            text = { Text(stringResource(R.string.settings_trust_all_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTrustAllCertificates(!state.trustAllCertificates)
                    showTrustAllDialog = false
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrustAllDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    checkedColor: Color = MaterialTheme.colorScheme.primary,
    checkedLabel: String = "ON",
    uncheckedLabel: String = "OFF"
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, fontWeight = FontWeight.Medium)
                Text(
                    text = if (checked) checkedLabel else uncheckedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = checkedColor
                )
            )
        }
    }
}

@Composable
private fun TimeoutField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    // Survives configuration change via saved instance state. The `value` key stays so that an
    // incoming settings change (DataStore finishes loading after the first composition) is still
    // reflected in the field; without it the box would keep showing the default timeout.
    var text by rememberSaveable(value) { mutableStateOf(value.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { newValue ->
                text = newValue
                val parsed = newValue.toIntOrNull()
                error = when {
                    parsed == null && newValue.isNotEmpty() -> "Must be a number"
                    parsed != null && (parsed < 1000 || parsed > 120000) -> "Must be 1000..120000"
                    else -> null
                }
                if (parsed != null && parsed in 1000..120000) {
                    onValueChange(parsed)
                }
            },
            label = { Text(label) },
            suffix = { Text(stringResource(R.string.unit_millis)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = error != null,
            supportingText = error?.let { { Text(it, color = Color.Red) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
    }
}
