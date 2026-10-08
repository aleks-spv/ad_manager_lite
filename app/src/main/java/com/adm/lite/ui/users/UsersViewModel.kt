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

package com.adm.lite.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adm.lite.data.repo.DirectoryRepository
import com.adm.lite.data.repo.ServerRepository
import com.adm.lite.ldap.LdapSession
import com.adm.lite.model.AdError
import com.adm.lite.model.AdUser
import com.adm.lite.model.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UsersViewModel(
    private val directory: DirectoryRepository,
    private val servers: ServerRepository,
    private val session: LdapSession
) : ViewModel() {

    data class UsersUiState(
        val query: String = "",
        val loading: Boolean = false,
        val allUsers: List<AdUser> = emptyList(),
        val users: List<AdUser> = emptyList(),
        val selected: AdUser? = null,
        val error: AdError? = null,
        val truncated: Boolean = false,
        val hasMore: Boolean = false,
        val nextCookie: String? = null,
        val connected: Boolean = false,
        val serverBaseDn: String = ""
    )

    private val _uiState = MutableStateFlow(UsersUiState())
    val uiState: StateFlow<UsersUiState> = _uiState.asStateFlow()

    /** Raw text of the search box; drives the debounced server-side search. */
    private val queryFlow = MutableStateFlow("")

    val currentServerId: Long
        get() = when (val s = session.state.value) {
            is ConnectionState.Connected -> s.serverId
            else -> 0L
        }

    init {
        // Observe connection state — load all users when connected
        viewModelScope.launch {
            session.state.collect { state ->
                val isConnected = state is ConnectionState.Connected
                val wasConnected = _uiState.value.connected
                _uiState.update { it.copy(connected = isConnected) }
                if (isConnected && !wasConnected && _uiState.value.allUsers.isEmpty()) {
                    loadUsers(_uiState.value.query)
                }
                if (isConnected && state is ConnectionState.Connected) {
                    val server = servers.get(state.serverId)
                    _uiState.update { it.copy(serverBaseDn = server?.baseDn ?: "") }
                }
            }
        }

        // Debounced server-side search. The first emission is the empty initial query,
        // which the connection observer above already loads, so it is dropped here.
        viewModelScope.launch {
            queryFlow
                .drop(1)
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { query -> loadUsers(query) }
        }
    }

    /**
     * Records the raw query in the UI state immediately (so the text field stays
     * responsive) and pushes it into [queryFlow], whose debounced collector issues the
     * LDAP search. Filtering happens on the directory server, not on the client.
     */
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, error = null) }
        queryFlow.value = query
    }

    /** Fetches the next page using the cookie returned by the previous search. */
    fun loadMore() {
        val state = _uiState.value
        val cookie = state.nextCookie
        if (cookie.isNullOrEmpty() || state.loading) return
        viewModelScope.launch { loadUsers(state.query, append = true, cookie = cookie) }
    }

    private suspend fun loadUsers(
        query: String,
        append: Boolean = false,
        cookie: String? = null
    ) {
        val state = session.state.value
        if (state !is ConnectionState.Connected) {
            _uiState.update {
                it.copy(error = AdError(AdError.Kind.NETWORK, null, null, "Not connected"))
            }
            return
        }

        _uiState.update { it.copy(loading = true, error = null) }

        // The LDAP filter is built by SearchUsersImpl via Filter.create* helpers; the
        // query is never concatenated into a filter string here.
        directory.searchUsers(query = query, pageSize = PAGE_SIZE, cookie = cookie).fold(
            onSuccess = { (users, nextCookie) ->
                val merged = if (append) _uiState.value.allUsers + users else users
                val hasMore = !nextCookie.isNullOrEmpty()
                _uiState.update {
                    it.copy(
                        loading = false,
                        allUsers = merged,
                        users = merged,
                        truncated = hasMore,
                        hasMore = hasMore,
                        nextCookie = nextCookie,
                        // Sync selected user with fresh data so UI shows updated enabled status
                        selected = it.selected?.let { sel ->
                            merged.find { u ->
                                u.dn == sel.dn ||
                                    (sel.sAMAccountName != null && u.sAMAccountName == sel.sAMAccountName)
                            } ?: sel
                        }
                    )
                }
            },
            onFailure = { e ->
                val error = if (e is AdError) e else AdError(AdError.Kind.UNKNOWN, null, null, e.message)
                _uiState.update { it.copy(loading = false, error = error) }
            }
        )
    }

    fun selectUser(user: AdUser) {
        _uiState.update { it.copy(selected = user) }
    }

    fun refreshUsers() {
        viewModelScope.launch { loadUsers(_uiState.value.query) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selected = null) }
    }

    private companion object {
        /** Page size requested from the directory server per LDAP search. */
        const val PAGE_SIZE = 500

        /** Idle time in ms before the typed query is sent to the directory server. */
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
