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

package com.adm.lite.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppSettings(
    val requireSecureChannelForPassword: Boolean = true,
    val connectTimeoutMs: Int = 10_000,
    val responseTimeoutMs: Int = 30_000,
    val customCaPem: String? = null,
    val trustAllCertificates: Boolean = false
)

/**
 * App settings backed by DataStore Preferences.
 *
 * KDoc for [requireSecureChannelForPassword]:
 * When enabled, password change operations require a secure (TLS) channel.
 * Disabling this flag only removes the client-side check — Active Directory
 * itself will still refuse to write `unicodePwd` over an unencrypted connection
 * (except AD LDS with `fAllowPasswordOperationsOverNonSecureConnection`).
 * The UI displays a warning when this is disabled.
 *
 * @param scope long-lived scope used to keep [settingsState] warm. Production code
 *   passes the application scope from `AppContainer`; tests rely on the default.
 */
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private object Keys {
        val REQUIRE_SECURE_CHANNEL = booleanPreferencesKey("require_secure_channel")
        val CONNECT_TIMEOUT = intPreferencesKey("connect_timeout_ms")
        val RESPONSE_TIMEOUT = intPreferencesKey("response_timeout_ms")
        val CUSTOM_CA_PEM = stringPreferencesKey("custom_ca_pem")
        val TRUST_ALL_CERTS = booleanPreferencesKey("trust_all_certificates")
    }

    /**
     * Settings stream.
     *
     * A corrupted or unreadable `preferences_pb` file surfaces as an
     * [IOException] on the DataStore flow, which would otherwise propagate to
     * every collector (including `LdapConnectionFactory` and
     * `DirectoryRepository.changePassword`) and break them permanently. Per the
     * official DataStore guidance the caller owns IOException handling, so we
     * fall back to default preferences here; any other exception is fatal and
     * is rethrown.
     */
    val settings: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            AppSettings(
                requireSecureChannelForPassword = prefs[Keys.REQUIRE_SECURE_CHANNEL] ?: true,
                connectTimeoutMs = prefs[Keys.CONNECT_TIMEOUT] ?: 10_000,
                responseTimeoutMs = prefs[Keys.RESPONSE_TIMEOUT] ?: 30_000,
                customCaPem = prefs[Keys.CUSTOM_CA_PEM],
                trustAllCertificates = prefs[Keys.TRUST_ALL_CERTS] ?: false
            )
        }

    /**
     * Eagerly cached snapshot of [settings] for callers that only need the current
     * value synchronously (e.g. the secure-channel check in
     * `DirectoryRepository.changePassword`). Reading [settingsState].value is O(1),
     * whereas `settings.first()` re-reads and re-maps the DataStore file on every call.
     */
    val settingsState: StateFlow<AppSettings> =
        settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun setRequireSecureChannel(value: Boolean) {
        dataStore.edit { it[Keys.REQUIRE_SECURE_CHANNEL] = value }
    }

    suspend fun setConnectTimeout(value: Int) {
        dataStore.edit { it[Keys.CONNECT_TIMEOUT] = value }
    }

    suspend fun setResponseTimeout(value: Int) {
        dataStore.edit { it[Keys.RESPONSE_TIMEOUT] = value }
    }

    suspend fun setCustomCaPem(pem: String?) {
        dataStore.edit {
            if (pem != null) it[Keys.CUSTOM_CA_PEM] = pem
            else it.remove(Keys.CUSTOM_CA_PEM)
        }
    }

    suspend fun setTrustAllCertificates(value: Boolean) {
        dataStore.edit { it[Keys.TRUST_ALL_CERTS] = value }
    }
}
