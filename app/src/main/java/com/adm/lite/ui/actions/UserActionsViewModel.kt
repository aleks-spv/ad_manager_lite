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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adm.lite.R
import com.adm.lite.data.prefs.AppSettings
import com.adm.lite.data.prefs.SettingsStore
import com.adm.lite.data.repo.DirectoryRepository
import com.adm.lite.ldap.ops.AccountProperties
import com.adm.lite.ldap.ops.ChangePasswordUseCase
import com.adm.lite.model.AdError
import com.adm.lite.model.AdGroup
import com.adm.lite.model.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UserActionsViewModel(
    private val directory: DirectoryRepository,
    private val settings: SettingsStore
) : ViewModel() {

    sealed interface ActionResult {
        data object Idle : ActionResult
        data object InProgress : ActionResult
        data class Success(val messageKey: Int, val formatArgs: Array<String> = emptyArray()) : ActionResult
        data class Failure(val error: AdError) : ActionResult
    }

    private val _result = MutableStateFlow<ActionResult>(ActionResult.Idle)
    val result: StateFlow<ActionResult> = _result.asStateFlow()

    // Get current settings for UI checks
    val currentSettings: StateFlow<AppSettings> = settings.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings()
    )

    fun changePassword(
        serverId: Long,
        userDn: String,
        newPassword: String
    ) {
        _result.value = ActionResult.InProgress
        viewModelScope.launch {
            val res = directory.changePassword(
                serverId, userDn,
                ChangePasswordUseCase.Mode.ADMIN_RESET,
                null,
                newPassword
            )
            _result.value = res.fold(
                onSuccess = { ActionResult.Success(R.string.action_change_password_ok) },
                onFailure = { e ->
                    val error = e as? AdError ?: AdError(AdError.Kind.UNKNOWN, null, null, e.message)
                    ActionResult.Failure(error)
                }
            )
        }
    }

    fun modifyGroups(userDn: String, addGroupDns: List<String>, removeGroupDns: List<String>) {
        _result.value = ActionResult.InProgress
        viewModelScope.launch {
            val res = directory.modifyGroups(userDn, addGroupDns, removeGroupDns)
            _result.value = res.fold(
                onSuccess = { result ->
                    val applied = result.appliedDns.size
                    val failed = result.failures.size
                    val total = applied + failed
                    when {
                        failed == 0 -> ActionResult.Success(R.string.action_groups_ok)
                        applied > 0 -> ActionResult.Success(
                            R.string.action_groups_partial_ok,
                            arrayOf(applied.toString(), total.toString())
                        )
                        else -> {
                            val error = result.failures.firstOrNull()?.second
                                ?: AdError(AdError.Kind.UNKNOWN, null, null, "Group modification failed")
                            ActionResult.Failure(error)
                        }
                    }
                },
                onFailure = { e ->
                    val error = e as? AdError ?: AdError(AdError.Kind.UNKNOWN, null, null, e.message)
                    ActionResult.Failure(error)
                }
            )
        }
    }

    fun editName(userDn: String, firstName: String?, lastName: String?) {
        _result.value = ActionResult.InProgress
        viewModelScope.launch {
            val res = directory.editName(userDn, firstName, lastName)
            _result.value = res.fold(
                onSuccess = { ActionResult.Success(R.string.action_rename_ok) },
                onFailure = { e ->
                    val error = e as? AdError ?: AdError(AdError.Kind.UNKNOWN, null, null, e.message)
                    ActionResult.Failure(error)
                }
            )
        }
    }

    fun resetResult() { _result.value = ActionResult.Idle }

    /* ---- Account properties ---- */

    private val _accountProperties = MutableStateFlow<AccountProperties?>(null)
    val accountProperties: StateFlow<AccountProperties?> = _accountProperties.asStateFlow()

    fun loadAccountProperties(userDn: String) {
        _result.value = ActionResult.InProgress
        viewModelScope.launch {
            val res = directory.getAccountProperties(userDn)
            res.fold(
                onSuccess = { props ->
                    _accountProperties.value = props
                    _result.value = ActionResult.Idle
                },
                onFailure = { e ->
                    val error = e as? AdError ?: AdError(AdError.Kind.UNKNOWN, null, null, e.message)
                    _result.value = ActionResult.Failure(error)
                }
            )
        }
    }

    fun saveAccountProperties(userDn: String, properties: AccountProperties) {
        _result.value = ActionResult.InProgress
        viewModelScope.launch {
            val res = directory.setAccountProperties(userDn, properties)
            _result.value = res.fold(
                onSuccess = {
                    _accountProperties.value = properties
                    ActionResult.Success(R.string.action_apply_ok)
                },
                onFailure = { e ->
                    val error = e as? AdError ?: AdError(AdError.Kind.UNKNOWN, null, null, e.message)
                    ActionResult.Failure(error)
                }
            )
        }
    }

    fun resetAccountProperties() { _accountProperties.value = null }

    fun unlockAccount(userDn: String) {
        _result.value = ActionResult.InProgress
        viewModelScope.launch {
            val res = directory.unlockAccount(userDn)
            _result.value = res.fold(
                onSuccess = { ActionResult.Success(R.string.action_unlock_ok) },
                onFailure = { e ->
                    val error = e as? AdError ?: AdError(AdError.Kind.UNKNOWN, null, null, e.message)
                    ActionResult.Failure(error)
                }
            )
        }
    }

    /* ---- Search groups ---- */

    private val _groupSearchResults = MutableStateFlow<List<AdGroup>>(emptyList())
    val groupSearchResults: StateFlow<List<AdGroup>> = _groupSearchResults.asStateFlow()

    private val _groupSearchLoading = MutableStateFlow(false)
    val groupSearchLoading: StateFlow<Boolean> = _groupSearchLoading.asStateFlow()

    private val _groupSearchError = MutableStateFlow<AdError?>(null)
    val groupSearchError: StateFlow<AdError?> = _groupSearchError.asStateFlow()

    fun searchGroups(query: String, baseDn: String) {
        if (query.isBlank()) {
            _groupSearchResults.value = emptyList()
            return
        }
        _groupSearchLoading.value = true
        _groupSearchError.value = null
        viewModelScope.launch {
            val res = directory.searchGroups(query, baseDn)
            _groupSearchLoading.value = false
            res.fold(
                onSuccess = { _groupSearchResults.value = it.first },
                onFailure = { e ->
                    val error = e as? AdError
                        ?: AdError(AdError.Kind.UNKNOWN, null, null, e.message)
                    _groupSearchError.value = error
                }
            )
        }
    }

    fun clearGroupSearch() {
        _groupSearchResults.value = emptyList()
        _groupSearchError.value = null
    }

    /* ---- Toggle enabled ---- */

    fun toggleEnabled(userDn: String, enable: Boolean) {
        _result.value = ActionResult.InProgress
        viewModelScope.launch {
            val res = directory.toggleAccountEnabled(userDn, enable)
            _result.value = res.fold(
                onSuccess = { ActionResult.Success(R.string.action_toggle_ok) },
                onFailure = { e ->
                    val error = e as? AdError ?: AdError(AdError.Kind.UNKNOWN, null, null, e.message)
                    ActionResult.Failure(error)
                }
            )
        }
    }
}
