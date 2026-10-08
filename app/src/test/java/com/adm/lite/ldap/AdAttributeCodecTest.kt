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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AdAttributeCodecTest {
    @Test
    fun encodeUnicodePwd_quotesAndUsesUtf16le() {
        val bytes = AdAttributeCodec.encodeUnicodePwd("new")
        // "new" quoted = 5 chars * 2 bytes = 10 bytes
        assertEquals(10, bytes.size)
        // First two bytes should be 0x22 0x00 (UTF-16LE double-quote)
        assertEquals(0x22.toByte(), bytes[0])
        assertEquals(0x00.toByte(), bytes[1])
        // Last two bytes should be 0x22 0x00
        assertEquals(0x22.toByte(), bytes[8])
        assertEquals(0x00.toByte(), bytes[9])
        // 'n' = 0x6E, 'e' = 0x65, 'w' = 0x77 (UTF-16LE: low byte first)
        assertEquals(0x6E.toByte(), bytes[2])
        assertEquals(0x00.toByte(), bytes[3])
        assertEquals(0x65.toByte(), bytes[4])
        assertEquals(0x00.toByte(), bytes[5])
        assertEquals(0x77.toByte(), bytes[6])
        assertEquals(0x00.toByte(), bytes[7])
    }

    @Test
    fun unicodePwdAttribute_hasCorrectNameAndValue() {
        val attr = AdAttributeCodec.unicodePwdAttribute("secret")
        assertEquals("unicodePwd", attr.getName())
        assertArrayEquals(AdAttributeCodec.encodeUnicodePwd("secret"), attr.getValueByteArray())
    }

    @Test
    fun unicodePwdAttribute_emptyPassword_stillQuoted() {
        val attr = AdAttributeCodec.unicodePwdAttribute("")
        // Empty password still gets quotes: "" = 2 chars * 2 bytes = 4 bytes
        assertEquals(4, attr.getValueByteArray().size)
        assertEquals(0x22.toByte(), attr.getValueByteArray()[0])
        assertEquals(0x22.toByte(), attr.getValueByteArray()[2])
    }
}
