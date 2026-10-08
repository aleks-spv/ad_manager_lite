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
import com.unboundid.ldap.sdk.SearchResult
import com.unboundid.ldap.sdk.SearchResultEntry
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl
import com.unboundid.ldap.sdk.Entry
import com.unboundid.ldif.LDIFReader
import com.unboundid.util.Base64
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import java.io.StringReader
import kotlinx.coroutines.runBlocking

class SearchUsersPagingTest {

    private lateinit var server: InMemoryDirectoryServer
    private lateinit var connection: LDAPConnection
    private lateinit var impl: SearchUsersImpl

    @Before
    fun setUp() {
        val config = InMemoryDirectoryServerConfig("dc=example,dc=com")
        config.addAdditionalBindCredentials("cn=admin,dc=example,dc=com", "secret")
        config.setSchema(null)
        server = InMemoryDirectoryServer(config)
        server.startListening()
        // UnboundID's in-memory server does not create the base DN itself.
        server.add(
            Entry("dn: dc=example,dc=com", "objectClass: top", "objectClass: domain", "dc: example")
        )
        connection = server.connection

        // Add 250 user entries
        for (i in 0 until 250) {
            val entry = Entry(
                "dn: cn=user$i,dc=example,dc=com",
                "objectClass: top",
                "objectClass: user",
                "objectCategory: person",
                "cn: user$i",
                "sAMAccountName: user$i",
                "userAccountControl: 512"
            )
            server.add(entry)
        }

        val executor = object : LdapExecutor {
            override suspend fun <T> execute(block: (LDAPConnection) -> T): T = block(connection)
        }
        impl = SearchUsersImpl(executor, LdapErrorMapper())
    }

    @After
    fun tearDown() {
        connection.close()
        server.shutDown(true)
    }

    @Test
    fun `full page traversal collects all 250 users`() = runBlocking {
        val allDns = mutableSetOf<String>()
        var cookie: String? = null
        var iterations = 0

        while (cookie != "DONE") {
            iterations++
            assertTrue("Too many iterations ($iterations), possible infinite loop", iterations <= 10)

            val result = impl.search(
                query = "",
                baseDn = "dc=example,dc=com",
                pageSize = 100,
                cookie = cookie
            )

            assertTrue(result.isSuccess)
            val (users, nextCookie) = result.getOrThrow()
            allDns.addAll(users.map { it.dn })

            if (nextCookie == null || nextCookie.isEmpty()) {
                cookie = "DONE"
            } else {
                cookie = nextCookie
            }
        }

        assertEquals(250, allDns.size)
        assertEquals(3, iterations)
    }

    @Test
    fun `cookie survives binary round-trip`() = runBlocking {
        val result = impl.search(
            query = "",
            baseDn = "dc=example,dc=com",
            pageSize = 100,
            cookie = null
        )

        assertTrue(result.isSuccess)
        val (users, nextCookie) = result.getOrThrow()
        assertEquals(100, users.size)
        assertNotNull(nextCookie)
        assertTrue(nextCookie!!.isNotEmpty())

        // Cookie round-trip: decode then encode should produce the same string
        val decoded = Base64.decode(nextCookie)
        val reEncoded = Base64.encode(decoded)
        assertEquals(nextCookie, reEncoded)

        // Verify binary safety with non-UTF8 sequence
        val raw = byteArrayOf(0xC3.toByte(), 0x28, 0x00, 0xFF.toByte())
        assertArrayEquals(raw, Base64.decode(Base64.encode(raw)))
    }

    @Test
    fun `last page returns null cookie`() = runBlocking {
        var cookie: String? = null
        var lastResult: SearchResult? = null

        while (true) {
            val result = impl.search(
                query = "",
                baseDn = "dc=example,dc=com",
                pageSize = 100,
                cookie = cookie
            )
            assertTrue(result.isSuccess)
            val (users, nextCookie) = result.getOrThrow()
            assertTrue(users.isNotEmpty())

            if (nextCookie == null || nextCookie.isEmpty()) {
                // This is the last page
                assertTrue("Last page should return non-empty user list", users.isNotEmpty())
                return@runBlocking
            }
            cookie = nextCookie
        }
    }
}
