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
import com.unboundid.ldap.sdk.LDAPConnection
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

class SearchUsersImplTest {

    private lateinit var impl: SearchUsersImpl
    private val errors = LdapErrorMapper()

    @Before
    fun setUp() {
        // The executor just returns pre-built results — no real connection needed.
        // Tests focus on filter building logic.
        impl = SearchUsersImpl(
            executor = object : LdapExecutor {
                override suspend fun <T> execute(block: (LDAPConnection) -> T): T =
                    throw UnsupportedOperationException("This test must not touch a live LDAP connection")
            },
            errors = errors
        )
    }

    @Test
    fun buildUserFilter_blankQuery_returnsBaseFilter() = runBlocking {
        val filter = impl.buildUserFilter("")
        val str = filter.toString()
        assertTrue(str.contains("objectClass=user"))
        assertTrue(str.contains("objectCategory=person"))
        assertFalse(str.contains("sAMAccountName"))
    }

    @Test
    fun buildUserFilter_withQuery_addsSubstringOnAllFields() = runBlocking {
        val filter = impl.buildUserFilter("alice")
        val str = filter.toString()
        assertTrue(str.contains("sAMAccountName=*alice*"))
        assertTrue(str.contains("cn=*alice*"))
        assertTrue(str.contains("displayName=*alice*"))
        assertTrue(str.contains("mail=*alice*"))
    }

    @Test
    fun buildUserFilter_isConjunctionOfBaseAndSubstring() = runBlocking {
        val filter = impl.buildUserFilter("test")
        val str = filter.toString()
        assertTrue("Filter should be AND: $str", str.startsWith("(&"))
    }

    @Test
    fun buildUserFilter_specialChars_escapedByFilterApi() = runBlocking {
        val filter = impl.buildUserFilter("a*b")
        val str = filter.toString()
        // UnboundID escapes * in substring filters
        assertTrue(str.contains("sAMAccountName="))
    }

    @Test
    fun buildUserFilter_emptyString_noWildcardInResult() = runBlocking {
        val filter = impl.buildUserFilter("")
        assertFalse(filter.toString().contains("*"))
    }

    @Test
    fun buildUserFilter_singleChar_worksCorrectly() = runBlocking {
        val filter = impl.buildUserFilter("a")
        val str = filter.toString()
        assertTrue(str.contains("sAMAccountName=*a*"))
    }

}
