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

package com.adm.lite.data

import com.adm.lite.data.db.ServerEntity
import com.adm.lite.data.prefs.AppSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Plain-text backup of app settings and server configurations.
 *
 * Passwords and any other credentials are never serialized: exported servers
 * always carry `rememberPassword = false`, and the same is enforced on import.
 */
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val settings: AppSettings,
    val servers: List<ServerEntity>
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("version", version)

        val s = JSONObject()
        s.put("requireSecureChannelForPassword", settings.requireSecureChannelForPassword)
        s.put("connectTimeoutMs", settings.connectTimeoutMs)
        s.put("responseTimeoutMs", settings.responseTimeoutMs)
        s.put("customCaPem", settings.customCaPem ?: JSONObject.NULL)
        s.put("trustAllCertificates", settings.trustAllCertificates)
        json.put("settings", s)

        // Servers — credentials are intentionally omitted.
        val arr = JSONArray()
        for (srv in servers) {
            val o = JSONObject()
            o.put("name", srv.name)
            o.put("host", srv.host)
            o.put("port", srv.port)
            o.put("baseDn", srv.baseDn)
            o.put("tlsMode", srv.tlsMode)
            o.put("bindDn", srv.bindDn)
            o.put("rememberPassword", false)
            arr.put(o)
        }
        json.put("servers", arr)
        return json.toString(2)
    }

    companion object {
        const val CURRENT_VERSION: Int = 1

        fun fromJson(json: String): BackupData {
            val obj = JSONObject(json)
            val version = obj.optInt("version", CURRENT_VERSION)

            val s = obj.getJSONObject("settings")
            val settings = AppSettings(
                requireSecureChannelForPassword = s.optBoolean("requireSecureChannelForPassword", true),
                connectTimeoutMs = s.optInt("connectTimeoutMs", 10_000),
                responseTimeoutMs = s.optInt("responseTimeoutMs", 30_000),
                customCaPem = if (s.isNull("customCaPem")) {
                    null
                } else {
                    s.optString("customCaPem").ifBlank { null }
                },
                trustAllCertificates = s.optBoolean("trustAllCertificates", false)
            )

            val servers = mutableListOf<ServerEntity>()
            val arr = obj.getJSONArray("servers")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                servers.add(
                    ServerEntity(
                        id = 0, // Always 0 — resolved against existing rows on import.
                        name = o.getString("name"),
                        host = o.getString("host"),
                        port = o.optInt("port", 389),
                        baseDn = o.optString("baseDn", ""),
                        tlsMode = o.optString("tlsMode", "PLAIN"),
                        bindDn = o.optString("bindDn", ""),
                        rememberPassword = false // Never import passwords.
                    )
                )
            }

            return BackupData(version, settings, servers)
        }
    }
}
