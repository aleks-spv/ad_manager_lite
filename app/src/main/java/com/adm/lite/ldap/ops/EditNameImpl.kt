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
import com.adm.lite.model.Validators
import com.unboundid.ldap.sdk.DN
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.Modification
import com.unboundid.ldap.sdk.ModificationType
import com.unboundid.ldap.sdk.ModifyDNRequest
import com.unboundid.ldap.sdk.RDN
import com.unboundid.ldap.sdk.SearchRequest
import com.unboundid.ldap.sdk.SearchScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EditNameImpl(
    private val executor: LdapExecutor,
    private val errors: LdapErrorMapper
) : EditNameUseCase {

    override suspend fun editName(
        userDn: String,
        firstName: String?,
        lastName: String?
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val newFirstName = firstName?.takeIf { it.isNotBlank() }
                val newLastName = lastName?.takeIf { it.isNotBlank() }
                if (newFirstName == null && newLastName == null) {
                    return@withContext Result.success(userDn)
                }

                // Read current attributes in one BASE search
                val current = executor.execute { conn ->
                    val sr = SearchRequest(
                        userDn, SearchScope.BASE, "(objectClass=*)",
                        "cn", "givenName", "sn", "displayName"
                    )
                    val result = conn.search(sr)
                    if (result.entryCount == 0) {
                        throw AdError(
                            kind = AdError.Kind.NOT_FOUND,
                            resultCode = null, hexData = null,
                            rawMessage = "User not found: $userDn"
                        )
                    }
                    result.searchEntries.first()
                }

                val curGivenName = current.getAttributeValue("givenName") ?: ""
                val curSn = current.getAttributeValue("sn") ?: ""
                val curCn = current.getAttributeValue("cn") ?: ""

                val effectiveFirst = newFirstName ?: curGivenName
                val effectiveLast = newLastName ?: curSn
                val newCn = listOfNotNull(
                    effectiveLast.takeIf { it.isNotBlank() },
                    effectiveFirst.takeIf { it.isNotBlank() }
                ).joinToString(" ")

                // Validate final CN
                if (Validators.validateNewCn(newCn) != null) {
                    return@withContext Result.failure(
                        AdError(AdError.Kind.VALIDATION, null, null, "Invalid CN")
                    )
                }

                val dn = DN(userDn)
                val parentDn = dn.parent?.toString() ?: ""
                val newDnStr = if (parentDn.isNotEmpty()) "CN=$newCn,$parentDn" else "CN=$newCn"

                // Step 1: modifyDN first
                if (!newCn.equals(curCn, ignoreCase = true)) {
                    executor.execute { conn ->
                        conn.modifyDN(
                            ModifyDNRequest(
                                DN(userDn), RDN("CN", newCn), true
                            )
                        )
                    }
                }

                // Step 2: modify attributes under new DN
                val modifications = mutableListOf<Modification>()
                if (newFirstName != null) {
                    modifications.add(
                        Modification(ModificationType.REPLACE, "givenName", newFirstName)
                    )
                }
                if (newLastName != null) {
                    modifications.add(
                        Modification(ModificationType.REPLACE, "sn", newLastName)
                    )
                }
                // displayName follows the CN convention ("Фамилия Имя").
                if (newCn.isNotBlank()) {
                    modifications.add(
                        Modification(ModificationType.REPLACE, "displayName", newCn)
                    )
                }

                if (modifications.isNotEmpty()) {
                    try {
                        executor.execute { conn ->
                            conn.modify(newDnStr, modifications)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // The rename already happened — say so instead of implying
                        // that nothing was changed.
                        val kind = (e as? LDAPException)?.let { errors.map(it).kind }
                            ?: (e as? AdError)?.kind
                            ?: AdError.Kind.UNKNOWN
                        return@withContext Result.failure(
                            AdError(
                                kind,
                                null,
                                null,
                                "DN already changed to $newDnStr; attribute update failed: ${e.message}"
                            )
                        )
                    }
                }

                Result.success(newDnStr)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LDAPException) {
                Result.failure(errors.map(e))
            } catch (e: AdError) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(
                    AdError(AdError.Kind.UNKNOWN, null, null, e.message ?: "Edit name failed")
                )
            }
        }
    }
}
