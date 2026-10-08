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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EditNameImplTest {

    private lateinit var ldapServer: InMemoryDirectoryServer
    private val errors = LdapErrorMapper()
    private val userDn = "CN=Ivanov Ivan,OU=Users,DC=corp,DC=local"
    private val parentDn = "OU=Users,DC=corp,DC=local"

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
                "sAMAccountName: iivanov"
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
    fun editName_success_updatesAttributesAndDn() = runBlocking {
        val impl = EditNameImpl(liveExecutor(), errors)
        val result = impl.editName(userDn, "Пётр", null)
        assertTrue(result.isSuccess)
        assertEquals("CN=Ivanov Пётр,$parentDn", result.getOrNull())

        val entry = ldapServer.getEntry("CN=Ivanov Пётр,$parentDn")
        assertEquals("Пётр", entry.getAttributeValue("givenName"))
        assertEquals("Ivanov", entry.getAttributeValue("sn"))
        assertEquals("Ivanov Пётр", entry.getAttributeValue("cn"))
        assertEquals("Ivanov Пётр", entry.getAttributeValue("displayName"))
    }

    @Test
    fun editName_onlyFirstName_keepsLastNameInCn() = runBlocking {
        val impl = EditNameImpl(liveExecutor(), errors)
        val result = impl.editName(userDn, "Пётр", null)
        assertTrue(result.isSuccess)
        val newDn = result.getOrNull()!!
        assertEquals("CN=Ivanov Пётр,$parentDn", newDn)
        val entry = ldapServer.getEntry(newDn)
        assertEquals("Ivanov", entry.getAttributeValue("sn"))
        assertEquals("Пётр", entry.getAttributeValue("givenName"))
    }

    @Test
    fun editName_blankFields_noModify() = runBlocking {
        val impl = EditNameImpl(liveExecutor(), errors)
        val result = impl.editName(userDn, "", "")
        assertTrue(result.isSuccess)
        assertEquals(userDn, result.getOrNull())
        val entry = ldapServer.getEntry(userDn)
        assertEquals("Ivanov", entry.getAttributeValue("sn"))
        assertEquals("Ivan", entry.getAttributeValue("givenName"))
    }

    @Test
    fun editName_ldapError_returnsFailure() = runBlocking {
        val impl = EditNameImpl(
            failingExecutor(LDAPException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS, "denied")),
            errors
        )
        val result = impl.editName(userDn, "Пётр", null)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as? AdError
        assertEquals(AdError.Kind.INSUFFICIENT_RIGHTS, error?.kind)
    }

    @Test
    fun editName_bothNames_updatesCnToLastFirst() = runBlocking {
        val impl = EditNameImpl(liveExecutor(), errors)
        val result = impl.editName(userDn, "Пётр", "Сидоров")
        assertTrue(result.isSuccess)
        assertEquals("CN=Сидоров Пётр,$parentDn", result.getOrNull())
        val entry = ldapServer.getEntry(result.getOrNull()!!)
        assertEquals("Сидоров", entry.getAttributeValue("sn"))
        assertEquals("Пётр", entry.getAttributeValue("givenName"))
    }

    @Test
    fun editName_newSurname_replacesSurnameKeepsFirstName() = runBlocking {
        val impl = EditNameImpl(liveExecutor(), errors)

        val result = impl.editName(userDn, null, "Doe")

        assertTrue(result.isSuccess)
        val newDn = result.getOrNull()!!
        assertEquals("CN=Doe Ivan,$parentDn", newDn)
        val entry = ldapServer.getEntry(newDn)
        assertEquals("Doe", entry.getAttributeValue("sn"))
        assertEquals("Ivan", entry.getAttributeValue("givenName"))
        assertEquals("Doe Ivan", entry.getAttributeValue("displayName"))
    }

    @Test
    fun editName_blankInput_keepsExistingValue() = runBlocking {
        val impl = EditNameImpl(liveExecutor(), errors)

        val result = impl.editName(userDn, "", null)

        assertTrue(result.isSuccess)
        val entry = ldapServer.getEntry(userDn)
        assertEquals("Ivanov", entry.getAttributeValue("sn"))
        assertEquals("Ivan", entry.getAttributeValue("givenName"))
    }

    @Test
    fun editName_forbiddenCnChars_isRejected() = runBlocking {
        val impl = EditNameImpl(liveExecutor(), errors)

        for (bad in listOf("Си,доров", "Сидоров+Пётр", "Сидоров\"Пётр")) {
            val result = impl.editName(userDn, "Пётр", bad)
            assertTrue("should reject '$bad'", result.isFailure)
            assertTrue(
                "kind=VALIDATION for '$bad'",
                (result.exceptionOrNull() as AdError).kind == AdError.Kind.VALIDATION
            )
            // Nothing was changed.
            assertEquals(userDn, ldapServer.getEntry(userDn).dn)
        }
    }

    @Test
    fun editName_secondStepFails_reportsDnAlreadyChanged() = runBlocking {
        var call = 0
        val impl = EditNameImpl(
            object : LdapExecutor {
                override suspend fun <T> execute(block: (LDAPConnection) -> T): T {
                    call++
                    if (call >= 3) throw LDAPException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS)
                    return block(ldapServer.connection)
                }
            },
            errors
        )

        val result = impl.editName(userDn, "Пётр", null)

        assertTrue(result.isFailure)
        val err = result.exceptionOrNull() as AdError
        assertTrue(
            "message should mention the new DN: ${err.rawMessage}",
            err.rawMessage?.contains("DN already changed") == true
        )
        // The rename did happen before the failure.
        val renamed = ldapServer.getEntry("CN=Ivanov Пётр,$parentDn")
        assertEquals("CN=Ivanov Пётр,$parentDn", renamed.dn)
    }

    @Test
    fun editName_modifyThrowsAdError_keepsDnAlreadyChangedMessage() = runBlocking {
        var call = 0
        val impl = EditNameImpl(
            object : LdapExecutor {
                override suspend fun <T> execute(block: (LDAPConnection) -> T): T {
                    call++
                    return when {
                        call == 1 || call == 2 -> block(ldapServer.connection)
                        else -> throw AdError(
                            kind = AdError.Kind.NETWORK,
                            resultCode = null,
                            hexData = null,
                            rawMessage = "Connection closed"
                        )
                    }
                }
            },
            errors
        )

        val result = impl.editName(userDn, "Пётр", null)

        assertTrue(result.isFailure)
        val err = result.exceptionOrNull() as AdError
        assertTrue(
            "message should mention the new DN: ${err.rawMessage}",
            err.rawMessage?.contains("DN already changed") == true
        )
        // The rename did happen before the failure.
        val renamed = ldapServer.getEntry("CN=Ivanov Пётр,$parentDn")
        assertEquals("CN=Ivanov Пётр,$parentDn", renamed.dn)
    }
}
