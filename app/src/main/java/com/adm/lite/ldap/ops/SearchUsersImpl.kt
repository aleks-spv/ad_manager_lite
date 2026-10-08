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
import com.adm.lite.model.AdUser
import com.unboundid.ldap.sdk.Filter
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.SearchRequest
import com.unboundid.ldap.sdk.SearchScope
import com.unboundid.asn1.ASN1OctetString
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl
import com.unboundid.util.Base64

import kotlinx.coroutines.CancellationException

/**
 * Implementation of [SearchUsersUseCase] using UnboundID LDAP SDK.
 *
 * Builds safe filters using [Filter.create*] methods (no string concatenation → no LDAP injection).
 * Uses paged search with [SimplePagedResultsControl].
 *
 * @param executor LDAP executor (wraps connection access)
 * @param errors Error mapper for LDAP exceptions
 * @param baseDn Base DN for search scope (from connected server config)
 */
class SearchUsersImpl(
    private val executor: LdapExecutor,
    private val errors: LdapErrorMapper
) : SearchUsersUseCase {

    override suspend fun search(
        query: String,
        baseDn: String,
        pageSize: Int,
        cookie: String?
    ): Result<Pair<List<AdUser>, String?>> {
        return try {
            val result = executor.execute { conn ->
                val filter = buildUserFilter(query)

                val searchRequest = SearchRequest(
                    baseDn,
                    SearchScope.SUB,
                    filter,
                    *REQUEST_ATTRIBUTES
                )

                // Attach paged results control
                val cookieValue = cookie?.let { ASN1OctetString(Base64.decode(it)) } ?: ASN1OctetString()
                searchRequest.addControl(SimplePagedResultsControl(pageSize, cookieValue, true))

                val searchResult = conn.search(searchRequest)

                val users = searchResult.searchEntries.map { it.toAdUser() }
                val nextCookie = extractNextCookie(searchResult)

                users to nextCookie
            }

            Result.success(result)
        } catch (e: LDAPException) {
            val adError = errors.map(e)
            Result.failure(adError)
        } catch (e: AdError) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
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
     * Build a compound filter: objectClass=user AND objectCategory=person
     * AND (substring match on sAMAccountName OR cn OR displayName OR mail).
     *
     * Uses [Filter.create*] factory methods — never string concatenation.
     */
    internal fun buildUserFilter(query: String): Filter {
        val baseFilter = Filter.createANDFilter(
            Filter.createEqualityFilter("objectClass", "user"),
            Filter.createEqualityFilter("objectCategory", "person")
        )

        if (query.isBlank()) return baseFilter

        val substringFilter = Filter.createORFilter(
            Filter.createSubstringFilter("sAMAccountName", null, arrayOf(query), null),
            Filter.createSubstringFilter("cn", null, arrayOf(query), null),
            Filter.createSubstringFilter("displayName", null, arrayOf(query), null),
            Filter.createSubstringFilter("mail", null, arrayOf(query), null),
            Filter.createSubstringFilter("userPrincipalName", null, arrayOf(query), null)
        )

        return Filter.createANDFilter(baseFilter, substringFilter)
    }

    private fun extractNextCookie(searchResult: com.unboundid.ldap.sdk.SearchResult): String? {
        val control = SimplePagedResultsControl.get(searchResult) ?: return null
        val raw = control.cookie ?: return null
        if (raw.valueLength == 0) return null
        return Base64.encode(raw.value)
    }

    companion object {
        private val REQUEST_ATTRIBUTES = arrayOf(
            "dn",
            "sAMAccountName",
            "userPrincipalName",
            "displayName",
            "givenName",
            "sn",
            "cn",
            "mail",
            "userAccountControl",
            "memberOf",
            "lockoutTime",
            "msDS-User-Account-Control-Computed"
        )
    }
}

/**
 * Map an LDAP [SearchResultEntry] to an [AdUser] domain object.
 *
 * - `enabled` = bit 0x2 (ACCOUNTDISABLE) is NOT set in `userAccountControl`
 * - `lockedOut` = bit 0x10 of `msDS-User-Account-Control-Computed`,
 *   fallback to `lockoutTime != 0` when computed is absent
 * - `groups` = list of `memberOf` attribute values
 */
private fun com.unboundid.ldap.sdk.SearchResultEntry.toAdUser(): AdUser {
    val uac = getAttributeValue("userAccountControl")?.toIntOrNull() ?: 0
    val computed = getAttributeValue("msDS-User-Account-Control-Computed")
    val lockoutTime = getAttributeValue("lockoutTime")?.toLongOrNull() ?: 0L
    val memberOf = getAttributeValues("memberOf")?.toList() ?: emptyList()

    // Decimal integer; bit 0x10 = lockout. Fallback to lockoutTime when absent/unparsable.
    val lockedOut = computed?.toIntOrNull()?.let { (it and 0x10) != 0 }
        ?: (lockoutTime != 0L)

    return AdUser(
        dn = dn,
        sAMAccountName = getAttributeValue("sAMAccountName"),
        userPrincipalName = getAttributeValue("userPrincipalName"),
        displayName = getAttributeValue("displayName"),
        givenName = getAttributeValue("givenName"),
        sn = getAttributeValue("sn"),
        mail = getAttributeValue("mail"),
        enabled = (uac and ACCOUNTDISABLE_BIT) == 0,
        lockedOut = lockedOut,
        groups = memberOf
    )
}

/** Bit 0x2 = ACCOUNTDISABLE flag in userAccountControl. */
private const val ACCOUNTDISABLE_BIT = 0x2
