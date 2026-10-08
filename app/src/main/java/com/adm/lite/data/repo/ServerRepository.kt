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

package com.adm.lite.data.repo

import com.adm.lite.data.crypto.CredentialStore
import com.adm.lite.data.db.ServerDao
import com.adm.lite.data.db.toDomain
import com.adm.lite.data.db.toEntity
import com.adm.lite.model.AdServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ServerRepository(
    private val dao: ServerDao,
    private val credentials: CredentialStore
) {
    fun observeServers(): Flow<List<AdServer>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): AdServer? =
        dao.getById(id)?.toDomain()

    suspend fun save(server: AdServer, password: String?): Long {
        val id = if (server.id == 0L) {
            dao.insert(server.toEntity())
        } else {
            dao.update(server.toEntity())
            server.id
        }
        if (server.rememberPassword && password != null) {
            credentials.put(id, password)
        } else {
            credentials.clear(id)
        }
        return id
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
        credentials.clear(id)
    }

    suspend fun savedPassword(id: Long): String? =
        credentials.get(id)
}
