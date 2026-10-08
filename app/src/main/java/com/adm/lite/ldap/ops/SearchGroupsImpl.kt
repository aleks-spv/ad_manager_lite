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
import com.adm.lite.model.AdGroup
import com.unboundid.ldap.sdk.Filter
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.SearchRequest
import com.unboundid.ldap.sdk.SearchScope
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl
import com.unboundid.asn1.ASN1OctetString
import com.unboundid.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchGroupsImpl(
    private val executor: LdapExecutor,
    private val errors: LdapErrorMapper
) : SearchGroupsUseCase {

    override suspend fun search(
        query: String,
        baseDn: String,
        pageSize: Int,
        cookie: String?
    ): Result<Pair<List<AdGroup>, String?>> {
        return try {
            val result = withContext(Dispatchers.IO) {
                executor.execute { conn ->
                    val filter = buildGroupFilter(query)

                    val searchRequest = SearchRequest(
                        baseDn,
                        SearchScope.SUB,
                        filter,
                        "cn", "displayName"
                    )

                    val cookieValue = cookie?.let { ASN1OctetString(Base64.decode(it)) }
                        ?: ASN1OctetString()
                    searchRequest.addControl(
                        SimplePagedResultsControl(pageSize, cookieValue, true)
                    )

                    val searchResult = conn.search(searchRequest)

                    val groups = searchResult.searchEntries.map { entry ->
                        AdGroup(
                            dn = entry.dn,
                            name = entry.getAttributeValue("displayName")
                                ?: entry.getAttributeValue("cn")
                                ?: entry.dn
                        )
                    }

                    val nextCookie = extractNextCookie(searchResult)
                    groups to nextCookie
                }
            }
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LDAPException) {
            Result.failure(errors.map(e))
        } catch (e: AdError) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(
                AdError(AdError.Kind.UNKNOWN, null, null, e.message ?: "Group search failed")
            )
        }
    }

    private fun buildGroupFilter(query: String): Filter {
        if (query.isBlank()) {
            return Filter.createEqualityFilter("objectClass", "group")
        }

        val groupFilter = Filter.createEqualityFilter("objectClass", "group")
        val cnFilter = Filter.createSubstringFilter("cn", null, arrayOf(query), null)
        val displayNameFilter = Filter.createSubstringFilter("displayName", null, arrayOf(query), null)

        return Filter.createANDFilter(groupFilter, Filter.createORFilter(cnFilter, displayNameFilter))
    }

    private fun extractNextCookie(searchResult: com.unboundid.ldap.sdk.SearchResult): String? {
        val control = SimplePagedResultsControl.get(searchResult) ?: return null
        val raw = control.cookie ?: return null
        if (raw.valueLength == 0) return null
        return Base64.encode(raw.value)
    }
}
