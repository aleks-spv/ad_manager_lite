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

class AccountPropertiesImpl(
    private val executor: LdapExecutor,
    private val errors: LdapErrorMapper
) : AccountPropertiesUseCase {

    override suspend fun get(userDn: String): Result<AccountProperties> {
        return withContext(Dispatchers.IO) {
            try {
                val props = executor.execute { conn ->
                    val searchRequest = SearchRequest(
                        userDn, SearchScope.BASE, "(objectClass=*)",
                        "userAccountControl", "pwdLastSet"
                    )
                    val result = conn.search(searchRequest)
                    if (result.entryCount == 0) {
                        throw AdError(
                            kind = AdError.Kind.NOT_FOUND,
                            resultCode = null, hexData = null,
                            rawMessage = "User not found"
                        )
                    }
                    val entry = result.searchEntries.first()
                    val uac = entry.getAttributeValue("userAccountControl")
                        ?.toIntOrNull()
                        ?: throw AdError(
                            kind = AdError.Kind.NOT_FOUND, resultCode = null,
                            hexData = null,
                            rawMessage = "userAccountControl unreadable for $userDn"
                        )
                    val pwdLastSet = entry.getAttributeValue("pwdLastSet")
                        ?.toLongOrNull() ?: -1L

                    AccountProperties(
                        forcePasswordChange = pwdLastSet == 0L,
                        passwordNeverExpires = (uac and UF_DONT_EXPIRE_PASSWD) != 0,
                        userCannotChangePassword = (uac and UF_PASSWD_CANT_CHANGE) != 0
                    )
                }
                Result.success(props)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LDAPException) {
                Result.failure(errors.map(e))
            } catch (e: AdError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(
                    AdError(AdError.Kind.UNKNOWN, null, null, e.message)
                )
            }
        }
    }

    override suspend fun set(userDn: String, properties: AccountProperties): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val (currentUac, currentPwdLastSet) = executor.execute { conn ->
                    val searchRequest = SearchRequest(
                        userDn, SearchScope.BASE, "(objectClass=*)",
                        "userAccountControl", "pwdLastSet"
                    )
                    val result = conn.search(searchRequest)
                    if (result.entryCount == 0) {
                        throw AdError(
                            kind = AdError.Kind.NOT_FOUND,
                            resultCode = null, hexData = null,
                            rawMessage = "User not found"
                        )
                    }
                    val entry = result.searchEntries.first()
                    val uac = entry.getAttributeValue("userAccountControl")
                        ?.toIntOrNull()
                        ?: throw AdError(
                            kind = AdError.Kind.NOT_FOUND, resultCode = null,
                            hexData = null,
                            rawMessage = "userAccountControl unreadable for $userDn"
                        )
                    val pwd = entry.getAttributeValue("pwdLastSet")
                        ?.toLongOrNull() ?: -1L
                    uac to pwd
                }

                val newUac = computeNewUac(currentUac, properties)
                val forceCurrently = currentPwdLastSet == 0L

                val modifications = mutableListOf<Modification>()

                if (newUac != currentUac) {
                    modifications.add(
                        Modification(ModificationType.REPLACE, "userAccountControl", newUac.toString())
                    )
                }
                if (forceCurrently != properties.forcePasswordChange) {
                    val pwd = if (properties.forcePasswordChange) "0" else "-1"
                    modifications.add(
                        Modification(ModificationType.REPLACE, "pwdLastSet", pwd)
                    )
                }

                if (modifications.isNotEmpty()) {
                    executor.execute { conn ->
                        conn.modify(userDn, modifications)
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
                Result.failure(
                    AdError(AdError.Kind.UNKNOWN, null, null, e.message)
                )
            }
        }
    }

    private fun computeNewUac(currentUac: Int, props: AccountProperties): Int {
        var uac = currentUac
        uac = if (props.passwordNeverExpires) {
            uac or UF_DONT_EXPIRE_PASSWD
        } else {
            uac and UF_DONT_EXPIRE_PASSWD.inv()
        }
        uac = if (props.userCannotChangePassword) {
            uac or UF_PASSWD_CANT_CHANGE
        } else {
            uac and UF_PASSWD_CANT_CHANGE.inv()
        }
        return uac
    }
}

/** DONT_EXPIRE_PASSWORD */
private const val UF_DONT_EXPIRE_PASSWD = 0x10000

/** PASSWD_CANT_CHANGE */
private const val UF_PASSWD_CANT_CHANGE = 0x40
