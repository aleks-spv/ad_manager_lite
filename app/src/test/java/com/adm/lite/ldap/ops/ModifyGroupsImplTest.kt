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
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.ResultCode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * Unit tests for [ModifyGroupsImpl].
 *
 * Tests use a fake executor that never performs real LDAP operations — all network
 * interactions are simulated via controlled exceptions from the mock connection.
 */
class ModifyGroupsImplTest {

    private lateinit var impl: ModifyGroupsImpl
    private val errors = LdapErrorMapper()

    @Before
    fun setUp() {
        impl = ModifyGroupsImpl(
            executor = object : LdapExecutor {
                override suspend fun <T> execute(block: (LDAPConnection) -> T): T =
                    throw UnsupportedOperationException("This test must not touch a live LDAP connection")
            },
            errors = errors
        )
    }

    @Test
    fun modifyGroups_emptyLists_returnsEmptyResult() = runBlocking {
        val result = impl.modifyGroups("cn=user,dc=test", emptyList(), emptyList())
        assertTrue(result.isSuccess)
        val applied = result.getOrNull()!!
        assertNotNull(applied)
        assertTrue(applied.appliedDns.isEmpty())
        assertTrue(applied.failures.isEmpty())
    }

    @Test
    fun modifyGroups_successAdd_returnsAppliedDns() = runBlocking {
        val addDns = listOf("cn=grp1,dc=test", "cn=grp2,dc=test")
        var callCount = 0
        val fakeExecutor = object : LdapExecutor {
            override suspend fun <T> execute(block: (LDAPConnection) -> T): T {
                callCount++
                return Unit as T
            }
        }
        impl = ModifyGroupsImpl(fakeExecutor, errors)

        val result = impl.modifyGroups("cn=user,dc=test", addDns, emptyList())
        assertTrue(result.isSuccess)
        val applied = result.getOrNull()!!
        assertEquals(2, applied.appliedDns.size)
        assertEquals(addDns[0], applied.appliedDns[0])
        assertEquals(addDns[1], applied.appliedDns[1])
        assertTrue(applied.failures.isEmpty())
    }

    @Test
    fun modifyGroups_successRemove_returnsAppliedDns() = runBlocking {
        val removeDns = listOf("cn=grp1,dc=test", "cn=grp2,dc=test")
        var callCount = 0
        val fakeExecutor = object : LdapExecutor {
            override suspend fun <T> execute(block: (LDAPConnection) -> T): T {
                callCount++
                return Unit as T
            }
        }
        impl = ModifyGroupsImpl(fakeExecutor, errors)

        val result = impl.modifyGroups("cn=user,dc=test", emptyList(), removeDns)
        assertTrue(result.isSuccess)
        val applied = result.getOrNull()!!
        assertEquals(2, applied.appliedDns.size)
        assertEquals(removeDns[0], applied.appliedDns[0])
        assertEquals(removeDns[1], applied.appliedDns[1])
        assertTrue(applied.failures.isEmpty())
    }

    @Test
    fun modifyGroups_addThenRemove_allAppliedInOrder() = runBlocking {
        val addDns = listOf("cn=grp-add,dc=test")
        val removeDns = listOf("cn=grp-rm,dc=test")
        var callCount = 0
        val fakeExecutor = object : LdapExecutor {
            override suspend fun <T> execute(block: (LDAPConnection) -> T): T {
                callCount++
                return Unit as T
            }
        }
        impl = ModifyGroupsImpl(fakeExecutor, errors)

        val result = impl.modifyGroups("cn=user,dc=test", addDns, removeDns)
        assertTrue(result.isSuccess)
        val applied = result.getOrNull()!!
        assertEquals(2, applied.appliedDns.size)
        // Adds are processed first
        assertEquals(addDns[0], applied.appliedDns[0])
        assertEquals(removeDns[0], applied.appliedDns[1])
        assertTrue(applied.failures.isEmpty())
    }

    @Test
    fun modifyGroups_ldapException_returnsAppliedAndFailures() = runBlocking {
        val fakeExecutor = object : LdapExecutor {
            override suspend fun <T> execute(block: (LDAPConnection) -> T): T =
                throw LDAPException(ResultCode.OPERATIONS_ERROR, "AD error: access denied")
        }
        impl = ModifyGroupsImpl(fakeExecutor, errors)

        val result = impl.modifyGroups("cn=user,dc=test", listOf("cn=grp,dc=test"), emptyList())
        assertTrue(result.isSuccess)
        val applied = result.getOrNull()!!
        assertTrue(applied.appliedDns.isEmpty())
        assertEquals(1, applied.failures.size)
        val (failedDn, error) = applied.failures[0]
        assertEquals("cn=grp,dc=test", failedDn)
        assertNotNull(error)
    }

    @Test
    fun modifyGroups_partialAddFailure_returnsAppliedBeforeFailure() = runBlocking {
        var callCount = 0
        val fakeExecutor = object : LdapExecutor {
            override suspend fun <T> execute(block: (LDAPConnection) -> T): T {
                callCount++
                if (callCount == 1) {
                    // First DN succeeds
                    return Unit as T
                }
                // Second DN fails — stops further processing
                throw LDAPException(ResultCode.OPERATIONS_ERROR, "access denied")
            }
        }
        impl = ModifyGroupsImpl(fakeExecutor, errors)

        val result = impl.modifyGroups(
            "cn=user,dc=test",
            listOf("cn=grp1,dc=test", "cn=grp2,dc=test", "cn=grp3,dc=test"),
            emptyList()
        )
        assertTrue(result.isSuccess)
        val applied = result.getOrNull()!!
        assertEquals(1, applied.appliedDns.size)
        assertEquals("cn=grp1,dc=test", applied.appliedDns[0])
        assertEquals(1, applied.failures.size)
        val (failedDn, error) = applied.failures[0]
        assertEquals("cn=grp2,dc=test", failedDn)
        assertNotNull(error)
    }

    @Test
    fun modifyGroups_genericException_returnsNetworkAdError() = runBlocking {
        val fakeExecutor = object : LdapExecutor {
            override suspend fun <T> execute(block: (LDAPConnection) -> T): T =
                throw RuntimeException("connection pool exhausted")
        }
        impl = ModifyGroupsImpl(fakeExecutor, errors)

        val result = impl.modifyGroups("cn=user,dc=test", listOf("cn=grp,dc=test"), emptyList())
        assertTrue(result.isSuccess)
        val applied = result.getOrNull()!!
        assertTrue(applied.appliedDns.isEmpty())
        assertEquals(1, applied.failures.size)
        val (failedDn, error) = applied.failures[0]
        assertEquals("cn=grp,dc=test", failedDn)
        assertNotNull(error)
        assertEquals(AdError.Kind.NETWORK, error?.kind)
    }

}
