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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SettingsStore
    private val dsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = dsScope
        ) { tmpFolder.newFile("test.preferences_pb") }
        store = SettingsStore(dataStore)
    }

    @After
    fun tearDown() {
        dsScope.cancel()
    }

    @Test
    fun emptyStoreReturnsDefaults() = runTest {
        val settings = store.settings.first()
        assertTrue(settings.requireSecureChannelForPassword)
        assertFalse(settings.trustAllCertificates)
        assertEquals(10_000, settings.connectTimeoutMs)
        assertEquals(30_000, settings.responseTimeoutMs)
        assertNull(settings.customCaPem)
    }

    @Test
    fun setRequireSecureChannelPersists() = runTest {
        store.setRequireSecureChannel(false)
        assertFalse(store.settings.first().requireSecureChannelForPassword)
        store.setRequireSecureChannel(true)
        assertTrue(store.settings.first().requireSecureChannelForPassword)
    }

    @Test
    fun setCustomCaPemPersistsAndClears() = runTest {
        val pem = "-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----"
        store.setCustomCaPem(pem)
        assertEquals(pem, store.settings.first().customCaPem)
        store.setCustomCaPem(null)
        assertNull(store.settings.first().customCaPem)
    }

    @Test
    fun setTimeouts() = runTest {
        store.setConnectTimeout(5000)
        assertEquals(5000, store.settings.first().connectTimeoutMs)
        store.setResponseTimeout(60000)
        assertEquals(60000, store.settings.first().responseTimeoutMs)
    }
}
