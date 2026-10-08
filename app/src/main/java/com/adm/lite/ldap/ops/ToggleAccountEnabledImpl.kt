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
import com.unboundid.ldap.sdk.SearchRequest
import com.unboundid.ldap.sdk.SearchScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ToggleAccountEnabledImpl(
    private val executor: LdapExecutor,
    private val errors: LdapErrorMapper
) : ToggleAccountEnabledUseCase {

    override suspend fun setEnabled(userDn: String, enabled: Boolean): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val currentUac = executor.execute { conn ->
                    val searchRequest = SearchRequest(
                        userDn, SearchScope.BASE, "(objectClass=*)", "userAccountControl"
                    )
                    val result = conn.search(searchRequest)
                    if (result.entryCount == 0) {
                        throw AdError(
                            kind = AdError.Kind.NOT_FOUND,
                            resultCode = null,
                            hexData = null,
                            rawMessage = "User not found"
                        )
                    }
                    result.searchEntries.first().getAttributeValue("userAccountControl")?.toIntOrNull()
                        ?: throw AdError(
                            kind = AdError.Kind.NOT_FOUND,
                            resultCode = null,
                            hexData = null,
                            rawMessage = "userAccountControl is missing or unreadable for $userDn"
                        )
                }

                val newUac = if (enabled) {
                    currentUac and ACCOUNTDISABLE_BIT.inv()
                } else {
                    currentUac or ACCOUNTDISABLE_BIT
                }

                executor.execute { conn ->
                    val mod = Modification(
                        ModificationType.REPLACE,
                        "userAccountControl",
                        newUac.toString()
                    )
                    conn.modify(userDn, mod)
                }

                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LDAPException) {
                Result.failure(errors.map(e))
            } catch (e: AdError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(
                    AdError(
                        kind = AdError.Kind.UNKNOWN,
                        resultCode = null,
                        hexData = null,
                        rawMessage = e.message ?: "Failed to toggle account"
                    )
                )
            }
        }
    }

    companion object {
        private const val ACCOUNTDISABLE_BIT = 0x2
    }
}
