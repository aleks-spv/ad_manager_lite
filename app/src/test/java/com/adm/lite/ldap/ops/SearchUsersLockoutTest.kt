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
import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldap.sdk.Entry
import com.unboundid.ldap.sdk.LDAPConnection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Lockout status must come from msDS-User-Account-Control-Computed (bit 0x10),
 * not from a non-zero lockoutTime — AD keeps lockoutTime set long after the
 * lockout has expired.
 */
class SearchUsersLockoutTest {

    private lateinit var ldapServer: InMemoryDirectoryServer
    private val errors = LdapErrorMapper()
    private val baseDn = "DC=corp,DC=local"

    private val computedNotLocked =
        "CN=u1,OU=Users,DC=corp,DC=local"
    private val computedLocked =
        "CN=u2,OU=Users,DC=corp,DC=local"
    private val noComputed =
        "CN=u3,OU=Users,DC=corp,DC=local"

    @Before
    fun setUp() {
        val config = InMemoryDirectoryServerConfig(baseDn)
        config.addAdditionalBindCredentials("cn=admin,$baseDn", "secret")
        config.setSchema(null)
        ldapServer = InMemoryDirectoryServer(config)
        ldapServer.startListening()
        // UnboundID's in-memory server does not create the base DN itself.
        ldapServer.add(
            Entry("dn: $baseDn", "objectClass: top", "objectClass: domain", "dc: corp")
        )

        ldapServer.add(
            Entry(
                "dn: OU=Users,$baseDn",
                "objectClass: top",
                "objectClass: organizationalUnit",
                "ou: Users"
            )
        )

        fun user(dn: String, cn: String, vararg extra: String) {
            ldapServer.add(
                Entry(
                    "dn: $dn",
                    "objectClass: top",
                    "objectClass: person",
                    "objectClass: organizationalPerson",
                    "objectClass: user",
                    "objectCategory: person",
                    "cn: $cn",
                    "sn: $cn",
                    "sAMAccountName: ${cn.lowercase()}",
                    *extra
                )
            )
        }

        // Stale lockoutTime but computed bit says: not locked.
        user(
            computedNotLocked, "u1",
            "lockoutTime: 133700000000000000",
            "msDS-User-Account-Control-Computed: 0"
        )
        // Computed bit 0x10 set.
        user(
            computedLocked, "u2",
            "lockoutTime: 133700000000000000",
            "msDS-User-Account-Control-Computed: 16"
        )
        // Computed attribute missing — fall back to lockoutTime.
        user(noComputed, "u3", "lockoutTime: 133700000000000000")
    }

    @After
    fun tearDown() {
        ldapServer.shutDown(true)
    }

    private fun executor(): LdapExecutor = object : LdapExecutor {
        override suspend fun <T> execute(block: (LDAPConnection) -> T): T =
            block(ldapServer.connection)
    }

    private suspend fun search(): Map<String, Boolean> {
        val impl = SearchUsersImpl(executor(), errors)
        val (users, _) = impl.search("", baseDn, 50, null).getOrThrow()
        return users.associate { it.dn to it.lockedOut }
    }

    @Test
    fun staleLockoutTime_butComputedBitClear_isNotLocked() = runBlocking {
        val byDn = search()
        assertFalse(byDn[computedNotLocked] ?: error("missing $computedNotLocked"))
    }

    @Test
    fun computedBitSet_isLocked() = runBlocking {
        val byDn = search()
        assertTrue(byDn[computedLocked] ?: error("missing $computedLocked"))
    }

    @Test
    fun computedAttributeAbsent_fallsBackToLockoutTime() = runBlocking {
        val byDn = search()
        assertTrue(byDn[noComputed] ?: error("missing $noComputed"))
    }

    @Test
    fun enabled_comesFromUserAccountControl() = runBlocking {
        val impl = SearchUsersImpl(executor(), errors)
        val (users, _) = impl.search("", baseDn, 50, null).getOrThrow()
        assertTrue(users.all { it.enabled })
        assertEquals(3, users.size)
    }
}
