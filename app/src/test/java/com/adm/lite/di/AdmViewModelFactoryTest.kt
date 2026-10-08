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

package com.adm.lite.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adm.lite.ui.actions.UserActionsViewModel
import com.adm.lite.ui.connect.ConnectViewModel
import com.adm.lite.ui.servers.ServersViewModel
import com.adm.lite.ui.settings.SettingsViewModel
import com.adm.lite.ui.users.UsersViewModel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdmViewModelFactoryTest {

    private lateinit var factory: AdmViewModelFactory

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)
        factory = AdmViewModelFactory(container)
    }

    @Test
    fun `creates ConnectViewModel`() {
        val vm = factory.create(ConnectViewModel::class.java)
        assertNotNull(vm)
        assertTrue(vm is ConnectViewModel)
    }

    @Test
    fun `creates UsersViewModel`() {
        val vm = factory.create(UsersViewModel::class.java)
        assertNotNull(vm)
        assertTrue(vm is UsersViewModel)
    }

    @Test
    fun `creates UserActionsViewModel`() {
        val vm = factory.create(UserActionsViewModel::class.java)
        assertNotNull(vm)
        assertTrue(vm is UserActionsViewModel)
    }

    @Test
    fun `creates ServersViewModel`() {
        val vm = factory.create(ServersViewModel::class.java)
        assertNotNull(vm)
        assertTrue(vm is ServersViewModel)
    }

    @Test
    fun `creates SettingsViewModel`() {
        val vm = factory.create(SettingsViewModel::class.java)
        assertNotNull(vm)
        assertTrue(vm is SettingsViewModel)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws for unknown ViewModel`() {
        factory.create(UnknownViewModel::class.java)
    }

    private class UnknownViewModel : androidx.lifecycle.ViewModel()
}
