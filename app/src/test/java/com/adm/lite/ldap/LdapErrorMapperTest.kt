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

import com.adm.lite.model.AdError.Kind
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.ResultCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [LdapErrorMapper].
 * Pure JUnit — the mapper is pure computation and requires no Android runtime.
 */
class LdapErrorMapperTest {

    private val mapper = LdapErrorMapper()

    // ─── Hex-code tests (all 11 codes) ────────────────────────────────

    @Test
    fun `map LDAPException with hex code 525 returns NOT_FOUND`() {
        val ex = makeExceptionWithHex("525")
        val err = mapper.map(ex)
        assertEquals(Kind.NOT_FOUND, err.kind)
        assertEquals("525", err.hexData)
        assertNotNull(err.rawMessage)
    }

    @Test
    fun `map LDAPException with hex code 52B returns WRONG_OLD_PASSWORD`() {
        val ex = makeExceptionWithHex("52B")
        val err = mapper.map(ex)
        assertEquals(Kind.WRONG_OLD_PASSWORD, err.kind)
        assertEquals("52B", err.hexData)
    }

    @Test
    fun `map LDAPException with hex code 52D returns PASSWORD_POLICY`() {
        val ex = makeExceptionWithHex("52D")
        val err = mapper.map(ex)
        assertEquals(Kind.PASSWORD_POLICY, err.kind)
        assertEquals("52D", err.hexData)
    }

    @Test
    fun `map LDAPException with hex code 52E returns INVALID_CREDENTIALS`() {
        val ex = makeExceptionWithHex("52E")
        val err = mapper.map(ex)
        assertEquals(Kind.INVALID_CREDENTIALS, err.kind)
        assertEquals("52E", err.hexData)
    }

    @Test
    fun `map LDAPException with hex code 530 returns INVALID_CREDENTIALS`() {
        val ex = makeExceptionWithHex("530")
        val err = mapper.map(ex)
        assertEquals(Kind.INVALID_CREDENTIALS, err.kind)
        assertEquals("530", err.hexData)
    }

    @Test
    fun `map LDAPException with hex code 531 returns INVALID_CREDENTIALS`() {
        val ex = makeExceptionWithHex("531")
        val err = mapper.map(ex)
        assertEquals(Kind.INVALID_CREDENTIALS, err.kind)
        assertEquals("531", err.hexData)
    }

    @Test
    fun `map LDAPException with hex code 532 returns MUST_CHANGE_PASSWORD`() {
        val ex = makeExceptionWithHex("532")
        val err = mapper.map(ex)
        assertEquals(Kind.MUST_CHANGE_PASSWORD, err.kind)
        assertEquals("532", err.hexData)
    }

    @Test
    fun `map LDAPException with hex code 533 returns ACCOUNT_DISABLED`() {
        val ex = makeExceptionWithHex("533")
        val err = mapper.map(ex)
        assertEquals(Kind.ACCOUNT_DISABLED, err.kind)
        assertEquals("533", err.hexData)
    }

    @Test
    fun `map LDAPException with hex code 701 returns ACCOUNT_DISABLED`() {
        val ex = makeExceptionWithHex("701")
        val err = mapper.map(ex)
        assertEquals(Kind.ACCOUNT_DISABLED, err.kind)
        assertEquals("701", err.hexData)
    }

    @Test
    fun `map LDAPException with hex code 773 returns MUST_CHANGE_PASSWORD`() {
        val ex = makeExceptionWithHex("773")
        val err = mapper.map(ex)
        assertEquals(Kind.MUST_CHANGE_PASSWORD, err.kind)
        assertEquals("773", err.hexData)
    }

    @Test
    fun `map LDAPException with hex code 775 returns ACCOUNT_LOCKED`() {
        val ex = makeExceptionWithHex("775")
        val err = mapper.map(ex)
        assertEquals(Kind.ACCOUNT_LOCKED, err.kind)
        assertEquals("775", err.hexData)
    }

    // ─── Combined parameterized-style test (all 11 hex codes in one method) ──

    @Test
    fun `map LDAPException maps all 11 known hex codes correctly`() {
        val cases = listOf(
            "525" to Kind.NOT_FOUND,
            "52B" to Kind.WRONG_OLD_PASSWORD,
            "52D" to Kind.PASSWORD_POLICY,
            "52E" to Kind.INVALID_CREDENTIALS,
            "530" to Kind.INVALID_CREDENTIALS,
            "531" to Kind.INVALID_CREDENTIALS,
            "532" to Kind.MUST_CHANGE_PASSWORD,
            "533" to Kind.ACCOUNT_DISABLED,
            "701" to Kind.ACCOUNT_DISABLED,
            "773" to Kind.MUST_CHANGE_PASSWORD,
            "775" to Kind.ACCOUNT_LOCKED
        )

        for ((hex, expectedKind) in cases) {
            val ex = makeExceptionWithHex(hex)
            val err = mapper.map(ex)
            assertEquals("hex=$hex", expectedKind, err.kind)
            assertEquals("hex=$hex", hex.uppercase(), err.hexData)
            assertNotNull("hex=$hex", err.rawMessage)
            assertNotNull("hex=$hex", err.resultCode)
        }
    }

