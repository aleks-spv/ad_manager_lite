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

package com.adm.lite.data.repo

import com.adm.lite.data.prefs.SettingsStore
import com.adm.lite.ldap.LdapSession
import com.adm.lite.ldap.ops.AccountProperties
import com.adm.lite.ldap.ops.AccountPropertiesUseCase
import com.adm.lite.ldap.ops.ChangePasswordUseCase
import com.adm.lite.ldap.ops.ModifyGroupsResult
import com.adm.lite.ldap.ops.ModifyGroupsUseCase
import com.adm.lite.ldap.ops.EditNameUseCase
import com.adm.lite.ldap.ops.SearchGroupsUseCase
import com.adm.lite.ldap.ops.SearchUsersUseCase
import com.adm.lite.ldap.ops.ToggleAccountEnabledUseCase
import com.adm.lite.ldap.ops.UnlockAccountUseCase
import com.adm.lite.model.AdError
import com.adm.lite.model.AdUser
import com.adm.lite.model.TlsMode

/**
 * Facade over LDAP session and use cases. Enforces app-level security constraints
 * (e.g., secure channel requirement for password changes) before delegating to LDAP operations.
 *
 * No UnboundID imports in this file — pure domain + use case interfaces.
 */
class DirectoryRepository(
    private val session: LdapSession,
    private val searchUsers: SearchUsersUseCase,
    private val searchGroups: SearchGroupsUseCase,
    private val changePassword: ChangePasswordUseCase,
    private val modifyGroups: ModifyGroupsUseCase,
    private val editName: EditNameUseCase,
    private val accountProperties: AccountPropertiesUseCase,
    private val toggleAccountEnabled: ToggleAccountEnabledUseCase,
    private val unlockAccountUseCase: UnlockAccountUseCase,
    private val serverRepository: ServerRepository,
    private val settingsStore: SettingsStore
) {

    /**
     * Search for users. Delegates directly to [SearchUsersUseCase].
     */
    suspend fun searchUsers(
        query: String,
        pageSize: Int = 100,
        cookie: String? = null
    ): Result<Pair<List<AdUser>, String?>> =
        searchUsers.search(query, session.baseDn, pageSize, cookie)

    /**
     * Change a user's password.
     *
     * Before delegating, enforces the secure channel requirement:
     * if [SettingsStore.requireSecureChannelForPassword] is enabled and the
     * current server uses [TlsMode.PLAIN], returns [AdError.Kind.INSECURE_CHANNEL].
     */
    suspend fun changePassword(
        serverId: Long,
        userDn: String,
        mode: ChangePasswordUseCase.Mode,
        oldPassword: String?,
        newPassword: String
    ): Result<Unit> {
        // Guard only when a session is actually bound: currentServerId == 0 means
        // "not connected yet", which is not a mismatch.
        if (session.currentServerId != 0L && serverId != session.currentServerId) {
            return Result.failure(
                AdError(
                    AdError.Kind.SERVER_MISMATCH, null, null,
                    "Server mismatch: requested $serverId, current ${session.currentServerId}"
                )
            )
        }
        val server = serverRepository.get(serverId)
            ?: return Result.failure(
                AdError(AdError.Kind.NOT_FOUND, null, null, "Server not found: $serverId")
            )

        val settings = settingsStore.settingsState.value

        if (settings.requireSecureChannelForPassword && server.tlsMode == TlsMode.PLAIN) {
            return Result.failure(
                AdError(
                    kind = AdError.Kind.INSECURE_CHANNEL,
                    resultCode = null,
                    hexData = null,
                    rawMessage = "Password change over plain LDAP is not allowed. " +
                        "Switch to LDAPS or START_TLS."
                )
            )
        }

        return changePassword.changePassword(userDn, mode, oldPassword, newPassword)
    }

    /**
     * Modify a user's group memberships.
     * Delegates directly to [ModifyGroupsUseCase].
     */
    suspend fun modifyGroups(
        userDn: String,
        addGroupDns: List<String>,
        removeGroupDns: List<String>
    ): Result<ModifyGroupsResult> =
        modifyGroups.modifyGroups(userDn, addGroupDns, removeGroupDns)

    /**
     * Edit a user's first name and/or last name, updating CN/DN accordingly.
     */
    suspend fun editName(
        userDn: String,
        firstName: String?,
        lastName: String?
    ): Result<String> =
        editName.editName(userDn, firstName, lastName)

    /**
     * Get account properties for a user.
     * Delegates directly to [AccountPropertiesUseCase].
     */
    suspend fun getAccountProperties(userDn: String): Result<AccountProperties> =
        accountProperties.get(userDn)

    /**
     * Set account properties for a user.
     * Delegates directly to [AccountPropertiesUseCase].
     */
    suspend fun setAccountProperties(userDn: String, properties: AccountProperties): Result<Unit> =
        accountProperties.set(userDn, properties)

    /**
     * Toggle a user's enabled/disabled status.
     * Delegates directly to [ToggleAccountEnabledUseCase].
     */
    suspend fun toggleAccountEnabled(userDn: String, enabled: Boolean): Result<Unit> =
        toggleAccountEnabled.setEnabled(userDn, enabled)

    /**
     * Unlock a locked-out AD account (resets lockoutTime and badPwdCount).
     */
    suspend fun unlockAccount(userDn: String): Result<Unit> =
        unlockAccountUseCase.unlock(userDn)

    /**
     * Search for groups in Active Directory.
     * Delegates directly to [SearchGroupsUseCase].
     */
    suspend fun searchGroups(
        query: String,
        baseDn: String,
        pageSize: Int = 100,
        cookie: String? = null
    ): Result<Pair<List<com.adm.lite.model.AdGroup>, String?>> =
        searchGroups.search(query, baseDn, pageSize, cookie)
}
