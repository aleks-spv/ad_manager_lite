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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adm.lite.data.repo.ServerRepository
import com.adm.lite.model.AdServer
import com.adm.lite.model.TlsMode
import com.adm.lite.model.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ServersViewModel(
    private val repository: ServerRepository
) : ViewModel() {

    data class UiState(
        val servers: List<AdServer> = emptyList(),
        val editing: AdServer? = null,
        val fieldErrors: Map<String, Int?> = emptyMap(),
        val saving: Boolean = false,
        val saveError: String? = null
    ) {
        companion object {
            fun newEditing(server: AdServer): UiState = UiState(
                servers = emptyList(),
                editing = server.copy(),
                fieldErrors = emptyMap()
            )
        }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeServers().collect { servers ->
                _uiState.update { it.copy(servers = servers) }
            }
        }
    }

    fun startCreate() {
        _uiState.update {
            it.copy(
                editing = AdServer(
                    id = 0L,
                    name = "",
                    host = "",
                    port = 636,
                    baseDn = "",
                    tlsMode = TlsMode.LDAPS,
                    bindDn = "",
                    rememberPassword = false
                ),
                fieldErrors = emptyMap()
            )
        }
    }

    fun startEdit(server: AdServer) {
        _uiState.update {
            it.copy(
                editing = server.copy(),
                fieldErrors = emptyMap()
            )
        }
    }

    fun updateField(field: String, value: String) {
        val editing = _uiState.value.editing ?: return
        val updated = when (field) {
            "name" -> editing.copy(name = value)
            "host" -> editing.copy(host = value)
            "port" -> editing.copy(port = value.toIntOrNull() ?: 0)
            "baseDn" -> editing.copy(baseDn = value)
            "bindDn" -> editing.copy(bindDn = value)
            "tlsMode" -> editing.copy(tlsMode = TlsMode.valueOf(value))
            "rememberPassword" -> editing.copy(rememberPassword = value.toBooleanStrictOrNull() ?: editing.rememberPassword)
            else -> return
        }
        _uiState.update {
            it.copy(
                editing = updated,
                fieldErrors = it.fieldErrors.toMutableMap().also { map ->
                    map[field] = null
                }
            )
        }
    }

    fun save(password: String?) {
        val editing = _uiState.value.editing ?: return

        val errors = mutableMapOf<String, Int?>()

        val nameError = Validators.validateServerName(editing.name)
        if (nameError != null) errors["name"] = nameError

        val hostError = Validators.validateHost(editing.host)
        if (hostError != null) errors["host"] = hostError

        val portError = Validators.validatePort(editing.port.toString())
        if (portError != null) errors["port"] = portError

        if (editing.baseDn.isBlank()) {
            errors["baseDn"] = com.adm.lite.R.string.error_invalid_base_dn
        }

        if (editing.bindDn.isBlank()) {
            errors["bindDn"] = com.adm.lite.R.string.error_invalid_bind_dn
        }

        val hasErrors = errors.isNotEmpty()
        _uiState.update { it.copy(fieldErrors = errors) }
        if (hasErrors) return

        _uiState.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val rememberPassword = !password.isNullOrBlank()
                val serverToSave = editing.copy(rememberPassword = rememberPassword)
                repository.save(serverToSave, if (rememberPassword) password else null)
                cancelEdit()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        saving = false,
                        saveError = e.localizedMessage ?: e.message ?: "Save failed"
                    )
                }
            }
        }
    }

    fun delete(server: AdServer) {
        viewModelScope.launch {
            repository.delete(server.id)
        }
    }

    fun cancelEdit() {
        _uiState.update {
            it.copy(
                editing = null,
                fieldErrors = emptyMap(),
                saveError = null,
                saving = false
            )
        }
    }
}