    // ─── Fallback by resultCode (no hex in diagnostic message) ─────────

    @Test
    fun `map LDAPException with no hex maps resultCode 49 to INVALID_CREDENTIALS`() {
        val diag = "Access denied: insufficient permissions"
        val ex = makeExceptionRaw(49, diag)
        val err = mapper.map(ex)
        assertEquals(Kind.INVALID_CREDENTIALS, err.kind)
        assertNull(err.hexData)
    }

    @Test
    fun `map LDAPException with no hex maps resultCode 50 to INSUFFICIENT_RIGHTS`() {
        val diag = "Access denied: insufficient rights"
        val ex = makeExceptionRaw(50, diag)
        val err = mapper.map(ex)
        assertEquals(Kind.INSUFFICIENT_RIGHTS, err.kind)
        assertNull(err.hexData)
    }

    @Test
    fun `map LDAPException with no hex maps resultCode 8 to INSECURE_CHANNEL`() {
        val diag = "Insecure channel required"
        val ex = makeExceptionRaw(8, diag)
        val err = mapper.map(ex)
        assertEquals(Kind.INSECURE_CHANNEL, err.kind)
        assertNull(err.hexData)
    }

    @Test
    fun `map LDAPException with no hex maps resultCode 32 to NOT_FOUND`() {
        val diag = "No such object: CN=NonExistent,OU=Users"
        val ex = makeExceptionRaw(32, diag)
        val err = mapper.map(ex)
        assertEquals(Kind.NOT_FOUND, err.kind)
        assertNull(err.hexData)
    }

    @Test
    fun `map LDAPException with no hex maps resultCode 81 to NETWORK`() {
        val diag = "Server unavailable: connection refused"
        val ex = makeExceptionRaw(81, diag)
        val err = mapper.map(ex)
        assertEquals(Kind.NETWORK, err.kind)
        assertNull(err.hexData)
    }

    @Test
    fun `map LDAPException with no hex maps resultCode 91 to NETWORK`() {
        val diag = "Service temporarily down: maintenance"
        val ex = makeExceptionRaw(91, diag)
        val err = mapper.map(ex)
        assertEquals(Kind.NETWORK, err.kind)
        assertNull(err.hexData)
    }

    @Test
    fun `map LDAPException with no hex maps resultCode 85 to TIMEOUT`() {
        val diag = "Operation timed out: server not responding"
        val ex = makeExceptionRaw(85, diag)
        val err = mapper.map(ex)
        assertEquals(Kind.TIMEOUT, err.kind)
        assertNull(err.hexData)
    }

    // ─── Lowercase hex recognition ─────────────────────────────────────

    @Test
    fun `map LDAPException recognizes lowercase hex code 52e`() {
        val ex = makeExceptionWithHex("52e", lowerCase = true)
        val err = mapper.map(ex)
        assertEquals(Kind.INVALID_CREDENTIALS, err.kind)
        assertEquals("52E", err.hexData) // normalized to uppercase
    }

    @Test
    fun `map LDAPException recognizes fully lowercase hex data 00000775`() {
        val diag = "00000775: LdapErr: DSID-0C0906DC, comment: AcceptSecurityContext error, data 00000775, v2580"
        val ex = makeExceptionRaw(13, diag) // UNWILLING_TO_PERFORM as carrier
        val err = mapper.map(ex)
        assertEquals(Kind.ACCOUNT_LOCKED, err.kind)
        assertEquals("775", err.hexData)
    }

    // ─── Leading zeros normalization ───────────────────────────────────

    @Test
    fun `map LDAPException normalizes hex 0000052D to 52D`() {
        val ex = makeExceptionWithHex("0000052D")
        val err = mapper.map(ex)
        assertEquals(Kind.PASSWORD_POLICY, err.kind)
        assertEquals("52D", err.hexData)
    }

    @Test
    fun `map LDAPException normalizes hex 00000532 to 532`() {
        val ex = makeExceptionWithHex("00000532")
        val err = mapper.map(ex)
        assertEquals(Kind.MUST_CHANGE_PASSWORD, err.kind)
        assertEquals("532", err.hexData)
    }

