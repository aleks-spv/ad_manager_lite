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

package com.adm.lite.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServerDaoTest {

    private lateinit var db: AdmDatabase
    private lateinit var dao: ServerDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AdmDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.serverDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun testEntity(name: String = "Test Server") = ServerEntity(
        name = name,
        host = "dc.example.com",
        port = 636,
        baseDn = "dc=example,dc=com",
        tlsMode = "LDAPS",
        bindDn = "cn=admin,dc=example,dc=com",
        rememberPassword = false
    )

    @Test
    fun insertReturnsIdAndGetByIdReturnsSameFields() = runTest {
        val id = dao.insert(testEntity())
        assertTrue(id > 0)
        val fetched = dao.getById(id)
        assertNotNull(fetched)
        assertEquals("Test Server", fetched!!.name)
        assertEquals("dc.example.com", fetched.host)
    }

    @Test
    fun updateChangesHost() = runTest {
        val id = dao.insert(testEntity())
        val original = dao.getById(id)!!
        dao.update(original.copy(host = "new.example.com"))
        val updated = dao.getById(id)
        assertEquals("new.example.com", updated!!.host)
    }

    @Test
    fun deleteByIdMakesGetByIdReturnNull() = runTest {
        val id = dao.insert(testEntity())
        dao.deleteById(id)
        assertNull(dao.getById(id))
    }

    @Test
    fun duplicateNameThrowsConstraintException() = runTest {
        dao.insert(testEntity("Unique"))
        try {
            dao.insert(testEntity("Unique"))
            assertTrue("Expected SQLiteConstraintException", false)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // Expected
        }
    }

    @Test
    fun observeAllReturnsSortedList() = runTest {
        dao.insert(testEntity("Z Server"))
        dao.insert(testEntity("A Server"))
        val servers = dao.observeAll().first()
        assertEquals(2, servers.size)
        assertEquals("A Server", servers[0].name)
        assertEquals("Z Server", servers[1].name)
    }
}
