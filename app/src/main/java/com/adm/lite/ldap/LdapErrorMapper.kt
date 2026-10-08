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

import com.adm.lite.model.AdError
import com.adm.lite.model.AdError.Kind
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.LDAPResult
import com.unboundid.ldap.sdk.ResultCode

/**
 * Converts LDAP exceptions and results to domain [AdError] objects.
 *
 * Extracts hex error codes from diagnostic messages, normalizes them,
 * and maps them to semantic [Kind] values. Falls back to resultCode-based
 * mapping when no hex code is present. Always returns an [AdError] — never throws.
 */
class LdapErrorMapper {

    /** Hex-code → Kind mapping (the authoritative AD error code table). */
    private val hexToKind: Map<String, Kind> = mapOf(
        "525" to Kind.NOT_FOUND,
        "52B" to Kind.WRONG_OLD_PASSWORD,
        "52C" to Kind.PASSWORD_POLICY,
        "52D" to Kind.PASSWORD_POLICY,
        "52E" to Kind.INVALID_CREDENTIALS,
        "530" to Kind.INVALID_CREDENTIALS,
        "531" to Kind.INVALID_CREDENTIALS,
        "532" to Kind.MUST_CHANGE_PASSWORD,
        "533" to Kind.ACCOUNT_DISABLED,
        "55E" to Kind.PASSWORD_POLICY,
        "701" to Kind.ACCOUNT_DISABLED,
        "773" to Kind.MUST_CHANGE_PASSWORD,
        "775" to Kind.ACCOUNT_LOCKED
    )

    /** Fallback resultCode → Kind mapping (raw integer result codes). */
    private val resultCodeToKind: Map<Int, Kind> = mapOf(
        8 to Kind.INSECURE_CHANNEL,
        12 to Kind.TLS,
        19 to Kind.PASSWORD_POLICY,
        48 to Kind.INVALID_CREDENTIALS,
        49 to Kind.INVALID_CREDENTIALS,
        50 to Kind.INSUFFICIENT_RIGHTS,
        32 to Kind.NOT_FOUND,
        81 to Kind.NETWORK,
        91 to Kind.NETWORK,
        85 to Kind.TIMEOUT
    )

    companion object {
        /**
         * Matches AD diagnostic codes of the form `data 52d`, `0000002D`, ...
         *
         * The leading `\b` prevents false positives on words that merely end with
         * "data", such as "metadata 52D".
         */
        private val HEX_REGEX = Regex("\\bdata ([0-9a-fA-F]{1,8})", RegexOption.IGNORE_CASE)
    }

    /**
     * Map an [LDAPException] to an [AdError].
     *
     * Uses [LDAPException.diagnosticMessage] when present (AD error messages with hex codes),
     * falls back to [LDAPException.message] for connection errors where diagnosticMessage is empty
     * but the full Java exception message contains the actual failure details.
     */
    fun map(e: LDAPException): AdError {
        val message = e.diagnosticMessage?.takeIf { it.isNotBlank() } ?: e.message
        return mapInternal(
            diagnosticMessage = message,
            resultIntValue = e.getResultCode().intValue()
        )
    }

    /**
     * Map an [LDAPResult] to an [AdError].
     */
    fun map(result: LDAPResult): AdError {
        return mapInternal(
            diagnosticMessage = result.diagnosticMessage,
            resultIntValue = result.resultCode.intValue()
        )
    }

    private fun mapInternal(
        diagnosticMessage: String?,
        resultIntValue: Int
    ): AdError {
        val rawResultCode = resultIntValue
        val diagMsg = diagnosticMessage ?: ""

        // Extract hex code from diagnostic message
        val hexData = extractHex(diagMsg)

        // Determine Kind: try hex mapping first, then fallback by resultCode
        val kind = findKind(hexData, rawResultCode)

        return AdError(
            kind = kind,
            resultCode = rawResultCode,
            hexData = hexData,
            rawMessage = diagnosticMessage
        )
    }

    private fun extractHex(diagnosticMessage: String): String? {
        return HEX_REGEX.find(diagnosticMessage)?.groupValues?.get(1)
            ?.let { normalizeHex(it) }
    }

    /**
     * Normalize a hex code: uppercase, strip leading zeros.
     * "0000052D" → "52D", "0530" → "530", "0" → "0"
     */
    private fun normalizeHex(hex: String): String {
        return hex.uppercase().trimStart('0').takeIf { it.isNotEmpty() } ?: "0"
    }

    private fun findKind(hexData: String?, rawResultCode: Int): Kind {
        // 1. Try hex mapping first (normalized key)
        hexData?.let { normalized ->
            hexToKind[normalized]?.let { return it }
        }

        // 2. Fallback: map by numeric resultCode
        resultCodeToKind[rawResultCode]?.let { return it }

        // 3. Ultimate fallback — unknown error with preserved diagnostic
        return Kind.UNKNOWN
    }
}