    @Test
    fun `map LDAPException normalizes hex 0775 to 775`() {
        val ex = makeExceptionWithHex("0775")
        val err = mapper.map(ex)
        assertEquals(Kind.ACCOUNT_LOCKED, err.kind)
        assertEquals("775", err.hexData)
    }

    // ─── Unknown code -> Kind.UNKNOWN ──────────────────────────────────

    @Test
    fun `map LDAPException with unknown hex returns UNKNOWN`() {
        val ex = makeExceptionWithHex("FFFF")
        val err = mapper.map(ex)
        assertEquals(Kind.UNKNOWN, err.kind)
        assertEquals("FFFF", err.hexData)
    }

    @Test
    fun `map LDAPException with unknown resultCode returns UNKNOWN`() {
        // Using a random resultCode that isn't in the fallback map
        val diag = "General error: something went wrong"
        val ex = makeExceptionRaw(666, diag)
        val err = mapper.map(ex)
        assertEquals(Kind.UNKNOWN, err.kind)
        assertNull(err.hexData)
    }

    @Test
    fun `map LDAPException preserves rawMessage for unknown errors`() {
        val diag = "0000FFFF: SvcErr: DSID-031A1255, problem 5000 (GENERAL_ERROR), data FFFF"
        val ex = makeExceptionRaw(2, diag) // OPERATIONS_ERROR int value
        val err = mapper.map(ex)
        assertEquals(Kind.UNKNOWN, err.kind)
        assertEquals(diag, err.rawMessage)
    }

    // ─── Hex regex false positives ──────────────────────────────────────

    @Test
    fun `map LDAPException with word ending in data does not extract hexData`() {
        // "metadata 52D" must NOT be parsed as the AD code 52D:
        // the hex token has to be preceded by a word boundary.
        val diag = "0000052D: LdapErr: DSID-0C0903A9, comment: metadata 52D"
        val ex = makeExceptionRaw(49, diag)
        val err = mapper.map(ex)
        assertNull(err.hexData)
        assertEquals(diag, err.rawMessage)
    }

    @Test
    fun `map LDAPException still extracts hexData when word boundary precedes data`() {
        val diag = "0000052D: LdapErr: DSID-0C0903A9, comment: AcceptSecurityContext error, data 52d, v3839"
        val ex = makeExceptionRaw(49, diag)
        val err = mapper.map(ex)
        assertEquals("52D", err.hexData)
    }

    // ─── resultCode field preservation ─────────────────────────────────

    @Test
    fun `map preserves original resultCode`() {
        val diag = "0000052E: LdapErr: DSID-0C0903A9, comment: AcceptSecurityContext error, data 52e, v3839"
        val ex = makeExceptionRaw(13, diag) // UNWILLING_TO_PERFORM int value
        val err = mapper.map(ex)
        assertEquals(13, err.resultCode!!)
    }

    // ─── Never throws exceptions ───────────────────────────────────────

    @Test
    fun `map never throws on empty diagnostic message`() {
        val ex = makeExceptionRaw(13, "")
        val err = mapper.map(ex)
        assertNotNull(err)
        assertNotNull(err.kind)
        assertNull(err.hexData)
    }

    @Test
    fun `map never throws on unknown input`() {
        // Even with an unknown resultCode and a diagnostic message containing neither
        // "data <hex>" nor fallback result codes, the mapper should return UNKNOWN without throwing
        val ex = makeExceptionRaw(999, "no data keyword here")
        val err = mapper.map(ex)
        assertEquals(Kind.UNKNOWN, err.kind)
    }

    // ─── Helper methods ────────────────────────────────────────────────

    /**
     * Creates an [LDAPException] whose diagnostic message contains a hex code extractable
     * by the `data <hex>` regex pattern.
     */
    private fun makeExceptionWithHex(
        hex: String,
        lowerCase: Boolean = false
    ): LDAPException {
        val normalized = if (lowerCase) hex.lowercase() else hex
        val diag = "0000$normalized: LdapErr: DSID-0C0903A9, comment: AcceptSecurityContext error, data $normalized, v3839"
        return makeExceptionRaw(13, diag) // 13 = UNWILLING_TO_PERFORM
    }

    /**
     * Creates an [LDAPException] with a raw int resultCode and an arbitrary diagnostic message.
     * The diagnostic message must NOT contain "data <hex>" to avoid hex extraction.
     */
    private fun makeExceptionRaw(
        resultCode: Int,
        diagnosticMessage: String
    ): LDAPException {
        return LDAPException(ResultCode.valueOf(resultCode), diagnosticMessage)
    }
}
