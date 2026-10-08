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

package com.adm.lite.model

enum class TlsMode { LDAPS, START_TLS, PLAIN }

data class AdServer(
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val baseDn: String,
    val tlsMode: TlsMode,
    val bindDn: String,
    val rememberPassword: Boolean = false
)

data class AdUser(
    val dn: String,
    val sAMAccountName: String?,
    val userPrincipalName: String?,
    val displayName: String?,
    val givenName: String?,
    val sn: String?,
    val mail: String?,
    val enabled: Boolean,
    val lockedOut: Boolean,
    val groups: List<String> = emptyList()
)

data class AdGroup(
    val dn: String,
    val name: String
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val serverId: Long, val boundAs: String) : ConnectionState
    data class Failed(val error: AdError) : ConnectionState
}

data class AdError(
    val kind: Kind,
    val resultCode: Int?,
    val hexData: String?,
    val rawMessage: String?
) : RuntimeException(rawMessage) {
    override fun fillInStackTrace(): Throwable = this
    enum class Kind {
        NETWORK,
        TLS,
        INVALID_CREDENTIALS,
        ACCOUNT_DISABLED,
        ACCOUNT_LOCKED,
        PASSWORD_POLICY,
        WRONG_OLD_PASSWORD,
        MUST_CHANGE_PASSWORD,
        INSUFFICIENT_RIGHTS,
        NOT_FOUND,
        INSECURE_CHANNEL,
        TIMEOUT,
        VALIDATION,
        SERVER_MISMATCH,
        UNKNOWN
    }
}
