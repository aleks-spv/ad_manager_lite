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

package com.adm.lite.ui.connect

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adm.lite.R
import com.adm.lite.model.ConnectionState
import com.adm.lite.ui.theme.ContentCopyIcon
import com.adm.lite.ui.theme.VisibilityIcon
import com.adm.lite.ui.theme.VisibilityOffIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    serverId: Long,
    onConnected: () -> Unit,
    onBack: () -> Unit,
    factory: ViewModelProvider.Factory,
    viewModel: ConnectViewModel = viewModel(factory = factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // When connect() succeeds, navigate to users screen (only once, not on back navigation)
    LaunchedEffect(uiState.shouldNavigateToUsers) {
        if (uiState.shouldNavigateToUsers) {
            viewModel.consumeNavigation()
            onConnected()
        }
    }

    LaunchedEffect(serverId) {
        viewModel.load(serverId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_connect)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            val server = uiState.server
            if (server != null) {
                // Server info card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = server.name,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        InfoRow(stringResource(R.string.server_host), "${server.host}:${server.port}")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        InfoRow(
                            stringResource(R.string.server_tls_mode),
                            formatTlsMode(server.tlsMode)
                        )
                        if (server.tlsMode == com.adm.lite.model.TlsMode.PLAIN) {
                            Text(
                                text = stringResource(R.string.plain_text_warning),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        InfoRow(stringResource(R.string.server_base_dn), server.baseDn)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        InfoRow(stringResource(R.string.server_bind_dn), server.bindDn)
                    }
                }

                // Connection state badge
                if (uiState.connectionState !is ConnectionState.Failed) {
                    ConnectionStateBadge(state = uiState.connectionState)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Password field + action buttons
                ConnectSection(
                    viewModel = viewModel,
                    state = uiState.connectionState,
                    savedPassword = uiState.savedPassword,
                    credentialsLost = uiState.credentialsLost
                )

                // Error display
                val error = uiState.error
                if (error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ErrorCard(error = error)
                }
            } else {
                // Loading state while server data loads
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.Medium)
        Text(text = value, color = Color.Gray)
    }
}

@Composable
private fun ConnectionStateBadge(state: ConnectionState) {
    val (text, color, showLoading) = when (state) {
        is ConnectionState.Disconnected -> Triple(stringResource(R.string.connect_status_disconnected), Color.Gray, false)
        is ConnectionState.Connecting -> Triple(stringResource(R.string.connect_status_connecting), Color.Yellow, true)
        is ConnectionState.Connected -> Triple(String.format(stringResource(R.string.connect_status_connected), state.boundAs), Color.Green, false)
        is ConnectionState.Failed -> Triple(stringResource(R.string.connect_status_failed), Color.Red, false)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
        }
        Text(text = text, color = color)
    }
}

@Composable
private fun ConnectSection(
    viewModel: ConnectViewModel,
    state: ConnectionState,
    savedPassword: String? = null,
    credentialsLost: Boolean = false
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Pre-fill password when saved password loads from credential store
    LaunchedEffect(savedPassword) {
        if (savedPassword != null && password.isEmpty()) {
            password = savedPassword
        }
    }

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(R.string.connect_password)) },
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) VisibilityOffIcon else VisibilityIcon,
                    contentDescription = stringResource(R.string.connect_toggle_password_visibility)
                )
            }
        }
    )

    if (credentialsLost) {
        Text(
            text = stringResource(R.string.error_credentials_reentered),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val canConnect = when (state) {
            is ConnectionState.Disconnected -> true
            is ConnectionState.Failed -> true
            else -> false
        }

        Button(
            onClick = { viewModel.connect(password) },
            enabled = canConnect,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.connect_bind))
        }

        when (state) {
            is ConnectionState.Connected -> {
                Button(
                    onClick = viewModel::disconnect,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.connect_disconnect))
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun ErrorCard(error: com.adm.lite.model.AdError) {
    var expanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = errorKindLabel(error.kind),
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) {
                        stringResource(R.string.error_hide_details)
                    } else {
                        stringResource(R.string.error_show_details)
                    },
                    tint = Color.Gray
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                val errorText = buildString {
                    append(error.rawMessage ?: stringResource(R.string.error_hint_network))
                    error.resultCode?.let { append("\nResult code: $it") }
                    error.hexData?.let { append("\nError code: 0x$it") }
                }

                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.error_hide_details),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(errorText))
                        }
                    ) {
                        Icon(
                            imageVector = ContentCopyIcon,
                            contentDescription = stringResource(R.string.error_copy),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.error_show_details),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun errorKindLabel(kind: com.adm.lite.model.AdError.Kind): String = stringResource(
    when (kind) {
        com.adm.lite.model.AdError.Kind.INVALID_CREDENTIALS -> R.string.error_invalid_credentials
        com.adm.lite.model.AdError.Kind.ACCOUNT_DISABLED -> R.string.error_account_disabled
        com.adm.lite.model.AdError.Kind.ACCOUNT_LOCKED -> R.string.error_account_locked
        com.adm.lite.model.AdError.Kind.PASSWORD_POLICY -> R.string.error_password_policy
        com.adm.lite.model.AdError.Kind.WRONG_OLD_PASSWORD -> R.string.error_wrong_old_password
        com.adm.lite.model.AdError.Kind.MUST_CHANGE_PASSWORD -> R.string.error_must_change_password
        com.adm.lite.model.AdError.Kind.INSUFFICIENT_RIGHTS -> R.string.error_insufficient_rights
        com.adm.lite.model.AdError.Kind.NOT_FOUND -> R.string.error_not_found
        com.adm.lite.model.AdError.Kind.INSECURE_CHANNEL -> R.string.error_insecure_channel
        com.adm.lite.model.AdError.Kind.TIMEOUT -> R.string.error_timeout
        com.adm.lite.model.AdError.Kind.VALIDATION -> R.string.error_invalid_new_cn
        com.adm.lite.model.AdError.Kind.SERVER_MISMATCH -> R.string.error_server_mismatch
        com.adm.lite.model.AdError.Kind.NETWORK -> R.string.error_hint_network
        com.adm.lite.model.AdError.Kind.TLS -> R.string.error_hint_tls
        com.adm.lite.model.AdError.Kind.UNKNOWN -> R.string.connect_status_failed
    }
)

private fun formatTlsMode(tlsMode: com.adm.lite.model.TlsMode): String {
    return when (tlsMode) {
        com.adm.lite.model.TlsMode.LDAPS -> "LDAPS"
        com.adm.lite.model.TlsMode.START_TLS -> "START_TLS"
        com.adm.lite.model.TlsMode.PLAIN -> "PLAIN"
    }
}
