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

import com.unboundid.ldap.sdk.Attribute

/**
 * Encodes values for Active Directory LDAP attributes that require
 * UTF-16LE encoding with surrounding double quotes.
 *
 * ## unicodePwd encoding rules
 *
 * Active Directory requires the `unicodePwd` attribute value to be a
 * binary value that is:
 *
 * 1. The password string wrapped in ASCII double-quote characters (`"`).
 * 2. Encoded as **UTF-16LE** (little-endian, **no BOM**).
 *
 * Example — encoding `"new"`:
 * ```
 * Step 1 : quote-wrapped string = "new"
 * Step 2 : UTF-16LE bytes per character (hex):
 *          " → 0x22 0x00
 *          n → 0x6E 0x00
 *          e → 0x65 0x00
 *          w → 0x77 0x00
 *          " → 0x22 0x00
 * Result : [0x22, 0x00, 0x6E, 0x00, 0x65, 0x00, 0x77, 0x00, 0x22, 0x00]
 * ```
 */
object AdAttributeCodec {

    /**
     * Encodes [password] as a UTF-16LE byte array with surrounding double quotes,
     * suitable for the Active Directory `unicodePwd` attribute.
     *
     * The encoding:
     * - Wraps the password in ASCII `"..."`.
     * - Uses `Charsets.UTF_16LE` (little-endian, **no BOM**).
     */
    fun encodeUnicodePwd(password: String): ByteArray {
        return ("\"" + password + "\"").toByteArray(Charsets.UTF_16LE)
    }

    /**
     * Creates an LDAP [Attribute] named `unicodePwd` containing the UTF-16LE
     * encoded bytes of [password].
     */
    fun unicodePwdAttribute(password: String): Attribute {
        return Attribute("unicodePwd", encodeUnicodePwd(password))
    }
}
