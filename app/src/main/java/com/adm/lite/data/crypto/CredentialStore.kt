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

package com.adm.lite.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Abstraction over secret key provision for testability.
 * Implementations provide keys from different sources (e.g., Android Keystore).
 */
fun interface SecretKeyProvider {
    fun key(): SecretKey
}

/**
 * Provides a secret key from Android Keystore.
 *
 * KDoc: This class can only be tested on a real device or emulator.
 * Robolectric does not emulate AndroidKeyStore, so unit tests must use
 * a fake SecretKeyProvider instead.
 */
class KeystoreSecretKeyProvider : SecretKeyProvider {
    private val alias = "adm_cred_key"

    override fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getEntry(alias, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES).apply {
            init(spec)
        }.generateKey()
    }
}

/**
 * AES/GCM/NoPadding encrypt/decrypt codec.
 * Output format: Base64(IV || ciphertext || tag).
 */
class CredentialCodec(private val provider: SecretKeyProvider) {

    companion object {
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    fun seal(plain: String): String {
        val key = provider.key()
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        // Do NOT provide IV explicitly — Android Keystore rejects caller-provided IVs.
        // Let the cipher generate a random IV, then read it back for the output.
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun open(blob: String): String {
        val decoded = Base64.decode(blob, Base64.NO_WRAP)
        require(decoded.size > GCM_IV_LENGTH) { "Blob too short" }
        val iv = decoded.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = decoded.copyOfRange(GCM_IV_LENGTH, decoded.size)
        val key = provider.key()
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}

/**
 * Stores encrypted credentials per server in DataStore Preferences.
 */
class CredentialStore(
    private val dataStore: DataStore<Preferences>,
    private val codec: CredentialCodec
) {
    suspend fun put(serverId: Long, password: String) {
        val key = stringPreferencesKey("cred_$serverId")
        val sealed = withContext(Dispatchers.IO) { codec.seal(password) }
        dataStore.edit { it[key] = sealed }
    }

    suspend fun get(serverId: Long): String? {
        val key = stringPreferencesKey("cred_$serverId")
        val blob = dataStore.data.map { it[key] }.first() ?: return null
        return runCatching {
            withContext(Dispatchers.IO) { codec.open(blob) }
        }.getOrElse { e ->
            if (e is GeneralSecurityException || e is IllegalArgumentException) {
                clear(serverId)
                null
            } else {
                throw e
            }
        }
    }

    suspend fun clear(serverId: Long) {
        val key = stringPreferencesKey("cred_$serverId")
        dataStore.edit { it.remove(key) }
    }
}
