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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.adm.lite.data.crypto.CredentialCodec
import com.adm.lite.data.crypto.CredentialStore
import com.adm.lite.data.crypto.KeystoreSecretKeyProvider
import com.adm.lite.data.db.AdmDatabase
import com.adm.lite.data.prefs.SettingsStore
import com.adm.lite.data.repo.DirectoryRepository
import com.adm.lite.data.repo.ServerRepository
import com.adm.lite.ldap.LdapConnectionFactory
import com.adm.lite.ldap.LdapErrorMapper
import com.adm.lite.ldap.LdapSession
import com.adm.lite.ldap.ops.AccountPropertiesImpl
import com.adm.lite.ldap.ops.ChangePasswordImpl
import com.adm.lite.ldap.ops.ModifyGroupsImpl
import com.adm.lite.ldap.ops.EditNameImpl
import com.adm.lite.ldap.ops.SearchGroupsImpl
import com.adm.lite.ldap.ops.SearchUsersImpl
import com.adm.lite.ldap.ops.ToggleAccountEnabledImpl
import com.adm.lite.ldap.ops.UnlockAccountImpl
import java.io.File

class AppContainer(context: Context) {

    private val settingsDataStore: androidx.datastore.core.DataStore<Preferences> = PreferenceDataStoreFactory.create {
        File(context.filesDir, "settings.preferences_pb")
    }

    private val credentialDataStore: androidx.datastore.core.DataStore<Preferences> = PreferenceDataStoreFactory.create {
        File(context.filesDir, "credentials.preferences_pb")
    }

    /**
     * Application-lifetime scope. Used to keep [SettingsStore.settingsState] warm so
     * settings lookups do not re-read the DataStore file on every call.
     */
    internal val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )

    val database: AdmDatabase by lazy {
        AdmDatabase.build(context)
    }

    val settingsStore: SettingsStore by lazy {
        SettingsStore(settingsDataStore, appScope)
    }

    private val credentialCodec by lazy {
        CredentialCodec(KeystoreSecretKeyProvider())
    }

    val credentialStore: CredentialStore by lazy {
        CredentialStore(credentialDataStore, credentialCodec)
    }

    val serverRepository: ServerRepository by lazy {
        ServerRepository(
            dao = database.serverDao(),
            credentials = credentialStore
        )
    }

    val ldapErrorMapper: LdapErrorMapper by lazy {
        LdapErrorMapper()
    }

    val ldapConnectionFactory: LdapConnectionFactory by lazy {
        LdapConnectionFactory(settingsStore)
    }

    val ldapSession: LdapSession by lazy {
        LdapSession(ldapConnectionFactory, ldapErrorMapper)
    }

    val searchUsersImpl: SearchUsersImpl by lazy {
        SearchUsersImpl(ldapSession, ldapErrorMapper)
    }

    val changePasswordImpl: ChangePasswordImpl by lazy {
        ChangePasswordImpl(ldapSession, ldapErrorMapper)
    }

    val modifyGroupsImpl: ModifyGroupsImpl by lazy {
        ModifyGroupsImpl(ldapSession, ldapErrorMapper)
    }

    val editNameImpl: EditNameImpl by lazy {
        EditNameImpl(ldapSession, ldapErrorMapper)
    }

    private val accountPropertiesImpl: AccountPropertiesImpl by lazy {
        AccountPropertiesImpl(ldapSession, ldapErrorMapper)
    }

    private val toggleAccountEnabledImpl: ToggleAccountEnabledImpl by lazy {
        ToggleAccountEnabledImpl(ldapSession, ldapErrorMapper)
    }

    private val unlockAccountImpl: UnlockAccountImpl by lazy {
        UnlockAccountImpl(ldapSession, ldapErrorMapper)
    }

    val searchGroupsImpl: SearchGroupsImpl by lazy {
        SearchGroupsImpl(ldapSession, ldapErrorMapper)
    }

    val directoryRepository: DirectoryRepository by lazy {
        DirectoryRepository(
            session = ldapSession,
            searchUsers = searchUsersImpl,
            searchGroups = searchGroupsImpl,
            changePassword = changePasswordImpl,
            modifyGroups = modifyGroupsImpl,
            editName = editNameImpl,
            accountProperties = accountPropertiesImpl,
            toggleAccountEnabled = toggleAccountEnabledImpl,
            unlockAccountUseCase = unlockAccountImpl,
            serverRepository = serverRepository,
            settingsStore = settingsStore
        )
    }

}
