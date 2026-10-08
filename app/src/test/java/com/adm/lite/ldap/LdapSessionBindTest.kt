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

package com.adm.lite.ldap

import com.adm.lite.model.AdError
import com.adm.lite.model.AdServer
import com.adm.lite.model.ConnectionState
import com.adm.lite.model.TlsMode
import com.unboundid.ldap.sdk.LDAPConnection
import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldap.sdk.Entry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LdapSessionBindTest {

    private lateinit var ldapServer: InMemoryDirectoryServer
    private lateinit var connector: LdapConnector
    private lateinit var session: LdapSession

    private val adServer = AdServer(
        id = 1,
        name = "Test Server",
        host = "localhost",
        port = 0,
        baseDn = "dc=example,dc=com",
        tlsMode = TlsMode.PLAIN,
        bindDn = "cn=admin,dc=example,dc=com"
    )

    @Before
    fun setUp() {
        val config = InMemoryDirectoryServerConfig("dc=example,dc=com")
        config.addAdditionalBindCredentials("cn=admin,dc=example,dc=com", "secret")
        config.setSchema(null)
        ldapServer = InMemoryDirectoryServer(config)
        ldapServer.startListening()
        // UnboundID's in-memory server does not create the base DN itself.
        ldapServer.add(
            Entry("dn: dc=example,dc=com", "objectClass: top", "objectClass: domain", "dc: example")
        )

        val entry = Entry(
            "dn: cn=admin,dc=example,dc=com",
            "objectClass: top",
            "objectClass: person",
            "cn: admin"
        )
        ldapServer.add(entry)

        connector = object : LdapConnector {
            override suspend fun connect(server: AdServer): LDAPConnection = ldapServer.connection
        }
        session = LdapSession(connector, LdapErrorMapper())
    }

    @After
    fun tearDown() {
        ldapServer.shutDown(true)
    }

    @Test
    fun `successful bind sets connected state`() = runBlocking {
        val result = session.bind(adServer, "secret")
        assertTrue(result.isSuccess)
        assertEquals("dc=example,dc=com", session.baseDn)
        val state = session.state.value
        assertTrue(state is ConnectionState.Connected)
        val connected = state as ConnectionState.Connected
        assertEquals(1L, connected.serverId)
        assertEquals("cn=admin,dc=example,dc=com", connected.boundAs)
    }

    @Test
    fun `wrong password gives INVALID_CREDENTIALS`() = runBlocking {
        val result = session.bind(adServer, "wrong")
        assertTrue(result.isFailure)
        val state = session.state.value
        assertTrue(state is ConnectionState.Failed)
        val failed = state as ConnectionState.Failed
        assertEquals(AdError.Kind.INVALID_CREDENTIALS, failed.error.kind)
        assertEquals(49, failed.error.resultCode)
    }

    @Test
    fun `disconnect clears state`() = runBlocking {
        session.bind(adServer, "secret")
        assertTrue(session.state.value is ConnectionState.Connected)

        session.disconnect()
        assertEquals(ConnectionState.Disconnected, session.state.value)
        assertEquals("", session.baseDn)
    }

    @Test
    fun `reconnect failure propagates AdError and sets session failed`() = runBlocking {
        var connectCount = 0
        val fakeConnector = object : LdapConnector {
            override suspend fun connect(s: AdServer): LDAPConnection {
                connectCount++
                return if (connectCount == 1) {
                    ldapServer.connection
                } else {
                    throw AdError(
                        kind = AdError.Kind.INVALID_CREDENTIALS,
                        resultCode = 49,
                        hexData = null,
                        rawMessage = "Bad credentials"
                    )
                }
            }
        }
        val reconnectSession = LdapSession(fakeConnector, LdapErrorMapper())

        // Successful initial bind using the real in-memory server
        val bindResult = reconnectSession.bind(adServer, "secret")
        assertTrue("bind should succeed", bindResult.isSuccess)

        // Simulate dead connection: close it so next execute triggers reconnect
        reconnectSession.execute { conn ->
            conn.close()
            null
        }

        // Next execute must attempt reconnect → second connect → AdError
        val thrown = try {
            reconnectSession.execute { _ -> null }
            throw AssertionError("expected AdError")
        } catch (e: AdError) {
            e
        }
        assertEquals("AdError kind", AdError.Kind.INVALID_CREDENTIALS, thrown.kind)

        val state = reconnectSession.state.value
        assertTrue("state should be Failed", state is ConnectionState.Failed)
        val failed = state as ConnectionState.Failed
        assertEquals("failed error kind", AdError.Kind.INVALID_CREDENTIALS, failed.error.kind)
    }
}
