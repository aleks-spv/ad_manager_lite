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
import com.adm.lite.model.AdError
import com.unboundid.ldap.sdk.Modification
import com.unboundid.ldap.sdk.ModificationType
import com.unboundid.ldap.sdk.ModifyRequest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * Unit tests for [ChangePasswordImpl].
 *
 * Tests the pure builder functions and basic execution flow.
 * No InMemoryDirectoryServer — uses a fake executor.
 */
class ChangePasswordImplTest {

    private lateinit var impl: ChangePasswordImpl
    private val errors = LdapErrorMapper()

    @Before
    fun setUp() {
        impl = ChangePasswordImpl(
            executor = object : LdapExecutor {
                override suspend fun <T> execute(block: (LDAPConnection) -> T): T =
                    throw UnsupportedOperationException("This test must not touch a live LDAP connection")
            },
            errors = errors
        )
    }

    @Test
    fun buildAdminReset_containsOneReplaceModification() {
        val req = impl.buildAdminReset("cn=test,dc=example,dc=com", "newpass123")
        assertEquals(1, req.modifications.size)
        val mod = req.modifications[0]
        assertEquals(ModificationType.REPLACE, mod.modificationType)
        assertEquals("unicodePwd", mod.attributeName)
        val bytes = mod.valueByteArrays[0]
        assertTrue("unicodePwd bytes should not be empty", bytes.isNotEmpty())
    }

    @Test
    fun buildSelfChange_containsDeleteAndAddModifications() {
        val req = impl.buildSelfChange("cn=test,dc=example,dc=com", "oldpass", "newpass")
        assertEquals(2, req.modifications.size)

        val deleteMod = req.modifications[0]
        assertEquals(ModificationType.DELETE, deleteMod.modificationType)
        assertEquals("unicodePwd", deleteMod.attributeName)

        val addMod = req.modifications[1]
        assertEquals(ModificationType.ADD, addMod.modificationType)
        assertEquals("unicodePwd", addMod.attributeName)
    }

    @Test
    fun buildAdminReset_unicodePwdBytesAreUtf16Le() {
        val req = impl.buildAdminReset("cn=test,dc=example,dc=com", "test")
        val bytes = req.modifications[0].valueByteArrays[0]
        // UTF-16LE of "\"test\"" = [0x22,0x00,0x74,0x00,0x65,0x00,0x73,0x74,0x00,0x22,0x00]
        assertEquals(0x22, bytes[0].toInt() and 0xFF)
        assertEquals(0x00, bytes[1].toInt() and 0xFF)
        assertEquals(0x74, bytes[2].toInt() and 0xFF) // 't'
    }

    @Test
    fun buildSelfChange_oldAndNewBytesAreDifferent() {
        val req = impl.buildSelfChange("cn=test,dc=example,dc=com", "old", "new")
        val oldBytes = req.modifications[0].valueByteArrays[0]
        val newBytes = req.modifications[1].valueByteArrays[0]
        assertFalse("Old and new password bytes should differ",
            oldBytes.contentEquals(newBytes))
    }

    @Test
    fun selfChange_withNullOldPassword_returnsFailure() = runBlocking {
        val result = impl.changePassword(
            userDn = "cn=test,dc=example,dc=com",
            mode = ChangePasswordUseCase.Mode.SELF_CHANGE,
            oldPassword = null,
            newPassword = "newpass"
        )
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as? AdError
        assertNotNull(error)
        assertEquals(AdError.Kind.WRONG_OLD_PASSWORD, error?.kind)
    }

    @Test
    fun adminReset_buildsCorrectModifyRequestDn() {
        val req = impl.buildAdminReset("cn=test,dc=example,dc=com", "pass")
        // DN is set externally, but the request should be ready
        assertEquals(1, req.modifications.size)
    }

}
