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

import com.adm.lite.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    @Test fun validateHost_valid() {
        assertNull(Validators.validateHost("valid"))
    }

    @Test fun validateHost_empty() {
        assertEquals(R.string.error_invalid_host, Validators.validateHost(""))
    }

    @Test fun validateHost_with_space() {
        assertEquals(R.string.error_invalid_host, Validators.validateHost("host with space"))
    }

    @Test fun validateHost_ldap_scheme() {
        assertEquals(R.string.error_invalid_host, Validators.validateHost("ldap://host"))
    }

    @Test fun validatePort_valid() {
        assertNull(Validators.validatePort("80"))
    }

    @Test fun validatePort_zero() {
        assertEquals(R.string.error_invalid_port, Validators.validatePort("0"))
    }

    @Test fun validatePort_max() {
        assertNull(Validators.validatePort("65535"))
    }

    @Test fun validatePort_over_max() {
        assertEquals(R.string.error_invalid_port, Validators.validatePort("65536"))
    }

    @Test fun validatePort_non_numeric() {
        assertEquals(R.string.error_invalid_port, Validators.validatePort("abc"))
    }

    @Test fun validateBaseDn_valid() {
        assertNull(Validators.validateBaseDn("dc=example,dc=com"))
    }

    @Test fun validateBaseDn_invalid() {
        assertEquals(R.string.error_invalid_base_dn, Validators.validateBaseDn("not a dn"))
    }

    @Test fun validateBindDn_valid_dn() {
        assertNull(Validators.validateBindDn("cn=admin,dc=example,dc=com"))
    }

    @Test fun validateBindDn_upn_format() {
        assertNull(Validators.validateBindDn("user@domain.com"))
    }

    @Test fun validateBindDn_domain_backslash_user() {
        assertNull(Validators.validateBindDn("DOMAIN\\user"))
    }

    @Test fun validateBindDn_empty() {
        assertEquals(R.string.error_invalid_bind_dn, Validators.validateBindDn(""))
    }

    @Test fun validateNewCn_valid() {
        assertNull(Validators.validateNewCn("valid"))
    }

    @Test fun validateNewCn_too_long() {
        assertEquals(R.string.error_invalid_new_cn, Validators.validateNewCn("a".repeat(65)))
    }

    @Test fun validateSamAccountName_max_length() {
        assertNull(Validators.validateSamAccountName("a".repeat(20)))
    }

    @Test fun validateSamAccountName_over_length() {
        assertEquals(R.string.error_invalid_sam_account_name, Validators.validateSamAccountName("a".repeat(21)))
    }

    @Test fun validatePasswordsMatch_same() {
        assertNull(Validators.validatePasswordsMatch("a", "a"))
    }

    @Test fun validatePasswordsMatch_different() {
        assertEquals(R.string.error_passwords_do_not_match, Validators.validatePasswordsMatch("a", "b"))
    }

    @Test fun validateServerName_valid() {
        assertNull(Validators.validateServerName("server"))
    }

    @Test fun validateServerName_empty() {
        assertEquals(R.string.error_invalid_host, Validators.validateServerName(""))
    }
}
