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

import com.adm.lite.ldap.AdAttributeCodec
import com.adm.lite.ldap.LdapErrorMapper
import com.adm.lite.ldap.LdapExecutor
import com.adm.lite.model.AdError
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.Modification
import com.unboundid.ldap.sdk.ModificationType
import com.unboundid.ldap.sdk.ModifyRequest
import com.unboundid.ldap.sdk.ResultCode
import kotlinx.coroutines.CancellationException

/**
 * Implementation of [ChangePasswordUseCase] using UnboundID LDAP SDK.
 *
 * ## Admin Reset
 * Single [Modification] of type REPLACE on `unicodePwd` with the new password bytes.
 *
 * ## Self-Change
 * Two modifications in ONE [ModifyRequest] (atomic):
 * 1. DELETE `unicodePwd` with old password bytes
 * 2. ADD `unicodePwd` with new password bytes
 *
 * AD requires both operations in a single request for self-change.
 *
 * ## Security Note
 * The `unicodePwd` attribute requires an encrypted channel (LDAPS or START_TLS).
 * Active Directory will reject writes over plain LDAP.
 */
class ChangePasswordImpl(
    private val executor: LdapExecutor,
    private val errors: LdapErrorMapper
) : ChangePasswordUseCase {

    override suspend fun changePassword(
        userDn: String,
        mode: ChangePasswordUseCase.Mode,
        oldPassword: String?,
        newPassword: String
    ): Result<Unit> {
        return try {
            val modifyRequest = when (mode) {
                ChangePasswordUseCase.Mode.ADMIN_RESET -> buildAdminReset(userDn, newPassword)
                ChangePasswordUseCase.Mode.SELF_CHANGE -> {
                    val old = oldPassword
                        ?: return Result.failure(AdError(
                            kind = AdError.Kind.WRONG_OLD_PASSWORD,
                            resultCode = null,
                            hexData = null,
                            rawMessage = "Old password is required for self-change"
                        ))
                    buildSelfChange(userDn, old, newPassword)
                }
            }

            executor.execute { conn ->
                val result = conn.modify(modifyRequest)
                if (result.resultCode != ResultCode.SUCCESS) {
                    throw LDAPException(result)
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LDAPException) {
            Result.failure(errors.map(e))
        } catch (e: AdError) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(AdError(
                kind = AdError.Kind.NETWORK,
                resultCode = null,
                hexData = null,
                rawMessage = e.message
            ))
        }
    }

    /**
     * Build a MODIFY request for admin password reset.
     *
     * Single REPLACE modification: `unicodePwd` ← new password bytes.
     */
    internal fun buildAdminReset(userDn: String, newPassword: String): ModifyRequest {
        val mod = Modification(
            ModificationType.REPLACE,
            "unicodePwd",
            AdAttributeCodec.encodeUnicodePwd(newPassword)
        )
        return ModifyRequest(userDn, mod)
    }

    /**
     * Build a MODIFY request for self-change (old + new password).
     *
     * Two modifications in ONE request (atomic):
     * 1. DELETE `unicodePwd` with old password bytes
     * 2. ADD `unicodePwd` with new password bytes
     */
    internal fun buildSelfChange(userDn: String, oldPassword: String, newPassword: String): ModifyRequest {
        val deleteMod = Modification(
            ModificationType.DELETE,
            "unicodePwd",
            AdAttributeCodec.encodeUnicodePwd(oldPassword)
        )
        val addMod = Modification(
            ModificationType.ADD,
            "unicodePwd",
            AdAttributeCodec.encodeUnicodePwd(newPassword)
        )
        return ModifyRequest(userDn, deleteMod, addMod)
    }
}
