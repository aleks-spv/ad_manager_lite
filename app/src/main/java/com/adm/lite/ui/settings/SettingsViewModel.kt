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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adm.lite.data.BackupData
import com.adm.lite.data.db.toDomain
import com.adm.lite.data.db.toEntity
import com.adm.lite.data.prefs.SettingsStore
import com.adm.lite.data.prefs.AppSettings
import com.adm.lite.data.repo.ServerRepository
import com.adm.lite.model.TlsMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsStore,
    private val serverRepository: ServerRepository
) : ViewModel() {

    val state: StateFlow<AppSettings> = settings.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettings()
    )

    fun setRequireSecureChannel(value: Boolean) {
        viewModelScope.launch { settings.setRequireSecureChannel(value) }
    }

    fun setTrustAllCertificates(value: Boolean) {
        viewModelScope.launch { settings.setTrustAllCertificates(value) }
    }

    fun setCustomCaPem(pem: String?) {
        viewModelScope.launch { settings.setCustomCaPem(pem) }
    }

    fun setConnectTimeout(ms: Int) {
        viewModelScope.launch { settings.setConnectTimeout(ms) }
    }

    fun setResponseTimeout(ms: Int) {
        viewModelScope.launch { settings.setResponseTimeout(ms) }
    }

    /**
     * Snapshot of the current settings and every stored server, without credentials.
     */
    suspend fun exportData(): BackupData = BackupData(
        settings = settings.settings.first(),
        servers = serverRepository.observeServers().first().map { it.toEntity().copy(rememberPassword = false) }
    )

    /**
     * Applies [json] produced by [exportData]: settings are overwritten and servers
     * are upserted by name. Passwords are never restored.
     *
     * @return the number of servers written, or the parse/write failure.
     */
    suspend fun importData(json: String): Result<Int> = runCatching {
        val backup = BackupData.fromJson(json)

        settings.setRequireSecureChannel(backup.settings.requireSecureChannelForPassword)
        settings.setConnectTimeout(backup.settings.connectTimeoutMs)
        settings.setResponseTimeout(backup.settings.responseTimeoutMs)
        settings.setCustomCaPem(backup.settings.customCaPem)
        settings.setTrustAllCertificates(backup.settings.trustAllCertificates)

        val existingByName = serverRepository.observeServers().first().associateBy { it.name }
        var imported = 0
        for (entity in backup.servers) {
            val safe = entity.copy(
                tlsMode = if (TlsMode.entries.any { it.name == entity.tlsMode }) {
                    entity.tlsMode
                } else {
                    TlsMode.PLAIN.name
                },
                rememberPassword = false
            )
            val existing = existingByName[safe.name]
            val toSave = if (existing != null) {
                safe.copy(id = existing.id)
            } else {
                safe
            }
            serverRepository.save(toSave.toDomain(), null)
            imported++
        }
        imported
    }

    /** Removes every stored server together with its saved credentials. */
    suspend fun clearAllServers() {
        serverRepository.observeServers().first().forEach { serverRepository.delete(it.id) }
    }
}
