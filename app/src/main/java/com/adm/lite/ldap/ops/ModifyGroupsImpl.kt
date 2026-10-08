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
 * Implementation of [ModifyGroupsUseCase] using UnboundID LDAP SDK.
 *
 * Group membership is edited via the `member` attribute on group objects (forward link),
 * NOT on the `memberOf` multi-value attribute of the user entry. Active Directory maintains
 * the reverse link automatically from the directory service.
 *
 * For each group in [addGroupDns], a single [Modification] of type ADD is issued:
 * `member` ← [userDn].
 *
 * For each group in [removeGroupDns], a single [Modification] of type DELETE is issued:
 * `member` ← [userDn].
 *
 * Additions are processed before removals. Each DN is modified with a separate
 * [ModifyRequest] — no transaction is used, so a mid-way failure leaves the directory
 * in a partially modified state. The returned [ModifyGroupsResult] reports:
 * - [ModifyGroupsResult.appliedDns] — DNs that were successfully modified before the failure;
 * - [ModifyGroupsResult.failures] — pairs of (DN, error) for DNs that could not be modified.
 *
 * A [Result.success] result always contains a complete [ModifyGroupsResult]; callers should
 * inspect both [ModifyGroupsResult.appliedDns] and [ModifyGroupsResult.failures] to determine
 * whether the operation was fully successful, partially successful, or completely failed.
 */
class ModifyGroupsImpl(
    private val executor: LdapExecutor,
    private val errors: LdapErrorMapper
) : ModifyGroupsUseCase {

    override suspend fun modifyGroups(
        userDn: String,
        addGroupDns: List<String>,
        removeGroupDns: List<String>
    ): Result<ModifyGroupsResult> {
        val appliedDns = mutableListOf<String>()
        var currentDn: String? = null
        try {
            // Process additions
            for (groupDn in addGroupDns) {
                currentDn = groupDn
                executor.execute { conn ->
                    val req = ModifyRequest(
                        groupDn,
                        Modification(ModificationType.ADD, "member", userDn)
                    )
                    val result = conn.modify(req)
                    if (result.resultCode != ResultCode.SUCCESS) {
                        throw LDAPException(result)
                    }
                }
                appliedDns.add(groupDn)
            }

            // Process removals
            for (groupDn in removeGroupDns) {
                currentDn = groupDn
                executor.execute { conn ->
                    val req = ModifyRequest(
                        groupDn,
                        Modification(ModificationType.DELETE, "member", userDn)
                    )
                    val result = conn.modify(req)
                    if (result.resultCode != ResultCode.SUCCESS) {
                        throw LDAPException(result)
                    }
                }
                appliedDns.add(groupDn)
            }

            return Result.success(ModifyGroupsResult(appliedDns, emptyList()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: LDAPException) {
            val failedDn = currentDn ?: userDn
            return Result.success(ModifyGroupsResult(
                appliedDns,
                listOf(failedDn to errors.map(e))
            ))
        } catch (e: AdError) {
            return Result.success(ModifyGroupsResult(appliedDns, listOf((currentDn ?: userDn) to e)))
        } catch (e: Exception) {
            val failedDn = currentDn ?: userDn
            val error = AdError(
                kind = AdError.Kind.NETWORK,
                resultCode = null,
                hexData = null,
                rawMessage = e.message
            )
            return Result.success(ModifyGroupsResult(appliedDns, listOf(failedDn to error)))
        }
    }
}
