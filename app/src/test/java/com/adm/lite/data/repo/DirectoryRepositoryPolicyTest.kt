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

package com.adm.lite.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.adm.lite.data.crypto.CredentialCodec
import com.adm.lite.data.crypto.CredentialStore
import com.adm.lite.data.crypto.SecretKeyProvider
import com.adm.lite.data.db.ServerDao
import com.adm.lite.data.db.ServerEntity
import com.adm.lite.data.prefs.SettingsStore
import com.adm.lite.ldap.LdapConnector
import com.adm.lite.ldap.LdapErrorMapper
import com.adm.lite.ldap.LdapSession
import com.adm.lite.ldap.ops.AccountProperties
import com.unboundid.ldap.sdk.LDAPConnection
import com.adm.lite.ldap.ops.AccountPropertiesUseCase
import com.adm.lite.ldap.ops.ChangePasswordUseCase
import com.adm.lite.ldap.ops.ModifyGroupsUseCase
import com.adm.lite.ldap.ops.EditNameUseCase
import com.adm.lite.ldap.ops.SearchGroupsUseCase
import com.adm.lite.ldap.ops.SearchUsersUseCase
import com.adm.lite.ldap.ops.ToggleAccountEnabledUseCase
import com.adm.lite.ldap.ops.UnlockAccountUseCase
import com.adm.lite.model.AdError
import com.adm.lite.model.AdUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

/**
 * Tests the security policy in [DirectoryRepository.changePassword]:
 * - PLAIN connection is blocked when requireSecureChannel is enabled
 * - PLAIN connection is allowed when policy is disabled
 * - LDAPS always passes the policy check
 * - Missing server returns NOT_FOUND
 */
class DirectoryRepositoryPolicyTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var settingsStore: SettingsStore
    private lateinit var fakeChangePassword: FakeChangePasswordUseCase
    private lateinit var repo: DirectoryRepository

    // ── Fakes ──────────────────────────────────────────────────────────

    private class FakeServerDao : ServerDao {
        private val servers = mutableMapOf<Long, ServerEntity>()
        fun add(entity: ServerEntity) { servers[entity.id] = entity }
        override suspend fun getById(id: Long): ServerEntity? = servers[id]
        override suspend fun insert(entity: ServerEntity): Long = entity.id
        override suspend fun update(entity: ServerEntity) { servers[entity.id] = entity }
        override suspend fun deleteById(id: Long) { servers.remove(id) }
        override fun observeAll(): Flow<List<ServerEntity>> = flowOf(servers.values.toList())
    }

    private class FakeChangePasswordUseCase : ChangePasswordUseCase {
        var changePasswordCalls = 0
        override suspend fun changePassword(
            userDn: String,
            mode: ChangePasswordUseCase.Mode,
            oldPassword: String?,
            newPassword: String
        ): Result<Unit> {
            changePasswordCalls++
            return Result.success(Unit)
        }
    }

    private object StubSearchUsers : SearchUsersUseCase {
        override suspend fun search(
            query: String, baseDn: String, pageSize: Int, cookie: String?
        ): Result<Pair<List<AdUser>, String?>> = Result.success(emptyList<AdUser>() to null)
    }

    private object StubModifyGroups : ModifyGroupsUseCase {
        override suspend fun modifyGroups(
            userDn: String, addGroupDns: List<String>, removeGroupDns: List<String>
        ): Result<com.adm.lite.ldap.ops.ModifyGroupsResult> =
            Result.success(com.adm.lite.ldap.ops.ModifyGroupsResult(emptyList(), emptyList()))
    }

    private object StubEditName : EditNameUseCase {
        override suspend fun editName(
            userDn: String, firstName: String?, lastName: String?
        ): Result<String> = Result.success(userDn)
    }

    private object StubAccountProperties : AccountPropertiesUseCase {
        override suspend fun get(userDn: String): Result<AccountProperties> =
            Result.success(
                AccountProperties(
                    forcePasswordChange = false,
                    passwordNeverExpires = false,
                    userCannotChangePassword = false
                )
            )
        override suspend fun set(userDn: String, properties: AccountProperties): Result<Unit> =
            Result.success(Unit)
    }

    private object StubToggleAccountEnabled : ToggleAccountEnabledUseCase {
        override suspend fun setEnabled(userDn: String, enabled: Boolean): Result<Unit> =
            Result.success(Unit)
    }

    private object StubUnlockAccount : UnlockAccountUseCase {
        override suspend fun unlock(userDn: String): Result<Unit> =
            Result.success(Unit)
    }

    private object StubSearchGroups : SearchGroupsUseCase {
        override suspend fun search(
            query: String, baseDn: String, pageSize: Int, cookie: String?
        ): Result<Pair<List<com.adm.lite.model.AdGroup>, String?>> =
            Result.success(emptyList<com.adm.lite.model.AdGroup>() to null)
    }

    // ── Setup ──────────────────────────────────────────────────────────

    @Before
    fun setup() {
        val fakeDao = FakeServerDao()

        // PLAIN server
        fakeDao.add(
            ServerEntity(
                id = 1L, name = "Plain", host = "plain.example.com",
                port = 389, baseDn = "dc=example,dc=com",
                tlsMode = "PLAIN", bindDn = "cn=admin,dc=example,dc=com",
                rememberPassword = false
            )
        )

        // LDAPS server
        fakeDao.add(
            ServerEntity(
                id = 2L, name = "Secure", host = "secure.example.com",
                port = 636, baseDn = "dc=example,dc=com",
                tlsMode = "LDAPS", bindDn = "cn=admin,dc=example,dc=com",
                rememberPassword = false
            )
        )

        // CredentialStore — constructor only, methods never called in these tests
        val testKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val credDataStore = PreferenceDataStoreFactory.create(scope = dsScope) {
            tmpFolder.newFile("cred.preferences_pb")
        }
        val credStore = CredentialStore(credDataStore, CredentialCodec(SecretKeyProvider { testKey }))

        val serverRepo = ServerRepository(fakeDao, credStore)

        // SettingsStore
        val settingsDataStore = PreferenceDataStoreFactory.create(scope = dsScope) {
            tmpFolder.newFile("settings.preferences_pb")
        }
        settingsStore = SettingsStore(settingsDataStore)

        fakeChangePassword = FakeChangePasswordUseCase()

        // LdapSession with dummy connector — not used by changePassword
        val dummyConnector = object : LdapConnector {
            override suspend fun connect(server: com.adm.lite.model.AdServer): LDAPConnection =
                throw UnsupportedOperationException("not used in policy tests")
        }
        val session = LdapSession(dummyConnector, LdapErrorMapper())

        repo = DirectoryRepository(
            session = session,
            searchUsers = StubSearchUsers,
            searchGroups = StubSearchGroups,
            changePassword = fakeChangePassword,
            modifyGroups = StubModifyGroups,
            editName = StubEditName,
            accountProperties = StubAccountProperties,
            toggleAccountEnabled = StubToggleAccountEnabled,
            unlockAccountUseCase = StubUnlockAccount,
            serverRepository = serverRepo,
            settingsStore = settingsStore
        )
    }

    @After
    fun tearDown() {
        dsScope.cancel()
    }

    // ── Tests ──────────────────────────────────────────────────────────

    @Test
    fun plainBlockedWhenPolicyEnabled() = runTest {
        // requireSecureChannelForPassword defaults to true
        val result = repo.changePassword(
            serverId = 1L,
            userDn = "cn=test,dc=example,dc=com",
            mode = ChangePasswordUseCase.Mode.ADMIN_RESET,
            oldPassword = null,
            newPassword = "NewP@ss1"
        )
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as AdError
        assertEquals(AdError.Kind.INSECURE_CHANNEL, error.kind)
        assertEquals(0, fakeChangePassword.changePasswordCalls)
    }

    @Test
    fun plainAllowedWhenPolicyDisabled() = runTest {
        settingsStore.setRequireSecureChannel(false)
        val result = repo.changePassword(
            serverId = 1L,
            userDn = "cn=test,dc=example,dc=com",
            mode = ChangePasswordUseCase.Mode.ADMIN_RESET,
            oldPassword = null,
            newPassword = "NewP@ss1"
        )
        assertTrue(result.isSuccess)
        assertEquals(1, fakeChangePassword.changePasswordCalls)
    }

    @Test
    fun ldapsPassesWhenPolicyEnabled() = runTest {
        val result = repo.changePassword(
            serverId = 2L,
            userDn = "cn=test,dc=example,dc=com",
            mode = ChangePasswordUseCase.Mode.ADMIN_RESET,
            oldPassword = null,
            newPassword = "NewP@ss1"
        )
        assertTrue(result.isSuccess)
        assertEquals(1, fakeChangePassword.changePasswordCalls)
    }

    @Test
    fun notFoundForMissingServer() = runTest {
        val result = repo.changePassword(
            serverId = 999L,
            userDn = "cn=test,dc=example,dc=com",
            mode = ChangePasswordUseCase.Mode.ADMIN_RESET,
            oldPassword = null,
            newPassword = "NewP@ss1"
        )
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as AdError
        assertEquals(AdError.Kind.NOT_FOUND, error.kind)
    }
}
