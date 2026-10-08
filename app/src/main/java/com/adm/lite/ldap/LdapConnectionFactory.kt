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

import com.adm.lite.data.prefs.AppSettings
import com.adm.lite.data.prefs.SettingsStore
import com.adm.lite.model.AdError
import com.adm.lite.model.AdServer
import com.adm.lite.model.TlsMode
import com.unboundid.ldap.sdk.LDAPConnection
import com.unboundid.ldap.sdk.LDAPConnectionOptions
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest
import com.unboundid.util.ssl.HostNameSSLSocketVerifier
import com.unboundid.util.ssl.SSLUtil
import com.unboundid.util.ssl.TrustAllTrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Factory for creating LDAP connections to Active Directory servers.
 *
 * Supports three TLS modes: LDAPS (encrypted from startup), START_TLS (upgrades plain),
 * and PLAIN (no encryption). Certificate trust is controlled via [SettingsStore].
 *
 * NOTE: TrustAllTrustManager is only enabled when [AppSettings.trustAllCertificates] is
 * explicitly set to `true` in the app settings. It is never the default behaviour.
 */
/**
 * Abstraction over LDAP connection creation, enabling tests to substitute a fake.
 */
interface LdapConnector {
    suspend fun connect(server: AdServer): LDAPConnection
}

class LdapConnectionFactory(private val settings: SettingsStore) : LdapConnector {

    override suspend fun connect(server: AdServer): LDAPConnection = withContext(Dispatchers.IO) {
        val appSettings = settings.settings.first()
        val tm = runCatching { trustManager(appSettings) }
            .getOrElse { e ->
                throw AdError(
                    kind = AdError.Kind.TLS,
                    resultCode = null,
                    hexData = null,
                    rawMessage = e.message ?: "TLS setup failed"
                )
            }
        val options = buildConnectionOptions(appSettings)

        when (server.tlsMode) {
            TlsMode.LDAPS -> {
                val sslUtil = SSLUtil(tm)
                val socketFactory = sslUtil.createSSLSocketFactory()
                LDAPConnection(socketFactory, options, server.host, server.port)
            }
            TlsMode.START_TLS -> {
                val conn = LDAPConnection(options, server.host, server.port)
                try {
                    val sslUtil = SSLUtil(tm)
                    // Версия согласуется с сервером; SSLUtil по умолчанию не разрешает протоколы ниже TLS 1.2.
                    val sslContext = sslUtil.createSSLContext()
                    val result = conn.processExtendedOperation(StartTLSExtendedRequest(sslContext))
                    if (result.resultCode != com.unboundid.ldap.sdk.ResultCode.SUCCESS) {
                        throw AdError(
                            kind = AdError.Kind.TLS,
                            resultCode = result.resultCode.intValue(),
                            hexData = null,
                            rawMessage = "START_TLS failed: ${result.diagnosticMessage}"
                        )
                    }
                } catch (e: Exception) {
                    conn.close()
                    if (e is AdError) throw e
                    throw AdError(
                        kind = AdError.Kind.TLS,
                        resultCode = null,
                        hexData = null,
                        rawMessage = e.message
                    )
                }
                conn
            }
            TlsMode.PLAIN -> {
                LDAPConnection(options, server.host, server.port)
            }
        }
    }

    private fun trustManager(appSettings: AppSettings): TrustManager {
        return when {
            appSettings.trustAllCertificates -> {
                // Trusting all certificates should only ever happen when the user
                // explicitly enables this flag in Settings (AppSettings.trustAllCertificates).
                TrustAllTrustManager()
            }
            appSettings.customCaPem != null -> {
                val cf = CertificateFactory.getInstance("X.509")
                val certs = cf.generateCertificates(
                    ByteArrayInputStream(appSettings.customCaPem.toByteArray())
                )

                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                    certs.forEachIndexed { i, cert ->
                        setCertificateEntry("custom-ca-$i", cert)
                    }
                }

                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                    init(keyStore)
                }

                tmf.trustManagers.firstNotNullOfOrNull {
                    it as? X509TrustManager
                } ?: throw IllegalStateException("No X509TrustManager found in TrustManagerFactory")
            }
            else -> {
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                    init(null as KeyStore?)
                }
                tmf.trustManagers.firstNotNullOfOrNull {
                    it as? X509TrustManager
                } ?: throw IllegalStateException("No X509TrustManager found in TrustManagerFactory")
            }
        }
    }

    private fun buildConnectionOptions(appSettings: AppSettings): LDAPConnectionOptions {
        return LDAPConnectionOptions().apply {
            connectTimeoutMillis = appSettings.connectTimeoutMs
            responseTimeoutMillis = appSettings.responseTimeoutMs.toLong()
            setFollowReferrals(false)
            if (!appSettings.trustAllCertificates) {
                setSSLSocketVerifier(HostNameSSLSocketVerifier(false))
            }
        }
    }
}
