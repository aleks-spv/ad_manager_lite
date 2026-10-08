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

package com.adm.lite.ldap.ops

import com.adm.lite.model.AdError
import com.adm.lite.model.AdGroup
import com.adm.lite.model.AdUser

/**
 * Search for users in Active Directory.
 * Query is matched against sAMAccountName, cn, displayName, mail.
 * Returns paginated results.
 */
interface SearchUsersUseCase {
    suspend fun search(
        query: String,
        baseDn: String,
        pageSize: Int = 100,
        cookie: String? = null
    ): Result<Pair<List<AdUser>, String?>>
}

/**
 * Change a user's password in Active Directory.
 * Supports admin reset (no old password needed) and self-change (old + new).
 */
interface ChangePasswordUseCase {
    enum class Mode {
        /** Admin reset: only new password required. User must be privileged. */
        ADMIN_RESET,
        /** Self-change: old + new passwords required. */
        SELF_CHANGE
    }

    /**
     * @param userDn DN of the target user
     * @param mode ADMIN_RESET or SELF_CHANGE
     * @param oldPassword Current password (required only for SELF_CHANGE)
     * @param newPassword New password to set
     */
    suspend fun changePassword(
        userDn: String,
        mode: Mode,
        oldPassword: String?,
        newPassword: String
    ): Result<Unit>
}

/**
 * Modify a user's group memberships in Active Directory.
 * Group membership is edited via the `member` attribute on group objects (forward link).
 */
interface ModifyGroupsUseCase {
    /**
     * @param userDn DN of the target user
     * @param addGroupDns DNs of groups to add the user to
     * @param removeGroupDns DNs of groups to remove the user from
     * @return Result with [ModifyGroupsResult] describing applied DNs and any failures
     */
    suspend fun modifyGroups(
        userDn: String,
        addGroupDns: List<String>,
        removeGroupDns: List<String>
    ): Result<ModifyGroupsResult>
}

/**
 * Rename a user in Active Directory.
 * Changes CN and optionally sAMAccountName / userPrincipalName.
 */
interface EditNameUseCase {
    /**
     * @param userDn Current DN of the user
     * @param firstName New first name (givenName), null = don't change
     * @param lastName New last name (sn), null = don't change
     * @return Result with the new DN after rename
     */
    suspend fun editName(
        userDn: String,
        firstName: String?,
        lastName: String?
    ): Result<String>
}

/**
 * Search for groups in Active Directory.
 */
interface SearchGroupsUseCase {
    suspend fun search(
        query: String,
        baseDn: String,
        pageSize: Int = 100,
        cookie: String? = null
    ): Result<Pair<List<AdGroup>, String?>>
}

/**
 * Toggle the "account enabled" flag on a user in Active Directory.
 */
interface ToggleAccountEnabledUseCase {
    suspend fun setEnabled(userDn: String, enabled: Boolean): Result<Unit>
}

/**
 * Read / write per-user account properties (UAC flags) in Active Directory.
 */
data class AccountProperties(
    val forcePasswordChange: Boolean = false,
    val passwordNeverExpires: Boolean,
    val userCannotChangePassword: Boolean
)

/**
 * Get or set account properties (UAC flags, password policy) for a user in AD.
 */
interface AccountPropertiesUseCase {
    suspend fun get(userDn: String): Result<AccountProperties>
    suspend fun set(userDn: String, properties: AccountProperties): Result<Unit>
}

/**
 * Unlock a locked-out AD account by resetting lockoutTime and badPwdCount.
 */
interface UnlockAccountUseCase {
    suspend fun unlock(userDn: String): Result<Unit>
}
