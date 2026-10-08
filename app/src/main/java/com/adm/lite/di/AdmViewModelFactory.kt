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

package com.adm.lite.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.adm.lite.ui.actions.UserActionsViewModel
import com.adm.lite.ui.connect.ConnectViewModel
import com.adm.lite.ui.servers.ServersViewModel
import com.adm.lite.ui.settings.SettingsViewModel
import com.adm.lite.ui.users.UsersViewModel

/**
 * Single ViewModelProvider.Factory that creates every ViewModel from [AppContainer].
 *
 * Replaces the old companion-object-per-ViewModel static-init pattern.
 * One instance is created in [com.adm.lite.MainActivity] and threaded through
 * [com.adm.lite.ui.nav.AdmNavGraph] → individual screens.
 */
class AdmViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm: ViewModel = when {
            modelClass.isAssignableFrom(ConnectViewModel::class.java) ->
                ConnectViewModel(
                    container.serverRepository,
                    container.ldapSession,
                    container.directoryRepository
                )
            modelClass.isAssignableFrom(UsersViewModel::class.java) ->
                UsersViewModel(
                    container.directoryRepository,
                    container.serverRepository,
                    container.ldapSession
                )
            modelClass.isAssignableFrom(UserActionsViewModel::class.java) ->
                UserActionsViewModel(
                    container.directoryRepository,
                    container.settingsStore
                )
            modelClass.isAssignableFrom(ServersViewModel::class.java) ->
                ServersViewModel(
                    container.serverRepository
                )
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(
                    container.settingsStore,
                    container.serverRepository
                )
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return vm as T
    }
}
