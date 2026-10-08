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
import com.adm.lite.model.AdError.Kind
import com.adm.lite.model.AdServer
import com.adm.lite.model.ConnectionState
import com.unboundid.ldap.sdk.LDAPConnection
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.LDAPResult
import com.unboundid.ldap.sdk.ResultCode
import com.unboundid.ldap.sdk.SimpleBindRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Allows callers to execute an LDAP operation against the current connection.
 */
interface LdapExecutor {
    suspend fun <T> execute(block: (LDAPConnection) -> T): T
}

/**
 * LDAP session manages connection lifecycle. Bind creates new connection; disconnect releases it.
 *
 * Close connection on errors: never leave connection in broken state
 */
class LdapSession(
    private val factory: LdapConnector,
    private val errors: LdapErrorMapper
) : LdapExecutor {

    @Volatile
    private var connection: LDAPConnection? = null

    /** Base DN from the currently connected server. Set during [bind], cleared on [disconnect]. */
    @Volatile
    var baseDn: String = ""
        private set

    @Volatile
    var currentServerId: Long = 0L
        private set

    @Volatile
    var currentServerName: String? = null
        private set

    /** Credentials for the last successful [bind], cached for transparent reconnection. */
    @Volatile
    private var lastServer: AdServer? = null

    @Volatile
    private var lastPassword: String? = null

    private val mutex = Mutex()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /**
     * Bind to the given [AdServer] using the provided password.
     *
     * Creates a new connection, stores it before bind attempt (for diagnostics on failure),
     * and updates state accordingly. Connection is discarded if bind fails.
     */
    suspend fun bind(server: AdServer, password: String): Result<Unit> {
        _state.value = ConnectionState.Connecting

        return try {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    clearConnectionLocked()

                    try {
                        val conn = factory.connect(server)
                        connection = conn
                        currentServerId = server.id
                        currentServerName = server.name

                        val result = conn.bind(SimpleBindRequest(server.bindDn, password))
                        if (result.resultCode == ResultCode.SUCCESS) {
                            baseDn = server.baseDn
                            lastServer = server
                            lastPassword = password
                            _state.value = ConnectionState.Connected(server.id, server.bindDn)
                            Result.success(Unit)
                        } else {
                            val adError = errors.map(result)
                            clearConnectionLocked()
                            _state.value = ConnectionState.Failed(adError)
                            Result.failure(adError)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        clearConnectionLocked()
                        throw e
                    } catch (e: LDAPException) {
                        val adError = errors.map(e)
                        clearConnectionLocked()
                        _state.value = ConnectionState.Failed(adError)
                        Result.failure(adError)
                    } catch (e: AdError) {
                        // e.g. TLS setup failure from LdapConnectionFactory
                        clearConnectionLocked()
                        _state.value = ConnectionState.Failed(e)
                        Result.failure(e)
                    } catch (e: Exception) {
                        clearConnectionLocked()
                        val adError = AdError(Kind.UNKNOWN, null, null, e.message ?: "Bind failed")
                        _state.value = ConnectionState.Failed(adError)
                        Result.failure(adError)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            _state.value = ConnectionState.Disconnected
            throw e
        }
    }

    private fun clearConnectionLocked() {
        connection?.close()
        connection = null
        baseDn = ""
        lastServer = null
        lastPassword = null
    }

    /**
     * Disconnect from the current server and release the connection.
     *
     * Takes [mutex], so it never tears the connection out from under a running
     * [execute] or [bind]. Cached bind credentials ([lastServer], [lastPassword])
     * are discarded.
     */
    suspend fun disconnect() {
        mutex.withLock { clearConnectionLocked() }
        _state.value = ConnectionState.Disconnected
    }

    /**
     * Execute a block against the current LDAP connection.
     *
     * Takes [mutex] for the whole block: the connection is re-checked under the
     * lock, so two coroutines cannot both notice a dead socket and race to
     * replace it. When the cached connection is dead, it is closed and re-bound
     * transparently so the block always runs against a live socket.
     *
     * @throws AdError if no connection is established or reconnect fails
     */
    override suspend fun <T> execute(block: (LDAPConnection) -> T): T {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val conn = connection
                    ?: throw AdError(
                        kind = Kind.NETWORK, resultCode = null, hexData = null,
                        rawMessage = "Not connected"
                    )
                if (conn.isConnected) {
                    block(conn)
                } else {
                    conn.close()
                    val newConn = rebindOrThrowLocked()
                    block(newConn)
                }
            }
        }
    }

    /**
     * Re-connect and re-bind using the credentials from the last successful [bind].
     *
     * Active Directory closes idle connections via MaxIdleTime (~30 min by default),
     * so the first operation after an idle period would otherwise fail with a raw
     * network error. This method exists to restore the connection transparently,
     * so callers of [execute] never have to handle a dead socket themselves.
     *
     * The password lives only in the [lastPassword] field, which is [String]-valued
     * and [Volatile]-visible; it is cleared by [disconnect].
     *
     * @throws AdError if no cached credentials exist or reconnection fails
     */
    private suspend fun rebindOrThrowLocked(): LDAPConnection {
        val server = lastServer ?: throw AdError(
            kind = Kind.NETWORK, resultCode = null, hexData = null,
            rawMessage = "Not connected"
        )
        val password = lastPassword ?: throw AdError(
            kind = Kind.NETWORK, resultCode = null, hexData = null,
            rawMessage = "Not connected"
        )

        val candidate = withContext(Dispatchers.IO) {
            try {
                factory.connect(server)
            } catch (e: LDAPException) {
                val adError = errors.map(e)
                connection = null
                _state.value = ConnectionState.Failed(adError)
                throw adError
            } catch (e: AdError) {
                connection = null
                _state.value = ConnectionState.Failed(e)
                throw e
            } catch (e: Exception) {
                val adError = AdError(Kind.UNKNOWN, null, null, e.message ?: "Reconnect failed")
                connection = null
                _state.value = ConnectionState.Failed(adError)
                throw adError
            }
        }

        try {
            val result = candidate.bind(SimpleBindRequest(server.bindDn, password))
            if (result.resultCode != ResultCode.SUCCESS) {
                val adError = errors.map(result)
                candidate.close()
                connection = null
                _state.value = ConnectionState.Failed(adError)
                throw adError
            }
        } catch (e: LDAPException) {
            val adError = errors.map(e)
            candidate.close()
            connection = null
            _state.value = ConnectionState.Failed(adError)
            throw adError
        } catch (e: AdError) {
            candidate.close()
            connection = null
            _state.value = ConnectionState.Failed(e)
            throw e
        } catch (e: Exception) {
            val adError = AdError(Kind.UNKNOWN, null, null, e.message ?: "Reconnect failed")
            candidate.close()
            connection = null
            _state.value = ConnectionState.Failed(adError)
            throw adError
        }

        connection?.close()
        connection = candidate
        baseDn = server.baseDn
        _state.value = ConnectionState.Connected(server.id, server.bindDn)
        return candidate
    }
}
