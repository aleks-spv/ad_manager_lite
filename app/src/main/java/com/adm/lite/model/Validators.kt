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

package com.adm.lite.model

import androidx.annotation.StringRes
import com.adm.lite.R
import com.unboundid.ldap.sdk.DN

object Validators {

    @StringRes
    fun validateServerName(v: String): Int? {
        if (v.isBlank()) return R.string.error_invalid_host
        return null
    }

    @StringRes
    fun validateHost(v: String): Int? {
        if (v.isBlank()) return R.string.error_invalid_host
        if (v.contains(' ')) return R.string.error_invalid_host
        if (v.contains(':') || v.contains('/') || v.contains('?')) return R.string.error_invalid_host
        val lower = v.lowercase()
        if (lower.startsWith("ldap://") || lower.startsWith("ldaps://")) return R.string.error_invalid_host
        return null
    }

    @StringRes
    fun validatePort(v: String): Int? {
        if (v.isBlank()) return R.string.error_invalid_port
        val port = v.toIntOrNull() ?: return R.string.error_invalid_port
        return if (port in 1..65535) null else R.string.error_invalid_port
    }

    @StringRes
    fun validateBaseDn(v: String): Int? {
        if (v.isBlank()) return R.string.error_invalid_base_dn
        return if (DN.isValidDN(v)) null else R.string.error_invalid_base_dn
    }

    @StringRes
    fun validateBindDn(v: String): Int? {
        if (v.isBlank()) return R.string.error_invalid_bind_dn
        // Valid DN
        if (DN.isValidDN(v)) return null
        // UPN format: user@domain.com (has @ and a dot after it)
        val atIndex = v.indexOf('@')
        if (atIndex > 0 && v.indexOf('.', atIndex) > atIndex) return null
        // DOMAIN\user format
        if (v.contains('\\')) return null
        // Nothing matched — invalid
        return R.string.error_invalid_bind_dn
    }

    @StringRes
    fun validateNewCn(v: String): Int? {
        if (v.isBlank()) return R.string.error_invalid_new_cn
        if (v.length > 64) return R.string.error_invalid_new_cn
        // Forbidden characters: , + " \ < > ; = #
        // # must be escaped when it starts an RDN per RFC 4514, so reject it here.
        for (ch in v) {
            when (ch) {
                ',', '+', '"', '\\', '<', '>', ';', '=', '#' -> return R.string.error_invalid_new_cn
            }
        }
        return null
    }

    @StringRes
    fun validateSamAccountName(v: String): Int? {
        if (v.isBlank()) return R.string.error_invalid_sam_account_name
        if (v.length !in 1..20) return R.string.error_invalid_sam_account_name
        val forbidden = setOf('/', '\\', '[', ']', ':', ';', '|', '=', ',', '+', '*', '?', '<', '>', '@')
        for (ch in v) {
            if (ch in forbidden) return R.string.error_invalid_sam_account_name
        }
        if (v.endsWith(' ')) return R.string.error_invalid_sam_account_name
        if (v.endsWith('.')) return R.string.error_invalid_sam_account_name
        return null
    }

    @StringRes
    fun validatePasswordsMatch(a: String, b: String): Int? {
        return if (a == b) null else R.string.error_passwords_do_not_match
    }
}
