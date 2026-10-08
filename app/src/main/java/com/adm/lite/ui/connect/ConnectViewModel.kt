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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adm.lite.data.repo.DirectoryRepository
import com.adm.lite.data.repo.ServerRepository
import com.adm.lite.ldap.LdapSession
import com.adm.lite.model.AdError
import com.adm.lite.model.AdServer
import com.adm.lite.model.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectViewModel(
    private val serverRepository: ServerRepository,
    private val session: LdapSession,
    private val directoryRepository: DirectoryRepository
) : ViewModel() {

    data class ConnectUiState(
        val server: AdServer? = null,
        val connectionState: ConnectionState = ConnectionState.Disconnected,
        val error: AdError? = null,
        val savedPassword: String? = null,
        val credentialsLost: Boolean = false,
        val shouldNavigateToUsers: Boolean = false
    )

    private val _uiState = MutableStateFlow(ConnectUiState())
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            session.state.collect { state ->
                _uiState.update { current ->
                    val newState = current.copy(connectionState = state)
                    // Clear error when transitioning away from Failed
                    val updatedError = when (state) {
                        is ConnectionState.Connected,
                        is ConnectionState.Disconnected -> null
                        is ConnectionState.Failed -> state.error
                        else -> current.error
                    }
                    if (updatedError != current.error) {
                        newState.copy(error = updatedError)
                    } else {
                        newState
                    }
                }
            }
        }
    }

    fun load(serverId: Long) {
        viewModelScope.launch {
            val server = serverRepository.get(serverId)
            if (server != null) {
                val savedPwd = if (server.rememberPassword) {
                    serverRepository.savedPassword(serverId)
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        server = server,
                        savedPassword = savedPwd,
                        // rememberPassword is set but the stored blob was unreadable
                        // and has been cleared — the user must type the password again.
                        credentialsLost = server.rememberPassword && savedPwd == null
                    )
                }
            }
        }
    }

    fun connect(password: String) {
        viewModelScope.launch {
            val server = uiState.value.server ?: return@launch
            val result = session.bind(server, password)
            if (result.isSuccess) {
                _uiState.update { it.copy(shouldNavigateToUsers = true) }
            }
        }
    }

    fun consumeNavigation() {
        _uiState.update { it.copy(shouldNavigateToUsers = false) }
    }

    fun disconnect() {
        viewModelScope.launch {
            session.disconnect()
        }
    }
}
