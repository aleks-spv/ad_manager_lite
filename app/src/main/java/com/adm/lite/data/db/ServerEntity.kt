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

package com.adm.lite.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.adm.lite.model.AdServer
import com.adm.lite.model.TlsMode

@Entity(
    tableName = "servers",
    indices = [Index(value = ["name"], unique = true)]
)
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val baseDn: String,
    val tlsMode: String,
    val bindDn: String,
    val rememberPassword: Boolean
)

fun ServerEntity.toDomain(): AdServer = AdServer(
    id = id,
    name = name,
    host = host,
    port = port,
    baseDn = baseDn,
    tlsMode = TlsMode.valueOf(tlsMode),
    bindDn = bindDn,
    rememberPassword = rememberPassword
)

fun AdServer.toEntity(): ServerEntity = ServerEntity(
    id = id,
    name = name,
    host = host,
    port = port,
    baseDn = baseDn,
    tlsMode = tlsMode.name,
    bindDn = bindDn,
    rememberPassword = rememberPassword
)
