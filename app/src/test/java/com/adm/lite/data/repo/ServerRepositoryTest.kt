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
import com.adm.lite.model.AdServer
import com.adm.lite.model.TlsMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
class ServerRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var fakeDao: FakeServerDao
    private lateinit var credentials: CredentialStore
    private lateinit var repository: ServerRepository
    private lateinit var dsScope: CoroutineScope

    @Before
    fun setup() {
        fakeDao = FakeServerDao()
        dsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val testKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val dataStore = PreferenceDataStoreFactory.create(scope = dsScope) {
            tmpFolder.newFile("cred.preferences_pb")
        }
        credentials = CredentialStore(dataStore, CredentialCodec(SecretKeyProvider { testKey }))
        repository = ServerRepository(fakeDao, credentials)
    }

    @After
    fun tearDown() {
        dsScope.cancel()
    }

    private fun testServer(
        id: Long = 0,
        name: String = "Test",
        rememberPassword: Boolean = false
    ) = AdServer(
        id = id,
        name = name,
        host = "dc.example.com",
        port = 636,
        baseDn = "dc=example,dc=com",
        tlsMode = TlsMode.LDAPS,
        bindDn = "cn=admin,dc=example,dc=com",
        rememberPassword = rememberPassword
    )

    private fun entity(
        name: String = "Test",
        rememberPassword: Boolean = false
    ) = ServerEntity(
        name = name,
        host = "dc.example.com",
        port = 636,
        baseDn = "dc=example,dc=com",
        tlsMode = "LDAPS",
        bindDn = "cn=admin,dc=example,dc=com",
        rememberPassword = rememberPassword
    )

    @Test
    fun saveNewServer_insertsAndStoresPassword() = runBlocking {
        val id = repository.save(testServer(rememberPassword = true), "secret")
        assertEquals(1L, id)
        assertEquals(1, fakeDao.servers.size)
        assertEquals("secret", repository.savedPassword(id))
    }

    @Test
    fun saveWithoutRemember_doesNotStorePassword() = runBlocking {
        val id = repository.save(testServer(rememberPassword = false), "secret")
        assertEquals(1L, id)
        assertNull(repository.savedPassword(id))
    }

    @Test
    fun saveExisting_clearsPasswordWhenRememberOff() = runBlocking {
        val id = repository.save(testServer(rememberPassword = true), "secret")
        assertEquals("secret", repository.savedPassword(id))
        repository.save(testServer(id = id, rememberPassword = false), "new")
        assertNull(repository.savedPassword(id))
    }

    @Test
    fun delete_clearsPassword() = runBlocking {
        val id = repository.save(testServer(rememberPassword = true), "secret")
        repository.delete(id)
        assertNull(repository.savedPassword(id))
        assertNull(fakeDao.getById(id))
    }

    @Test
    fun observeServers_sortedByName() = runBlocking {
        fakeDao.insert(entity(name = "Zeta"))
        fakeDao.insert(entity(name = "Alpha"))
        val servers = repository.observeServers().first()
        assertEquals(listOf("Alpha", "Zeta"), servers.map { it.name })
    }

    @Test
    fun getById_returnsServer() = runBlocking {
        val id = repository.save(testServer(name = "Prod", rememberPassword = true), "pw")
        val server = repository.get(id)
        assertEquals("Prod", server?.name)
        assertEquals("pw", repository.savedPassword(id))
    }

    private class FakeServerDao : ServerDao {
        val servers = mutableListOf<ServerEntity>()
        private val flow = MutableStateFlow<List<ServerEntity>>(emptyList())

        override suspend fun insert(entity: ServerEntity): Long {
            val id = (servers.maxOfOrNull { it.id } ?: 0L) + 1L
            val stored = entity.copy(id = id)
            servers.add(stored)
            flow.value = servers.sortedBy { it.name }
            return id
        }

        override suspend fun update(entity: ServerEntity) {
            val idx = servers.indexOfFirst { it.id == entity.id }
            if (idx >= 0) servers[idx] = entity else servers.add(entity)
            flow.value = servers.sortedBy { it.name }
        }

        override suspend fun deleteById(id: Long) {
            servers.removeAll { it.id == id }
            flow.value = servers.sortedBy { it.name }
        }

        override suspend fun getById(id: Long): ServerEntity? =
            servers.find { it.id == id }

        override fun observeAll(): Flow<List<ServerEntity>> = flow
    }
}
