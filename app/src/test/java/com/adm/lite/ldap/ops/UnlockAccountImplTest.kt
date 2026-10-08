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
import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldap.sdk.Entry
import com.unboundid.ldap.sdk.LDAPConnection
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.ResultCode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UnlockAccountImplTest {

    private lateinit var ldapServer: InMemoryDirectoryServer
    private val errors = LdapErrorMapper()
    private val userDn = "CN=Ivanov Ivan,OU=Users,DC=corp,DC=local"

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
                "dn: " + userDn,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: user",
                "cn: Ivanov Ivan",
                "sn: Ivanov",
                "givenName: Ivan",
                "lockoutTime: 133700000000000000"
            )
        )
    }

    @After
    fun tearDown() {
        ldapServer.shutDown(true)
    }

    private fun liveExecutor(): LdapExecutor = object : LdapExecutor {
        override suspend fun <T> execute(block: (LDAPConnection) -> T): T =
            block(ldapServer.connection)
    }

    private fun failingExecutor(ex: LDAPException): LdapExecutor = object : LdapExecutor {
        override suspend fun <T> execute(block: (LDAPConnection) -> T): T = throw ex
    }

    @Test
    fun unlock_success_clearsLockoutTime() = runBlocking {
        val impl = UnlockAccountImpl(liveExecutor(), errors)
        val result = impl.unlock(userDn)
        assertTrue(result.isSuccess)
        val entry = ldapServer.getEntry(userDn)
        assertEquals("0", entry.getAttributeValue("lockoutTime"))
    }

    @Test
    fun unlock_ldapError_returnsFailure() = runBlocking {
        val impl = UnlockAccountImpl(
            failingExecutor(LDAPException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS, "denied")),
            errors
        )
        val result = impl.unlock(userDn)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as? AdError
        assertEquals(AdError.Kind.INSUFFICIENT_RIGHTS, error?.kind)
    }

    @Test
    fun unlock_unknownUser_mapsNotFoundError() = runBlocking {
        val impl = UnlockAccountImpl(liveExecutor(), errors)
        val result = impl.unlock("CN=Missing,OU=Users,DC=corp,DC=local")
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as? AdError
        assertNotNull(error)
    }
}
