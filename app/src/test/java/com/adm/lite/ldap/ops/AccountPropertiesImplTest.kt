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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountPropertiesImplTest {

    private lateinit var ldapServer: InMemoryDirectoryServer
    private val errors = LdapErrorMapper()
    private val userDn = "CN=Ivanov Ivan,OU=Users,DC=corp,DC=local"

    /** Counts LdapExecutor.execute invocations — 1 = read only, 2 = read + modify. */
    private var executeCount = 0

    @Before
    fun setUp() {
        val config = InMemoryDirectoryServerConfig("DC=corp,DC=local")
        config.addAdditionalBindCredentials("cn=admin,DC=corp,DC=local", "secret")
        config.setSchema(null)
        ldapServer = InMemoryDirectoryServer(config)
        ldapServer.startListening()
        // UnboundID's in-memory server does not create the base DN itself.
        ldapServer.add(
            Entry("dn: DC=corp,DC=local", "objectClass: top", "objectClass: domain", "dc: corp")
        )

        ldapServer.add(
            Entry(
                "dn: OU=Users,DC=corp,DC=local",
                "objectClass: top",
                "objectClass: organizationalUnit",
                "ou: Users"
            )
        )
        ldapServer.add(
            Entry(
                "dn: $userDn",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: user",
                "cn: Ivanov Ivan",
                "sn: Ivanov",
                "givenName: Ivan",
                "userAccountControl: 512",
                "pwdLastSet: -1"
            )
        )
    }

    @After
    fun tearDown() {
        ldapServer.shutDown(true)
    }

    private fun countingExecutor(): LdapExecutor = object : LdapExecutor {
        override suspend fun <T> execute(block: (LDAPConnection) -> T): T {
            executeCount++
            return block(ldapServer.connection)
        }
    }

    @Test
    fun set_withoutChanges_sendsNoModify() = runBlocking {
        val impl = AccountPropertiesImpl(countingExecutor(), errors)
        executeCount = 0

        val result = impl.set(
            userDn,
            AccountProperties(
                forcePasswordChange = false,
                passwordNeverExpires = false,
                userCannotChangePassword = false
            )
        )

        assertTrue(result.isSuccess)
        // Only the read — no modify when nothing changed.
        assertEquals(1, executeCount)
        val entry = ldapServer.getEntry(userDn)
        assertEquals("512", entry.getAttributeValue("userAccountControl"))
        assertEquals("-1", entry.getAttributeValue("pwdLastSet"))
    }

    @Test
    fun set_pwdLastSetUnchanged_isNotWritten() = runBlocking {
        val impl = AccountPropertiesImpl(countingExecutor(), errors)
        executeCount = 0

        // Account already has pwdLastSet = -1; toggling passwordNeverExpires
        // must not touch pwdLastSet at all.
        val result = impl.set(
            userDn,
            AccountProperties(
                forcePasswordChange = false,
                passwordNeverExpires = true,
                userCannotChangePassword = false
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(2, executeCount)
        val entry = ldapServer.getEntry(userDn)
        assertEquals("-1", entry.getAttributeValue("pwdLastSet"))
        assertEquals(
            (512 or 0x10000).toString(),
            entry.getAttributeValue("userAccountControl")
        )
    }

    @Test
    fun set_forcePasswordChange_writesPwdLastSetZero() = runBlocking {
        val impl = AccountPropertiesImpl(countingExecutor(), errors)

        val result = impl.set(
            userDn,
            AccountProperties(
                forcePasswordChange = true,
                passwordNeverExpires = false,
                userCannotChangePassword = false
            )
        )

        assertTrue(result.isSuccess)
        assertEquals("0", ldapServer.getEntry(userDn).getAttributeValue("pwdLastSet"))
    }

    @Test
    fun get_readsAllFlags() = runBlocking {
        val impl = AccountPropertiesImpl(countingExecutor(), errors)

        val props = impl.get(userDn).getOrThrow()

        assertEquals(false, props.forcePasswordChange)
        assertEquals(false, props.passwordNeverExpires)
        assertEquals(false, props.userCannotChangePassword)
    }
}
